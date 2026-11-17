package app.pulse.visualizer.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** FFT radix-2 com janela de Hann. [n] precisa ser potencia de 2. */
class Fft(val n: Int) {
    private val cosT = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2.0 * PI * it / n).toFloat() }
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val rev = IntArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)

    init {
        require(n >= 2 && n and (n - 1) == 0) { "n deve ser potencia de 2" }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /**
     * Calcula as magnitudes de [samples] (n amostras em -1..1) em [out] (n/2 posicoes).
     * Normalizado para que uma senoide de amplitude 1 resulte em ~1 no bin correspondente.
     */
    fun magnitudes(samples: FloatArray, out: FloatArray) {
        for (i in 0 until n) {
            re[rev[i]] = samples[i] * window[i]
            im[i] = 0f
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (j in 0 until half) {
                    val c = cosT[j * step]
                    val s = -sinT[j * step]
                    val a = i + j
                    val b = a + half
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                i += len
            }
            len = len shl 1
        }
        val norm = 4f / n
        for (k in 0 until n / 2) out[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * norm
    }
}
