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

    private fun nowPlaying(name: String, settings: Settings, withArt: Boolean) {
        val art = if (withArt) fakeArt() else null
        val track = NowPlaying("Hino à Alegria", "Ludwig van Beethoven", art)
        captureRoboImage("$dir/$name.png") {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NowPlayingScene(
                    settings, track, art, animateBackground = false,
                    controls = app.pulse.visualizer.ui.MediaActions(playing = true, {}, {}, {}),
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
    ) {
        val art = if (withArt) fakeArt() else null
        val track = NowPlaying("Amazing Grace", "John Newton", art, durationMs = 263_000)
        val lines = listOf(
            0L to "Amazing grace", 9_000L to "How sweet the sound", 13_000L to "That saved a wretch like me",
            17_000L to "I once was lost, but now am found", 22_000L to "",
            26_000L to "Was blind but now I see it all", 30_000L to "'Twas grace that taught my heart to fear",
            34_000L to "And grace my fears relieved", 38_000L to "How precious did that grace appear",
        ).map { app.pulse.visualizer.lyrics.LyricLine(it.first, it.second) }
        // A linha atual (26 s) tem tempo por palavra; na previa, "now" esta sendo cantada.
        val words = listOf(26_000L to "Was ", 26_300L to "blind ", 26_700L to "but ", 27_000L to "now ", 28_200L to "I ",
            28_500L to "see ", 28_800L to "it ", 29_200L to "all")
            .map { app.pulse.visualizer.lyrics.LyricWord(it.first, it.second) }
        val withWords = lines.map {
            if (it.timeMs == 26_000L) it.copy(text = words.joinToString("") { w -> w.text }, endMs = 29_900, words = words) else it
        }
        val result = app.pulse.visualizer.lyrics.LyricsResult.Found(app.pulse.visualizer.lyrics.Lyrics(withWords, synced = true))
        captureRoboImage("$dir/$name.png") {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NowPlayingScene(
                    s, track, art, animateBackground = false, lyricsMode = true,
                    controls = app.pulse.visualizer.ui.MediaActions(playing = true, {}, {}, {}),
                ) {
                    app.pulse.visualizer.ui.LyricsPage(result, true, { 27_500L }, 263_000L, live = false)
                }
            }
        }
    }

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
    fun nowPlayingWaves() = nowPlaying("8-musica-ondas", Settings(style = StyleKind.WAVES), withArt = true)

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
    fun waves() = shot("3-ondas", Settings(style = StyleKind.WAVES, palette = 2))

    @Test
    fun barsIntensityColor() =
        shot("4-barras-ambar", Settings(style = StyleKind.BARS, palette = 5, spectrumColor = false, mirror = false, barCount = 56))

    @Test
    fun radialNeon() = shot("5-radial-neon", Settings(style = StyleKind.RADIAL, palette = 4, spectrumColor = false))

    @Test
    fun settingsSheet() = shot("6-ajustes", Settings(style = StyleKind.BARS), sheet = true)
}
