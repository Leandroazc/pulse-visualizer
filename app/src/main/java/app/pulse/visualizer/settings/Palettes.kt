package app.pulse.visualizer.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

class Palette(val name: String, val colors: List<Color>) {
    /** Cor em [t] (0..1) ao longo do degrade. */
    fun colorAt(t: Float): Color {
        val x = t.coerceIn(0f, 1f) * (colors.size - 1)
        val i = x.toInt().coerceAtMost(colors.size - 2)
        return lerp(colors[i], colors[i + 1], x - i)
    }
}

object Palettes {
    val all = listOf(
        Palette("Aurora", listOf(Color(0xFF5EF2C5), Color(0xFF4C8DFF), Color(0xFFB36BFF))),
        Palette("Pôr do sol", listOf(Color(0xFFFFB347), Color(0xFFFF5E62), Color(0xFFC644FC))),
        Palette("Oceano", listOf(Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFF5E5CE6))),
        Palette("Mono", listOf(Color(0xFFFFFFFF), Color(0xFFC7C7CC), Color(0xFF8E8E93))),
        Palette("Neon", listOf(Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFF0A84FF))),
        Palette("Âmbar", listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF6B00))),
        Palette("Menta", listOf(Color(0xFFA8E063), Color(0xFF30D158), Color(0xFF63E6BE))),
        Palette("Rosé", listOf(Color(0xFFFFD1DC), Color(0xFFFF8FAB), Color(0xFFFF375F))),
    )

    operator fun get(index: Int): Palette = all[index.coerceIn(0, all.lastIndex)]
}
