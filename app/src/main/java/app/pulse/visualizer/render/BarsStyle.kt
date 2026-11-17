package app.pulse.visualizer.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.settings.Palette
import app.pulse.visualizer.settings.Settings
import kotlin.math.abs
import kotlin.math.max

/** Barras arredondadas com picos e reflexo opcionais. */
class BarsStyle : VisualizerStyle {
    override fun DrawScope.draw(f: FrameSmoother, s: Settings, p: Palette) {
        val n = s.barCount
        val areaW = size.width * 0.86f
        val left = (size.width - areaW) / 2f
        val slot = areaW / n
        val bw = slot * s.thickness.coerceIn(0.25f, 0.92f)
        val maxH = size.height * (if (s.reflection) 0.50f else 0.60f)
        val base = size.height * (if (s.reflection) 0.68f else 0.80f)
        val scale = 1f + 0.06f * f.beat * s.pulse
        val radius = CornerRadius(bw / 2f)

        val brush = if (s.spectrumColor) {
            Brush.horizontalGradient(p.colors, startX = left, endX = left + areaW)
        } else {
            Brush.verticalGradient(p.colors.reversed(), startY = base - maxH, endY = base)
        }
        val gap = bw * 0.35f
        val peakH = max(3f, bw * 0.26f)

        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            val pos = if (s.mirror) abs(t - 0.5f) * 2f else t
            val h = max(bw, sampleBands(f.bands, pos) * maxH * scale)
            val x = left + slot * i + (slot - bw) / 2f
            drawRoundRect(brush, Offset(x, base - h), Size(bw, h), radius)
            if (s.reflection) {
                // Reflexo que some sozinho, sem cobrir o brilho de fundo.
                val rh = max(bw, h * 0.42f)
                val rc = if (s.spectrumColor) p.colorAt(t) else p.colors.first()
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(rc.copy(alpha = 0.26f), rc.copy(alpha = 0f)),
                        startY = base + gap, endY = base + gap + rh,
                    ),
                    Offset(x, base + gap), Size(bw, rh), radius,
                )
            }
            if (s.peaks) {
                val ph = max(bw, sampleBands(f.peaks, pos) * maxH * scale)
                if (ph > h + peakH) {
                    drawRoundRect(
                        brush, Offset(x, base - ph - peakH * 1.6f), Size(bw, peakH),
                        CornerRadius(peakH / 2f), alpha = 0.8f,
                    )
                }
            }
        }
    }
}
