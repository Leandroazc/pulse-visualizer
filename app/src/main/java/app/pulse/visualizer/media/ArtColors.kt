package app.pulse.visualizer.media

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.pulse.visualizer.settings.Palette
import kotlin.math.max
import kotlin.math.min

/** Tira da imagem a cor que da o tom do visualizador. */
object ArtColors {
    /** Paleta clara tingida pela cor mais viva da imagem; le bem sobre o fundo feito da propria capa. */
    fun palette(art: Bitmap): Palette {
        val small = Bitmap.createScaledBitmap(art, 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        val vivid = Color(vibrant(px))
        return Palette("Capa", listOf(Color.White, lerp(Color.White, vivid, 0.45f), lerp(Color.White, vivid, 0.15f)))
    }

    /** true quando as duas imagens sao a mesma capa (comparando miniaturas 8x8). */
    fun similar(a: Bitmap, b: Bitmap): Boolean = similar(thumb(a), thumb(b))

    private fun thumb(bmp: Bitmap): IntArray {
        val s = Bitmap.createScaledBitmap(bmp, 8, 8, true)
        return IntArray(64).also { s.getPixels(it, 0, 8, 0, 0, 8, 8) }
    }

    /** Diferenca media por canal abaixo de ~18%: mesma capa, mesmo com outro corte ou compressao. */
    fun similar(a: IntArray, b: IntArray): Boolean {
        var diff = 0L
        for (i in a.indices) {
            for (shift in intArrayOf(16, 8, 0)) diff += kotlin.math.abs((a[i] shr shift and 0xFF) - (b[i] shr shift and 0xFF))
        }
        return diff / (a.size * 3.0) < 46
    }

    /**
     * Media dos pixels mais saturados e claros (ARGB). Imagens sem cor devolvem branco.
     * Funcao pura, para poder ser testada na JVM.
     */
    fun vibrant(pixels: IntArray): Int {
        var best = 0f
        val scores = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx <= 0f) 0f else (mx - mn) / mx
            (sat * mx).also { if (it > best) best = it }
        }
        if (best < 0.15f) return 0xFFFFFFFF.toInt()
        var r = 0f
        var g = 0f
        var b = 0f
        var w = 0f
        for (i in pixels.indices) {
            if (scores[i] < best * 0.7f) continue
            val p = pixels[i]
            r += (p shr 16 and 0xFF) * scores[i]
            g += (p shr 8 and 0xFF) * scores[i]
            b += (p and 0xFF) * scores[i]
            w += scores[i]
        }
        return (0xFF shl 24) or (Math.round(r / w) shl 16) or (Math.round(g / w) shl 8) or Math.round(b / w)
    }
}
