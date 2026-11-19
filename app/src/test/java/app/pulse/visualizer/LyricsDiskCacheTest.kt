package app.pulse.visualizer

import app.pulse.visualizer.lyrics.LyricLine
import app.pulse.visualizer.lyrics.Lyrics
import app.pulse.visualizer.lyrics.LyricsDiskCache
import app.pulse.visualizer.lyrics.LyricsFetcher
import app.pulse.visualizer.lyrics.LyricsResult
import app.pulse.visualizer.media.NowPlaying
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsDiskCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun lyrics(n: Int, text: String = "linha") =
        LyricsResult.Found(Lyrics((0 until n).map { LyricLine(it * 1000L, "$text $it") }, synced = true))

    @Test
    fun keepsFoundAndInstrumentalButNotNotFound() {
        val cache = LyricsDiskCache(tmp.newFolder())
        cache.put("a", lyrics(3))
        cache.put("b", LyricsResult.Instrumental)
        cache.put("c", LyricsResult.NotFound)
        assertEquals(lyrics(3), cache.get("a"))
        assertEquals(LyricsResult.Instrumental, cache.get("b"))
        assertNull(cache.get("c"))
        assertNull(cache.get("nunca tocada"))
    }

    @Test
    fun dropsTheLeastRecentlyUsedWhenOverTheCap() {
        val big = lyrics(40)
        // Mede o tamanho de uma letra para montar o teto: cabem 3, a limpeza deixa 2.
        val one = LyricsDiskCache(tmp.newFolder()).apply { put("x", big) }.size()
        val dir = tmp.newFolder()
        val cache = LyricsDiskCache(dir, maxBytes = one * 3 + one / 2, targetBytes = one * 2 + one / 5)
        for (i in 0 until 3) cache.put("musica $i", big)
        // Todas antigas; "musica 0" e lida agora e passa a ser a mais recente das tres.
        dir.listFiles()!!.forEach { it.setLastModified(1_000L) }
        cache.get("musica 0")
        cache.put("musica 3", big) // quatro letras: passa do teto e limpa as menos usadas
        assertTrue("passou do teto: ${cache.size()}", cache.size() <= one * 3 + one / 2)
        assertEquals(big, cache.get("musica 0"))
        assertEquals(big, cache.get("musica 3"))
        assertNull(cache.get("musica 1"))
        assertNull(cache.get("musica 2"))
    }

    @Test
    fun fetcherReadsFromDiskAfterTheAppForgetsTheMemory() = runBlocking {
        val track = NowPlaying("Real", "Kendrick Lamar", null)
        LyricsFetcher.disk = LyricsDiskCache(tmp.newFolder())
        try {
            LyricsFetcher.put(track, lyrics(2, "salva"))
            Thread.sleep(300) // a gravacao vinda de put() roda em outra thread
            LyricsFetcher.clearMemory()
            // Sem internet no teste: so acha porque esta no disco.
            assertEquals(lyrics(2, "salva"), LyricsFetcher.fetch(track))
        } finally {
            LyricsFetcher.disk = null
            LyricsFetcher.clearMemory()
        }
    }
}
