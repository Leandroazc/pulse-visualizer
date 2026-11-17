package app.pulse.visualizer.audio

import android.os.SystemClock
import app.pulse.visualizer.settings.SourceMode

/** Liga as fontes de audio aos processadores e publica o quadro mais recente. */
class AudioEngine {
    @Volatile
    var latest: AnalysisFrame? = null
        private set

    /** Nivel bruto de cada fonte em dB, para o diagnostico nos ajustes (-120 = sem dados). */
    @Volatile
    var systemDb = SILENT
        private set

    @Volatile
    var micDb = SILENT
        private set

    @Volatile
    var systemOk = false
        private set

    @Volatile
    var micOk = false
        private set

    @Volatile
    var active: SourceKind? = null
        private set

    private val system = SystemAudioSource()
    private val mic = MicAudioSource()
    private var selector = AutoSourceSelector()

    @Synchronized
    fun start(mode: SourceMode, hasPermission: Boolean) {
        stop()
        if (!hasPermission) return
        selector = AutoSourceSelector()
        val sel = selector

        if (mode != SourceMode.MIC) {
            val proc = SpectrumProcessor()
            var last = 0L
            systemOk = system.start { mags, binHz ->
                val now = System.nanoTime()
                val dt = if (last == 0L) 0.05f else ((now - last) / 1e9f).coerceIn(0.001f, 0.5f)
                last = now
                val frame = proc.process(mags, binHz, dt, now)
                systemDb = frame.rawPeakDb
                val ms = SystemClock.elapsedRealtime()
                sel.onSystemFrame(frame.rawPeakDb > SYSTEM_SIGNAL_DB, ms)
                if (mode == SourceMode.SYSTEM || sel.active(ms, true, micOk) == SourceKind.SYSTEM) {
                    active = SourceKind.SYSTEM
                    latest = frame
                }
            }
        }
        if (mode != SourceMode.SYSTEM) {
            val proc = SpectrumProcessor()
            var last = 0L
            micOk = mic.start { mags, binHz ->
                val now = System.nanoTime()
                val dt = if (last == 0L) 0.023f else ((now - last) / 1e9f).coerceIn(0.001f, 0.5f)
                last = now
                val frame = proc.process(mags, binHz, dt, now)
                micDb = frame.rawPeakDb
                val ms = SystemClock.elapsedRealtime()
                if (mode == SourceMode.MIC || sel.active(ms, systemOk, true) == SourceKind.MIC) {
                    active = SourceKind.MIC
                    latest = frame
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        system.stop()
        mic.stop()
        systemOk = false
        micOk = false
        systemDb = SILENT
        micDb = SILENT
        active = null
        latest = null
    }

    companion object {
        const val SILENT = -120f

        /** A captura do Visualizer tem 8 bits; acima disso ja e sinal de verdade. */
        private const val SYSTEM_SIGNAL_DB = -40f
    }
}
