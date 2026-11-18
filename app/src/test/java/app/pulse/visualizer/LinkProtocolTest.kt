package app.pulse.visualizer

import android.graphics.Bitmap
import android.os.Looper
import app.pulse.visualizer.link.LinkProtocol
import app.pulse.visualizer.lyrics.LyricLine
import app.pulse.visualizer.lyrics.LyricWord
import app.pulse.visualizer.lyrics.Lyrics
import app.pulse.visualizer.lyrics.LyricsResult
import app.pulse.visualizer.media.NowPlaying
import app.pulse.visualizer.media.NowPlayingMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkProtocolTest {
    @Test
    fun framesRoundTripInOrder() {
        val buf = ByteArrayOutputStream()
        val out = DataOutputStream(buf)
        LinkProtocol.write(out, LinkProtocol.META, byteArrayOf(1, 2, 3))
        LinkProtocol.write(out, LinkProtocol.CMD, byteArrayOf(NowPlayingMonitor.CMD_NEXT.toByte()))
        val input = DataInputStream(ByteArrayInputStream(buf.toByteArray()))
        val (t1, b1) = LinkProtocol.read(input)
        val (t2, b2) = LinkProtocol.read(input)
        assertEquals(LinkProtocol.META, t1)
        assertEquals(listOf<Byte>(1, 2, 3), b1.toList())
        assertEquals(LinkProtocol.CMD, t2)
        assertEquals(NowPlayingMonitor.CMD_NEXT, b2[0].toInt())
    }

    @Test
    fun commandsAreTextWithoutZeroBytes() {
        for (cmd in listOf(NowPlayingMonitor.CMD_PREVIOUS, NowPlayingMonitor.CMD_PLAY_PAUSE, NowPlayingMonitor.CMD_NEXT)) {
            val bytes = LinkProtocol.encodeCommand(cmd)
            assertTrue(bytes.none { it.toInt() == 0 })
            assertEquals(cmd, LinkProtocol.decodeCommand(bytes[0].toInt()))
        }
        assertNull(LinkProtocol.decodeCommand('\n'.code))
        assertNull(LinkProtocol.decodeCommand(0))
    }

    @Test
    fun metaKeepsAccentsAndNumbers() {
        val m = LinkProtocol.Meta("Coração", "Anitta, J Balvin", "Álbum", 215_000, 61_500, true, 1f)
        assertEquals(m, LinkProtocol.decodeMeta(LinkProtocol.encodeMeta(m)))
    }

    @Test
    fun lyricsKeepLineAndWordTimes() {
        val lyrics = Lyrics(
            listOf(
                LyricLine(1_000, "Olá mundo", 2_500, listOf(LyricWord(1_000, "Olá "), LyricWord(1_600, "mundo"))),
                LyricLine(3_000, "Segunda linha"),
            ),
            synced = true,
        )
        val (key, back) = LinkProtocol.decodeLyrics(LinkProtocol.encodeLyrics("k", LyricsResult.Found(lyrics)))
        assertEquals("k", key)
        assertEquals(LyricsResult.Found(lyrics), back)
        assertEquals(
            LyricsResult.Instrumental,
            LinkProtocol.decodeLyrics(LinkProtocol.encodeLyrics("k", LyricsResult.Instrumental)).second,
        )
    }

    @Test
    fun imageCarriesItsTrackKey() {
        val bmp = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
        val (key, back) = LinkProtocol.decodeImage(LinkProtocol.encodeImage("música\u0000artista", bmp))
        assertEquals("música\u0000artista", key)
        assertNotNull(back)
    }

    @Test
    fun remoteTrackWinsAndCommandsGoToThePhone() {
        val monitor = NowPlayingMonitor(RuntimeEnvironment.getApplication())
        val sent = mutableListOf<Int>()
        monitor.remoteCommand = { sent += it }
        val track = NowPlaying("Música", "Artista", null, durationMs = 200_000)

        monitor.applyRemote(track, playing = true, positionMs = 10_000, playbackSpeed = 1f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(track, monitor.state.value)
        assertTrue(monitor.playing.value)
        assertTrue(monitor.positionMs() >= 10_000)

        monitor.next()
        monitor.playPause()
        assertEquals(listOf(NowPlayingMonitor.CMD_NEXT, NowPlayingMonitor.CMD_PLAY_PAUSE), sent)
        assertFalse(monitor.playing.value)

        monitor.applyRemote(null, false, 0, 1f)
        assertNull(monitor.state.value)
        monitor.next()
        assertEquals(2, sent.size) // sem musica do celular, nada vai para ele
    }
}
