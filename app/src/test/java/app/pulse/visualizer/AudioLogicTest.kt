package app.pulse.visualizer

import app.pulse.visualizer.audio.AnalysisFrame
import app.pulse.visualizer.audio.AutoSourceSelector
import app.pulse.visualizer.audio.Fft
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.audio.SourceKind
import app.pulse.visualizer.audio.SpectrumProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioLogicTest {
    private val rate = 44100f
    private val n = 2048

    private fun sine(hz: Float, amp: Float = 1f) =
        FloatArray(n) { (amp * sin(2.0 * PI * hz * it / rate)).toFloat() }

    private fun mags(samples: FloatArray) = FloatArray(n / 2).also { Fft(n).magnitudes(samples, it) }

    @Test
    fun fftPutsSineInTheRightBinWithUnitMagnitude() {
        val m = mags(sine(1000f))
        val peak = m.indices.maxBy { m[it] }
        assertEquals(1000f / (rate / n), peak.toFloat(), 1f)
        assertEquals(1f, m[peak], 0.15f)
    }

    @Test
    fun processorMapsToneToMatchingLogBand() {
        for (hz in listOf(60f, 250f, 1000f, 4000f, 12000f)) {
            val p = SpectrumProcessor()
            val frame = p.process(mags(sine(hz)), rate / n, 0.02f)
            val peak = frame.bands.indices.maxBy { frame.bands[it] }
            val ratio = p.centerHz(peak) / hz
            assertTrue("tom de $hz Hz caiu em ${p.centerHz(peak)} Hz", ratio in 0.8f..1.25f)
            assertEquals(1f, frame.bands[peak], 0.001f)
        }
    }

    @Test
    fun silenceGivesZeroBandsAndVeryLowRawPeak() {
        val frame = SpectrumProcessor().process(FloatArray(n / 2), rate / n, 0.02f)
        assertTrue(frame.bands.all { it == 0f })
        assertTrue(frame.rawPeakDb < -100f)
    }

    @Test
    fun quietSignalIsBroughtUpByAutoGain() {
        val p = SpectrumProcessor()
        val quiet = mags(sine(1000f, amp = 0.01f)) // -40 dB
        // Depois de um trecho alto, o sinal baixo some...
        p.process(mags(sine(1000f)), rate / n, 0.02f)
        var frame = p.process(quiet, rate / n, 0.02f)
        assertTrue(frame.bands.max() < 0.1f)
        // ...e volta a encher a tela conforme o ganho automatico desce (2 dB/s).
        repeat(1500) { frame = p.process(quiet, rate / n, 0.02f) }
        assertTrue("pico = ${frame.bands.max()}", frame.bands.max() > 0.9f)
    }

    @Test
    fun bassBurstsAreCountedAsBeats() {
        val p = SpectrumProcessor()
        val kick = mags(sine(70f))
        val rest = mags(sine(70f, amp = 0.02f))
        var frame: AnalysisFrame? = null
        // 4 batidas: 0,1 s de bumbo a cada 0,5 s.
        repeat(100) { i ->
            frame = p.process(if (i % 25 < 5) kick else rest, rate / n, 0.02f)
        }
        assertEquals(4, frame!!.beatCount)
    }

    @Test
    fun smootherRisesFastFallsSlowAndDropsStaleFrames() {
        val s = FrameSmoother()
        val bands = FloatArray(s.bandCount) { 1f }
        val frame = AnalysisFrame(bands, 1f, 1f, 0, 0f, timeNanos = 0L)
        repeat(30) { s.update(frame, nowNanos = 0L, dt = 0.016f, sensitivity = 1f, smoothness = 0.5f) }
        assertTrue(s.bands[10] > 0.95f)

        // Sensibilidade menor reduz o alvo.
        repeat(120) { s.update(frame, 0L, 0.016f, sensitivity = 0.5f, smoothness = 0.5f) }
        assertEquals(0.5f, s.bands[10], 0.02f)

        // Quadro velho (fonte parou): cai para zero, e o pico cai mais devagar que a barra.
        s.update(frame, nowNanos = 1_000_000_000L, dt = 0.016f, sensitivity = 1f, smoothness = 0.5f)
        repeat(20) { s.update(frame, 1_000_000_000L, 0.016f, 1f, 0.5f) }
        assertTrue(s.bands[10] < 0.2f)
        assertTrue(s.peaks[10] > s.bands[10])
        repeat(300) { s.update(frame, 1_000_000_000L, 0.016f, 1f, 0.5f) }
        assertTrue(abs(s.bands[10]) < 0.01f)
    }

    @Test
    fun smootherPulsesOnNewBeat() {
        val s = FrameSmoother()
        val bands = FloatArray(s.bandCount)
        s.update(AnalysisFrame(bands, 0f, 0f, 3, 0f, 0L), 0L, 0.016f, 1f, 0.5f)
        assertEquals(0f, s.beat, 0.001f) // o primeiro quadro so registra a contagem
        s.update(AnalysisFrame(bands, 0f, 0f, 4, 0f, 0L), 0L, 0.016f, 1f, 0.5f)
        assertTrue(s.beat > 0.9f)
        repeat(60) { s.update(AnalysisFrame(bands, 0f, 0f, 4, 0f, 0L), 0L, 0.016f, 1f, 0.5f) }
        assertTrue(s.beat < 0.05f)
    }

    @Test
    fun autoSelectorPrefersSystemWhileItHasSignal() {
        val sel = AutoSourceSelector(holdMs = 3000)
        assertEquals(SourceKind.MIC, sel.active(0))
        sel.onSystemFrame(hasSignal = true, nowMs = 1000)
        assertEquals(SourceKind.SYSTEM, sel.active(1000))
        sel.onSystemFrame(hasSignal = false, nowMs = 3900)
        assertEquals(SourceKind.SYSTEM, sel.active(3900)) // pausa curta entre faixas
        assertEquals(SourceKind.MIC, sel.active(4100))
        sel.onSystemFrame(hasSignal = true, nowMs = 5000)
        assertEquals(SourceKind.SYSTEM, sel.active(5000))
    }

    @Test
    fun vibrantColorIgnoresGraysAndPicksTheSaturatedHue() {
        val px = IntArray(100) { if (it < 80) 0xFF808080.toInt() else 0xFFD81E2C.toInt() }
        val c = app.pulse.visualizer.media.ArtColors.vibrant(px)
        assertEquals(0xD8, c shr 16 and 0xFF)
        assertEquals(0x1E, c shr 8 and 0xFF)
        // Imagem sem cor: branco.
        assertEquals(0xFFFFFFFF.toInt(), app.pulse.visualizer.media.ArtColors.vibrant(IntArray(10) { 0xFF404040.toInt() }))
    }

    @Test
    fun mainArtistDropsFeaturedNames() {
        val f = app.pulse.visualizer.media.ArtFetcher
        assertEquals("Justin Timberlake", f.mainArtist("Justin Timberlake, JAY-Z"))
        assertEquals("Rihanna", f.mainArtist("Rihanna feat. Drake"))
        assertEquals("Rihanna", f.mainArtist("Rihanna"))
    }

    @Test
    fun lrcParserReadsTimesAndFindsCurrentLine() {
        val lrc = """
            [ar:Rihanna]
            [00:12.50]Primeira linha
            [00:15.2]Segunda
            [00:18.123][01:02.00]Refrao
            [00:20.00]
        """.trimIndent()
        val lines = app.pulse.visualizer.lyrics.LrcParser.parse(lrc)
        assertEquals(listOf(12_500L, 15_200L, 18_123L, 20_000L, 62_000L), lines.map { it.timeMs })
        assertEquals("Refrao", lines[2].text)
        assertEquals("", lines[3].text)
        val l = app.pulse.visualizer.lyrics.Lyrics(lines, synced = true)
        assertEquals(-1, l.indexAt(5_000, 0))
        assertEquals(0, l.indexAt(12_500, 0))
        assertEquals(1, l.indexAt(17_999, 0))
        assertEquals(4, l.indexAt(90_000, 0))
        // Sem tempos: proporcional a duracao.
        val plain = app.pulse.visualizer.lyrics.Lyrics(List(10) { app.pulse.visualizer.lyrics.LyricLine(0, "x") }, synced = false)
        assertEquals(5, plain.indexAt(100_000, 200_000))
    }

    @Test
    fun lyricsFileParserReadsWordTimes() {
        val yaml = """
            version: '1.0'
            metadata:
              title: Luz da Estrada
              duration_ms: 276000
            lines:
            - text: La-la, la-la (oh, estrada)
              start_ms: 1240
              end_ms: 3430
              words:
              - text: La-
                start_ms: 1240
              - text: 'la, '
                start_ms: 1580
              - text: '(oh, '
                start_ms: 2180
              - text: estrada)
                start_ms: 2860
            - start_ms: 19840
              text: "Vou \"seguindo\""
              end_ms: 22560
              words:
              - start_ms: 19840
                text: 'Vou '
              - text: 'seguindo '
                start_ms: 19900
              - text: 'D''água '
                start_ms: 20072
            plain: ''
        """.trimIndent()
        val lines = app.pulse.visualizer.lyrics.LyricsFileParser.parse(yaml)
        assertEquals(2, lines.size)
        assertEquals("La-la, (oh, estrada)", lines[0].text)
        assertEquals(listOf(1240L, 1580L, 2180L, 2860L), lines[0].words.map { it.startMs })
        assertEquals(3430L, lines[0].endMs)
        assertEquals("Vou seguindo D'água", lines[1].text)
        assertEquals("D'água ", lines[1].words[2].text)
        assertEquals(19840L, lines[1].timeMs)
        assertTrue(app.pulse.visualizer.lyrics.Lyrics(lines, true).wordSynced)
    }

    @Test
    fun offsetTextDescribesDirection() {
        assertEquals("No tempo", app.pulse.visualizer.ui.offsetText(0))
        assertEquals("Letra 0,3 s antes", app.pulse.visualizer.ui.offsetText(300))
        assertEquals("Letra 1,2 s depois", app.pulse.visualizer.ui.offsetText(-1200))
    }

    @Test
    fun similarCoversAreRecognized() {
        val red = IntArray(64) { if (it < 32) 0xFFD81E2C.toInt() else 0xFFEEEEEE.toInt() }
        val redJpeg = IntArray(64) { if (it < 32) 0xFFD0242A.toInt() else 0xFFE6E8E9.toInt() }
        val blue = IntArray(64) { 0xFF1030C0.toInt() }
        assertTrue(app.pulse.visualizer.media.ArtColors.similar(red, redJpeg))
        assertTrue(!app.pulse.visualizer.media.ArtColors.similar(red, blue))
    }

    @Test
    fun metadataCleanerRemovesNoiseForSearch() {
        val c = app.pulse.visualizer.lyrics.MetadataCleaner
        assertEquals("Umbrella", c.title("Umbrella (Official Music Video)"))
        assertEquals("Bohemian Rhapsody", c.title("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("Rihanna", c.artist("Rihanna, Drake"))
        assertEquals("Rihanna", c.artist("Rihanna feat. JAY-Z"))
    }

    @Test
    fun autoSelectorFallsBackWhenASourceIsUnavailable() {
        val sel = AutoSourceSelector()
        assertEquals(SourceKind.SYSTEM, sel.active(0, systemAvailable = true, micAvailable = false))
        sel.onSystemFrame(true, 0)
        assertEquals(SourceKind.MIC, sel.active(0, systemAvailable = false, micAvailable = true))
    }
}
