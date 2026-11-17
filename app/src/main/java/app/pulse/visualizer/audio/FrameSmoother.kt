package app.pulse.visualizer.audio

import kotlin.math.exp
import kotlin.math.max

/**
 * Suaviza a analise quadro a quadro (60 fps) para o desenho ficar fluido
 * mesmo com o FFT chegando a ~20 Hz. Usado somente pela thread de desenho.
 */
class FrameSmoother(val bandCount: Int = SpectrumProcessor.BANDS) {
    val bands = FloatArray(bandCount)
    val peaks = FloatArray(bandCount)
    var bass = 0f; private set
    var level = 0f; private set

    /** 1 logo apos uma batida, caindo para 0. */
    var beat = 0f; private set

    /** Segundos desde o inicio, para animacoes continuas. */
    var time = 0f; private set

    private var lastBeatCount = -1

    fun update(target: AnalysisFrame?, nowNanos: Long, dt: Float, sensitivity: Float, smoothness: Float) {
        time += dt
        val t = if (target != null && nowNanos - target.timeNanos < STALE_NANOS) target else null
        val attack = coef(dt, 0.02f + 0.05f * smoothness)
        val decay = coef(dt, 0.08f + 0.45f * smoothness)
        for (i in 0 until bandCount) {
            val goal = if (t != null) (t.bands[i] * sensitivity).coerceIn(0f, 1f) else 0f
            val c = bands[i]
            bands[i] = c + (goal - c) * (if (goal > c) attack else decay)
            peaks[i] = max(bands[i], peaks[i] - PEAK_FALL_PER_SEC * dt)
        }
        val tb = if (t != null) (t.bass * sensitivity).coerceIn(0f, 1f) else 0f
        val tl = if (t != null) (t.level * sensitivity).coerceIn(0f, 1f) else 0f
        bass += (tb - bass) * (if (tb > bass) attack else decay)
        level += (tl - level) * (if (tl > level) attack else decay)

        if (t != null) {
            if (lastBeatCount != -1 && t.beatCount != lastBeatCount) beat = 1f
            lastBeatCount = t.beatCount
        }
        beat *= exp(-dt / 0.25f)
    }

    private fun coef(dt: Float, tau: Float) = 1f - exp(-dt / tau)

    companion object {
        private const val STALE_NANOS = 400_000_000L
        private const val PEAK_FALL_PER_SEC = 0.35f
    }
}
