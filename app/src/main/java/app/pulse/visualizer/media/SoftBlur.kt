package app.pulse.visualizer.media

import android.graphics.Bitmap

/**
 * Desfoque feito na CPU para o fundo da capa. O Modifier.blur so existe no Android 12+; em centrais
 * mais antigas o fundo era a capa reduzida a 16x16 e esticada, que aparece em blocos e vincos.
 * Aqui a imagem fica pequena (96 px) e passa por tres desfoques de caixa (quase gaussiano):
 * esticada depois, continua lisa.
 */
object SoftBlur {
    fun blurred(src: Bitmap, size: Int = 96, radius: Int = 10, passes: Int = 3): Bitmap {
        val small = Bitmap.createScaledBitmap(src, size, size, true)
        val px = IntArray(size * size)
        small.getPixels(px, 0, size, 0, 0, size, size)
        val out = blur(px, size, size, radius, passes)
        return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Desfoque de caixa repetido em ARGB, com as bordas estendidas. Funcao pura, testada na JVM. */
    fun blur(pixels: IntArray, w: Int, h: Int, radius: Int, passes: Int): IntArray {
        var a = pixels.copyOf()
        var b = IntArray(a.size)
        repeat(passes) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        return a
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val span = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = at(i)
                sa += p ushr 24
                sr += p shr 16 and 0xFF
                sg += p shr 8 and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                // Arredonda (em vez de truncar) para a imagem nao escurecer um pouco a cada passada.
                val half = span / 2
                val out = (((sa + half) / span) shl 24) or (((sr + half) / span) shl 16) or
                    (((sg + half) / span) shl 8) or ((sb + half) / span)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val add = at(i + r + 1)
                val rem = at(i - r)
                sa += (add ushr 24) - (rem ushr 24)
                sr += (add shr 16 and 0xFF) - (rem shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (rem shr 8 and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
