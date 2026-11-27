package app.pulse.visualizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import app.pulse.visualizer.media.ArtColors
import app.pulse.visualizer.media.NowPlaying
import app.pulse.visualizer.settings.Palettes
import app.pulse.visualizer.ui.NowPlayingScene
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pulse.visualizer.audio.AnalysisFrame
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.audio.SourceKind
import app.pulse.visualizer.render.StyleSet
import app.pulse.visualizer.settings.Settings
import app.pulse.visualizer.settings.StyleKind
import app.pulse.visualizer.ui.AudioDiag
import app.pulse.visualizer.ui.SettingsSheet
import app.pulse.visualizer.ui.VisualizerCanvas
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.exp
import kotlin.math.sin

/** Gera imagens dos estilos com um espectro sintetico, em app/build/previews. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-land-mdpi")
class PreviewScreenshotTest {

    /** Espectro parecido com musica: grave forte, queda para os agudos, alguma textura. */
    private fun smoother(): FrameSmoother {
        val s = FrameSmoother()
        val bands = FloatArray(s.bandCount) { i ->
            val x = i / (s.bandCount - 1f)
            (0.95f * exp(-2.2f * x) + 0.22f * sin(i * 0.9f) * (1f - x) + 0.18f * sin(i * 0.37f + 1f))
                .coerceIn(0.03f, 1f)
        }
        val louder = AnalysisFrame(FloatArray(s.bandCount) { (bands[it] * 1.35f).coerceAtMost(1f) }, 0.9f, 0.5f, 0, 0f, 0L)
        val frame = AnalysisFrame(bands, 0.75f, 0.4f, 0, 0f, 0L)
        // Um instante mais alto antes, para os picos ficarem acima das barras.
        repeat(40) { s.update(louder, 0L, 0.016f, 1f, 0.5f) }
        repeat(25) { s.update(frame, 0L, 0.016f, 1f, 0.5f) }
        return s
    }

    @Composable
    private fun Scene(settings: Settings, sheet: Boolean = false) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VisualizerCanvas(settings.style, settings, smoother(), StyleSet(), { 0L })
            if (sheet) {
                SettingsSheet(
                    settings, AudioDiag(-18f, -31f, systemOk = true, micOk = true, active = SourceKind.SYSTEM),
                    update = {}, onClose = {}, modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }

    private fun shot(name: String, settings: Settings, sheet: Boolean = false) {
        captureRoboImage("$dir/$name.png") { Scene(settings, sheet) }
    }

    private val dir = System.getProperty("pulse.previews") ?: "build/previews"

    /** Capa de mentira: metade vermelha, metade clara, com um disco escuro. */
    private fun fakeArt(): Bitmap {
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(0xFFE9E6E4.toInt())
        p.color = 0xFFD81E2C.toInt()
        c.drawRect(0f, 0f, 600f, 330f, p)
        p.color = 0xFF2A0A0C.toInt()
        c.drawCircle(400f, 120f, 90f, p)
        p.color = 0xFFF2C14E.toInt()
        c.drawRect(40f, 40f, 200f, 120f, p)
        return bmp
    }

    /** Area do cartao lateral do Android Auto medida no simulador: so os 386 px da direita. */
    private val cardPadding = androidx.compose.foundation.layout.PaddingValues(
        start = 804.dp, top = 24.dp, end = 12.dp, bottom = 24.dp,
    )

    private fun nowPlaying(name: String, settings: Settings, withArt: Boolean, compact: Boolean = false) {
        val art = if (withArt) fakeArt() else null
        // Musicas reais em dominio publico, para as previas poderem ficar no repositorio publico.
        val track = NowPlaying("Hino à Alegria", "Ludwig van Beethoven", art)
        captureRoboImage("$dir/$name.png") {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NowPlayingScene(
                    settings, track, art, animateBackground = false,
                    contentPadding = if (compact) cardPadding else androidx.compose.foundation.layout.PaddingValues(0.dp),
                    controls = app.pulse.visualizer.ui.MediaActions(playing = true, {}, {}, {}),
                    compact = compact,
                ) {
                    val bg = art != null && settings.artBackground
                    VisualizerCanvas(
                        settings.style, if (bg) settings.copy(ambient = false) else settings,
                        smoother(), StyleSet(), { 0L },
                        palette = if (bg) ArtColors.palette(art!!) else Palettes[settings.palette],
                    )
                }
            }
        }
    }

    private fun lyricsShot(
        name: String,
        withArt: Boolean,
        s: Settings = Settings(style = StyleKind.LYRICS, palette = 4),
        compact: Boolean = false,
    ) {
        val art = if (withArt) fakeArt() else null
        val track = NowPlaying("Amazing Grace", "John Newton", art, durationMs = 263_000)
        val result = amazingGrace()
        // Na previa, a terceira linha esta sendo cantada, no meio de "lost".
        captureRoboImage("$dir/$name.png") {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NowPlayingScene(
                    s, track, art, animateBackground = false, lyricsMode = true,
                    contentPadding = if (compact) cardPadding else androidx.compose.foundation.layout.PaddingValues(0.dp),
                    controls = app.pulse.visualizer.ui.MediaActions(playing = true, {}, {}, {}),
                    compact = compact,
                ) {
                    app.pulse.visualizer.ui.LyricsPage(result, true, { 11_950L }, 263_000L, live = false)
                }
            }
        }
    }

    /**
     * "Amazing Grace" (John Newton, 1779; dominio publico), com tempo por silaba como o LRCLIB
     * entrega em "lyricsfile": cada linha dura 4,6 s e as silabas dividem esse tempo.
     */
    private fun amazingGrace(): app.pulse.visualizer.lyrics.LyricsResult {
        val verses = listOf(
            listOf("A", "ma", "zing ", "grace, ", "how ", "sweet ", "the ", "sound"),
            listOf("That ", "saved ", "a ", "wretch ", "like ", "me"),
            listOf("I ", "once ", "was ", "lost, ", "but ", "now ", "am ", "found"),
            listOf("Was ", "blind, ", "but ", "now ", "I ", "see"),
            listOf("'Twas ", "grace ", "that ", "taught ", "my ", "heart ", "to ", "fear"),
            listOf("And ", "grace ", "my ", "fears ", "re", "lieved"),
        )
        val lines = verses.mapIndexed { i, syllables ->
            val start = i * 5_000L
            val step = 4_600L / syllables.size
            val words = syllables.mapIndexed { k, s -> app.pulse.visualizer.lyrics.LyricWord(start + k * step, s) }
            app.pulse.visualizer.lyrics.LyricLine(start, syllables.joinToString(""), start + 4_600, words)
        }
        return app.pulse.visualizer.lyrics.LyricsResult.Found(app.pulse.visualizer.lyrics.Lyrics(lines, synced = true))
    }

    /**
     * GIF do preenchimento silaba por silaba: desenha a pagina Letra quadro a quadro com o tempo
     * andando (as duas primeiras linhas) e junta os quadros num GIF animado.
     */
    @Test
    fun lyricsGif() {
        val result = amazingGrace()
        val track = NowPlaying("Amazing Grace", "John Newton", null, durationMs = 263_000)
        val s = Settings(style = StyleKind.LYRICS, artMode = app.pulse.visualizer.settings.ArtMode.NONE)
        val framesDir = java.io.File("$dir/gif-frames").apply { deleteRecursively(); mkdirs() }
        val stepMs = 120L
        val frames = (0 until 84).map { i ->
            val pos = -250L + i * stepMs
            val file = java.io.File(framesDir, "f%03d.png".format(i))
            captureRoboImage(file.path) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    NowPlayingScene(s, track, null, animateBackground = false, lyricsMode = true) {
                        app.pulse.visualizer.ui.LyricsPage(result, true, { pos }, 263_000L, live = false)
                    }
                }
            }
            file
        }
        // Os quadros viram GIF no compilar.bat (tools/GifMaker.java): o ImageIO do Java nao existe
        // no classpath dos testes Android.
        check(frames.size == 84)
    }


    @Test
    fun cardBars() = nowPlaying("15-cartao-barras", Settings(style = StyleKind.BARS, barCount = 24), withArt = true, compact = true)

    @Test
    fun cardLyrics() = lyricsShot("16-cartao-letra", withArt = true, compact = true)

    @Test
    fun lyricsWithArt() = lyricsShot("11-letra-capa", withArt = true)

    @Test
    fun lyricsWithoutArt() = lyricsShot("12-letra-sem-imagem", withArt = false)

    @Test
    fun lyricsSideArt() = lyricsShot(
        "13-letra-imagem-lateral", withArt = true,
        Settings(style = StyleKind.LYRICS, artFull = true),
    )

    @Test
    fun lyricsFullScreen() = lyricsShot(
        "14-letra-tela-cheia", withArt = false,
        Settings(style = StyleKind.LYRICS, artMode = app.pulse.visualizer.settings.ArtMode.NONE),
    )

    @Test
    fun nowPlayingBars() = nowPlaying("7-musica-barras", Settings(style = StyleKind.BARS, barCount = 28), withArt = true)

    @Test
    fun nowPlayingRadialPlainBackground() =
        nowPlaying("9-musica-radial-sem-fundo", Settings(style = StyleKind.RADIAL, artBackground = false, palette = 4), withArt = true)

    @Test
    fun nowPlayingNoArt() = nowPlaying("10-musica-sem-imagem", Settings(style = StyleKind.BARS, palette = 2), withArt = false)

    @Test
    fun bars() = shot("1-barras", Settings(style = StyleKind.BARS))

    @Test
    fun radial() = shot("2-radial", Settings(style = StyleKind.RADIAL, palette = 1))

    @Test
    fun barsIntensityColor() =
        shot("4-barras-ambar", Settings(style = StyleKind.BARS, palette = 5, spectrumColor = false, mirror = false, barCount = 56))

    @Test
    fun radialNeon() = shot("5-radial-neon", Settings(style = StyleKind.RADIAL, palette = 4, spectrumColor = false))

    @Test
    fun settingsSheet() = shot("6-ajustes", Settings(style = StyleKind.BARS), sheet = true)
}
