package app.pulse.visualizer.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.settings.Palette
import app.pulse.visualizer.settings.Settings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Camadas translucidas de ondas que se somam em luz, simetricas em torno do centro. */
class WavesStyle : VisualizerStyle {
    private class Layer(val freq: Float, val speed: Float, val phase: Float, val gain: Float, val alpha: Float)

    private val layers = arrayOf(
        Layer(freq = 1.0f, speed = 0.55f, phase = 0.0f, gain = 1.00f, alpha = 0.50f),
        Layer(freq = 1.6f, speed = -0.80f, phase = 1.9f, gain = 0.78f, alpha = 0.45f),
        Layer(freq = 2.3f, speed = 1.10f, phase = 4.1f, gain = 0.58f, alpha = 0.40f),
    )
    private val path = Path()
    private val top = FloatArray(POINTS + 1)
    private val bottom = FloatArray(POINTS + 1)

    override fun DrawScope.draw(f: FrameSmoother, s: Settings, p: Palette) {
        val cy = size.height * 0.5f
        val maxA = size.height * 0.36f
        val w = size.width
        val twoPi = 2f * PI.toFloat()
        val kick = 1f + 0.25f * f.beat * s.pulse

        layers.forEachIndexed { j, layer ->
            for (k in 0..POINTS) {
                val x = k / POINTS.toFloat()
                // sin(pi) em float fica levemente negativo; sem o limite, pow devolveria NaN.
                val taper = sin(PI.toFloat() * x).coerceAtLeast(0f).pow(1.4f)
                val pos = if (s.mirror) abs(x - 0.5f) * 2f else x
                // Media de tres amostras para a envoltoria ficar macia.
                val v = (sampleBands(f.bands, pos - 0.04f) + sampleBands(f.bands, pos) + sampleBands(f.bands, pos + 0.04f)) / 3f
                val amp = taper * maxA * (0.035f + v * layer.gain * kick)
                val a = twoPi * layer.freq * x + f.time * layer.speed + layer.phase
                top[k] = cy - amp * (0.65f + 0.35f * sin(a))
                bottom[k] = cy + amp * (0.65f + 0.35f * sin(a + 1.7f))
            }
            path.rewind()
            path.moveTo(0f, top[0])
            for (k in 1..POINTS) {
                val x0 = (k - 1) * w / POINTS
                val x1 = k * w / POINTS
                path.quadraticTo(x0, top[k - 1], (x0 + x1) / 2f, (top[k - 1] + top[k]) / 2f)
            }
            path.lineTo(w, top[POINTS])
            path.lineTo(w, bottom[POINTS])
            for (k in POINTS downTo 1) {
                val x0 = k * w / POINTS
                val x1 = (k - 1) * w / POINTS
                path.quadraticTo(x0, bottom[k], (x0 + x1) / 2f, (bottom[k] + bottom[k - 1]) / 2f)
            }
            path.lineTo(0f, bottom[0])
            path.close()

            val brush = if (s.spectrumColor) {
                // Cada camada desloca o degrade para as cores se misturarem.
                val cols = List(p.colors.size) { p.colors[(it + j) % p.colors.size] }
                Brush.horizontalGradient(cols, startX = 0f, endX = w)
            } else {
                Brush.verticalGradient(
                    listOf(p.colors.last(), p.colors[j % p.colors.size], p.colors.last()),
                    startY = cy - maxA, endY = cy + maxA,
                )
            }
            drawPath(path, brush, alpha = layer.alpha, blendMode = BlendMode.Plus)
        }
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent)),
            Offset(0f, cy), Offset(w, cy), strokeWidth = 1.5f,
        )
    }

    private companion object {
        const val POINTS = 72
    }
}
