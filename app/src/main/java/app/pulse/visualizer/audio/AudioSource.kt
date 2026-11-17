package app.pulse.visualizer.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.max

fun interface MagnitudeSink {
    /** O indice k de [mags] corresponde a k * [binHz]. O array e reutilizado entre chamadas. */
    fun onMagnitudes(mags: FloatArray, binHz: Float)
}

interface AudioSource {
    /** Retorna false se a fonte nao pode ser aberta neste aparelho. */
    fun start(sink: MagnitudeSink): Boolean
    fun stop()
}

/** Mix de saida do Android (sessao 0) pela API Visualizer. */
class SystemAudioSource : AudioSource {
    private var visualizer: Visualizer? = null
    private var mags = FloatArray(0)

    override fun start(sink: MagnitudeSink): Boolean {
        stop()
        return try {
            val v = Visualizer(0)
            visualizer = v
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            runCatching { v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED }
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, rate: Int) = Unit

                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null || fft.size < 4) return
                    val bins = fft.size / 2
                    if (mags.size != bins) mags = FloatArray(bins)
                    for (k in 1 until bins) {
                        mags[k] = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat()) / 128f
                    }
                    // samplingRate vem em mHz.
                    sink.onMagnitudes(mags, samplingRate / 1000f / fft.size)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            true
        } catch (t: Throwable) {
            stop()
            false
        }
    }

    override fun stop() {
        visualizer?.let { v ->
            runCatching { v.enabled = false }
            runCatching { v.release() }
        }
        visualizer = null
    }
}

/** Microfone da central, com FFT proprio. */
class MicAudioSource : AudioSource {
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission") // AudioEngine so inicia com a permissao concedida.
    override fun start(sink: MagnitudeSink): Boolean {
        stop()
        return try {
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return false
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FFT_SIZE * 4),
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            rec.startRecording()
            record = rec
            running = true
            thread = Thread({ loop(rec, sink) }, "pulse-mic").also { it.start() }
            true
        } catch (t: Throwable) {
            stop()
            false
        }
    }

    private fun loop(rec: AudioRecord, sink: MagnitudeSink) {
        val fft = Fft(FFT_SIZE)
        val hop = ShortArray(HOP)
        val window = FloatArray(FFT_SIZE)
        val mags = FloatArray(FFT_SIZE / 2)
        while (running) {
            var read = 0
            while (read < HOP && running) {
                val n = rec.read(hop, read, HOP - read)
                if (n <= 0) return
                read += n
            }
            if (!running) return
            System.arraycopy(window, HOP, window, 0, FFT_SIZE - HOP)
            for (i in 0 until HOP) window[FFT_SIZE - HOP + i] = hop[i] / 32768f
            fft.magnitudes(window, mags)
            sink.onMagnitudes(mags, RATE.toFloat() / FFT_SIZE)
        }
    }

    override fun stop() {
        running = false
        record?.let { r ->
            runCatching { r.stop() }
            runCatching { thread?.join(500) }
            runCatching { r.release() }
        }
        record = null
        thread = null
    }

    private companion object {
        const val RATE = 44100
        const val FFT_SIZE = 2048
        const val HOP = 1024
    }
}
