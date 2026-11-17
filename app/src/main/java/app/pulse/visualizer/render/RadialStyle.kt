package app.pulse.visualizer.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.settings.Palette
import app.pulse.visualizer.settings.Settings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Anel de barras em volta de um nucleo que pulsa com o grave. */
class RadialStyle : VisualizerStyle {
    override fun DrawScope.draw(f: FrameSmoother, s: Settings, p: Palette) {
        val c = center
        val m = size.minDimension
        val r = m * 0.20f * (1f + 0.10f * f.bass * s.pulse + 0.05f * f.beat * s.pulse)
        val maxL = m * 0.20f
        val n = (s.barCount * 2).coerceIn(32, 128)
        val sw = (2f * PI.toFloat() * r / n) * s.thickness.coerceIn(0.25f, 0.92f)

        // Nucleo: brilho suave + aro fino.
        val core = p.colorAt(0.5f)
        drawCircle(
            Brush.radialGradient(
                listOf(core.copy(alpha = 0.10f + 0.40f * f.bass), Color.Transparent),
                center = c, radius = r * 0.96f,
            ),
            radius = r * 0.96f, center = c,
        )
        drawCircle(Color.White.copy(alpha = 0.14f), radius = r - sw, center = c, style = Stroke(width = max(1.5f, m * 0.002f)))

        val intensityBrush = if (s.spectrumColor) null else {
            val inner = r / (r + maxL)
            val stops = p.colors.mapIndexed { i, col ->
                (inner + (1f - inner) * i / (p.colors.size - 1)) to col
            }.toTypedArray()
            Brush.radialGradient(*stops, center = c, radius = r + maxL)
        }

        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            // Espelhado: grave no topo, agudo embaixo, simetrico nos dois lados.
            val pos = if (s.mirror) 1f - abs(2f * t - 1f) else t
            val v = sampleBands(f.bands, pos)
            val ang = (-0.5f + 2f * t) * PI.toFloat()
            val dx = cos(ang)
            val dy = sin(ang)
            val len = max(0.5f, v * maxL)
            val start = Offset(c.x + dx * r, c.y + dy * r)
            val end = Offset(c.x + dx * (r + len), c.y + dy * (r + len))
            if (intensityBrush != null) {
                drawLine(intensityBrush, start, end, strokeWidth = sw, cap = StrokeCap.Round)
            } else {
                drawLine(p.colorAt(pos), start, end, strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}
