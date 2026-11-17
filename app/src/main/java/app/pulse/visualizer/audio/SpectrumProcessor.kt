package app.pulse.visualizer.audio

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Resultado de uma analise: bandas 0..1 ja com ganho automatico, prontas para suavizar e desenhar. */
class AnalysisFrame(
    val bands: FloatArray,
    val bass: Float,
    val level: Float,
    /** Incrementa a cada batida detectada. */
    val beatCount: Int,
    /** Pico bruto em dB (0 dB = fundo de escala), antes do ganho automatico. */
    val rawPeakDb: Float,
    val timeNanos: Long,
)

/**
 * Converte magnitudes lineares de FFT em bandas logaritmicas normalizadas.
 * Sem dependencias do Android, para poder ser testado na JVM.
 */
class SpectrumProcessor(
    val bandCount: Int = BANDS,
    private val minHz: Float = 40f,
    private val maxHz: Float = 16000f,
    private val rangeDb: Float = 36f,
    private val minPeakDb: Float = -60f,
) {
    private val edges = FloatArray(bandCount + 1) { minHz * (maxHz / minHz).pow(it / bandCount.toFloat()) }
    private val centers = FloatArray(bandCount) { sqrt(edges[it] * edges[it + 1]) }
    private val tilt = FloatArray(bandCount) { TILT_DB_PER_OCTAVE * (ln(centers[it] / 1000f) / ln(2f)) }
    private val bassBands = centers.count { it < 160f }.coerceAtLeast(1)
    private val db = FloatArray(bandCount)

    private var peakDb = minPeakDb
    private var bassAvg = 0f
    private var clock = 0f
    private var lastBeat = -1f
    private var beatCount = 0

    /** Frequencia central da banda [i], em Hz. */
    fun centerHz(i: Int): Float = centers[i]

    /**
     * @param mags magnitudes lineares; o indice k corresponde a k * [binHz] (k = 0 e DC)
     * @param dtSec tempo desde a chamada anterior
     */
    fun process(mags: FloatArray, binHz: Float, dtSec: Float, nowNanos: Long = System.nanoTime()): AnalysisFrame {
        clock += dtSec
        val last = mags.size - 1
        var frameMax = -200f
        var rawMax = 0f
        for (i in 0 until bandCount) {
            val k0 = edges[i] / binHz
            val k1 = edges[i + 1] / binHz
            var m: Float
            if (k1 - k0 < 1f) {
                // Banda mais estreita que um bin: interpola no centro.
                val kc = ((k0 + k1) * 0.5f).coerceIn(1f, last.toFloat())
                val a = floor(kc).toInt()
                val b = min(a + 1, last)
                val t = kc - a
                m = mags[a] * (1f - t) + mags[b] * t
            } else {
                val lo = max(1, ceil(k0).toInt())
                val hi = min(last, floor(k1).toInt())
                m = 0f
                for (k in lo..hi) if (mags[k] > m) m = mags[k]
            }
            if (m > rawMax) rawMax = m
            val d = 20f * log10(m + 1e-7f) + tilt[i]
            db[i] = d
            if (d > frameMax) frameMax = d
        }

        // Ganho automatico: o topo acompanha o pico recente e desce devagar.
        peakDb = max(max(frameMax, peakDb - PEAK_FALL_DB_PER_SEC * dtSec), minPeakDb)
        val floorDb = peakDb - rangeDb

        val bands = FloatArray(bandCount)
        var sum = 0f
        var bassSum = 0f
        for (i in 0 until bandCount) {
            val v = ((db[i] - floorDb) / rangeDb).coerceIn(0f, 1f)
            bands[i] = v
            sum += v
            if (i < bassBands) bassSum += v
        }
        val bass = bassSum / bassBands
        val level = sum / bandCount

        if (bass > bassAvg * 1.3f + 0.04f && bass > 0.35f && clock - lastBeat > 0.22f) {
            beatCount++
            lastBeat = clock
        }
        bassAvg += (bass - bassAvg) * (1f - exp(-dtSec / 1.0f))

        return AnalysisFrame(bands, bass, level, beatCount, 20f * log10(rawMax + 1e-7f), nowNanos)
    }

    companion object {
        const val BANDS = 64
        private const val TILT_DB_PER_OCTAVE = 3f
        private const val PEAK_FALL_DB_PER_SEC = 2f
    }
}
