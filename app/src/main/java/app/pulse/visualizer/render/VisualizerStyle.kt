package app.pulse.visualizer.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.settings.Palette
import app.pulse.visualizer.settings.Settings
import app.pulse.visualizer.settings.StyleKind
import kotlin.math.cos
import kotlin.math.sin

/** Um estilo desenha o quadro suavizado atual; todos partem do mesmo espectro. */
interface VisualizerStyle {
    fun DrawScope.draw(f: FrameSmoother, s: Settings, p: Palette)
}

/** Guarda uma instancia de cada estilo (eles reutilizam buffers entre quadros). */
class StyleSet {
    private val bars = BarsStyle()
    private val radial = RadialStyle()

    operator fun get(kind: StyleKind): VisualizerStyle = when (kind) {
        StyleKind.BARS, StyleKind.LYRICS -> bars
        StyleKind.RADIAL -> radial
    }
}

/** Amostra [bands] na posicao [pos] (0 = grave, 1 = agudo) com interpolacao linear. */
fun sampleBands(bands: FloatArray, pos: Float): Float {
    val x = pos.coerceIn(0f, 1f) * (bands.size - 1)
    val i = x.toInt().coerceAtMost(bands.size - 2)
    val t = x - i
    return bands[i] * (1f - t) + bands[i + 1] * t
}

/** Brilho de fundo: duas manchas de cor que derivam devagar e respiram com o grave. */
fun DrawScope.drawAmbient(f: FrameSmoother, s: Settings, p: Palette) {
    val radius = size.maxDimension * 0.6f
    val alpha = (0.09f + 0.20f * f.bass * s.pulse).coerceAtMost(0.4f)
    val t = f.time
    val c1 = Offset(size.width * (0.28f + 0.08f * sin(t * 0.13f)), size.height * (0.30f + 0.10f * cos(t * 0.17f)))
    val c2 = Offset(size.width * (0.74f + 0.08f * cos(t * 0.11f)), size.height * (0.72f + 0.10f * sin(t * 0.19f)))
    drawRect(Brush.radialGradient(listOf(p.colors.first().copy(alpha = alpha), Color.Transparent), c1, radius))
    drawRect(Brush.radialGradient(listOf(p.colors.last().copy(alpha = alpha), Color.Transparent), c2, radius))
}
