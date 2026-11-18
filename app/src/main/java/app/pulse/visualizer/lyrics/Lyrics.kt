/*
 * Busca e leitura de letras adaptadas do projeto auto-lyrics
 * (github.com/GitUpGitUp/auto-lyrics), distribuido sob a licenca MIT.
 * Veja TERCEIROS.md na raiz do projeto.
 */
package app.pulse.visualizer.lyrics

import android.util.LruCache
import app.pulse.visualizer.media.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Um trecho cantado (palavra ou silaba) e o instante em que comeca. O texto inclui o espaco final. */
data class LyricWord(val startMs: Long, val text: String)

/**
 * Uma linha da letra; [timeMs] e o instante em que ela comeca a ser cantada.
 * Com [words], o texto da linha e exatamente a juncao das palavras (para achar cada uma na tela).
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val endMs: Long = -1,
    val words: List<LyricWord> = emptyList(),
)

/** Letra de uma musica. [synced] = tem tempos por linha (LRC); senao e so o texto. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** true quando ha tempo por palavra (destaque palavra a palavra, como no Apple Music). */
    val wordSynced: Boolean get() = synced && lines.any { it.words.isNotEmpty() }

    /** Linha que esta sendo cantada em [posMs]; -1 antes da primeira. */
    fun indexAt(posMs: Long, durationMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (!synced) {
            // Sem tempos: avanca proporcionalmente a duracao da musica.
            if (durationMs <= 0) return -1
            return ((posMs.toDouble() / durationMs) * lines.size).toInt().coerceIn(0, lines.lastIndex)
        }
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

/** Le o formato LRC: "[mm:ss.xx] texto", com um ou mais tempos por linha. */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> =
        lrc.lineSequence().flatMap { raw ->
            var rest = raw.trim()
            val times = mutableListOf<Long>()
            while (true) {
                val m = stamp.find(rest)?.takeIf { it.range.first == 0 } ?: break
                times += toMs(m)
                rest = rest.substring(m.range.last + 1)
            }
            // Tags de cabecalho ([ar:...], [ti:...]) nao tem tempo e sao ignoradas.
            val text = rest.replace(Regex("""<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>"""), "").trim()
            times.map { LyricLine(it, text) }.asSequence()
        }.sortedBy { it.timeMs }.toList()

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min * 60_000 + sec * 1_000 + ms
    }
}

/**
 * Le o campo "lyricsfile" do LRCLIB: um YAML com linhas (start_ms, end_ms) e, dentro delas,
 * palavras com start_ms. So o pedaco do YAML que o LRCLIB usa e entendido.
 */
object LyricsFileParser {
    fun parse(yaml: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        var inLines = false
        var text: String? = null
        var start = -1L
        var end = -1L
        var words = mutableListOf<LyricWord>()
        var wordText: String? = null
        var wordStart = -1L

        fun flushWord() {
            val t = wordText
            if (t != null && wordStart >= 0) words += LyricWord(wordStart, t)
            wordText = null
            wordStart = -1
        }

        fun flushLine() {
            flushWord()
            val t = text
            if (t != null && start >= 0) {
                val joined = words.joinToString("") { it.text }
                // Usa o texto das palavras quando existem, para os indices baterem com o desenho.
                out += if (words.isNotEmpty()) LyricLine(start, joined.trimEnd(), end, words.toList())
                else LyricLine(start, t, end)
            }
            text = null
            start = -1
            end = -1
            words = mutableListOf()
        }

        for (raw in yaml.lineSequence()) {
            if (raw.isBlank()) continue
            val indent = raw.length - raw.trimStart().length
            val line = raw.trim()
            if (indent == 0 && !line.startsWith("-")) {
                inLines = line == "lines:"
                if (!inLines) flushLine()
                continue
            }
            if (!inLines) continue
            // "- chave: valor" abre um item novo; a chave conta como se estivesse 2 espacos adiante.
            var level = indent
            var entry = line
            if (line.startsWith("- ")) {
                if (indent == 0) flushLine() else if (indent == 2) flushWord()
                level += 2
                entry = line.substring(2).trim()
            }
            val key = entry.substringBefore(':')
            val value = entry.substringAfter(':', "")
            when {
                level == 2 && key == "text" -> text = scalar(value)
                level == 2 && key == "start_ms" -> start = num(value)
                level == 2 && key == "end_ms" -> end = num(value)
                level == 4 && key == "text" -> wordText = scalar(value)
                level == 4 && key == "start_ms" -> wordStart = num(value)
            }
        }
        flushLine()
        return out.sortedBy { it.timeMs }
    }

    private fun num(value: String) = value.trim().toLongOrNull() ?: -1L

    /** Valor YAML de uma linha: 'aspas simples', "aspas duplas" ou texto puro. */
    internal fun scalar(v: String): String {
        val s = v.trim()
        return when {
            s.length >= 2 && s.startsWith("'") && s.endsWith("'") -> s.substring(1, s.length - 1).replace("''", "'")
            s.length >= 2 && s.startsWith("\"") && s.endsWith("\"") -> unescape(s.substring(1, s.length - 1))
            else -> s
        }
    }

    private fun unescape(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> b.append('\n')
                    't' -> b.append('\t')
                    'u' -> if (i + 5 < s.length) {
                        b.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> b.append(n)
                }
                i += 2
            } else {
                b.append(c)
                i++
            }
        }
        return b.toString()
    }
}

/** Tira do titulo/artista o que atrapalha a busca ("(Official Video)", "Remastered", "feat."...). */
object MetadataCleaner {
    private val quality = Regex(
        """\b(lossless|hi-?res|flac|hi-?fi|dolby\s*atmos|spatial\s*audio|atmos|mqa|\d{2,3}\s*kbps|\d{2}-?bit|\d{2,3}(\.\d)?\s*khz)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val noise = Regex(
        """\s*[(\[][^)\]]*\b(official|video|lyric|lyrics|audio|visualizer|remaster(ed)?|explicit|clean)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val dashRemaster = Regex("""\s+-\s+.*\b(remaster(ed)?|version|mono|stereo|live)\b.*$""", RegexOption.IGNORE_CASE)

    fun title(raw: String): String =
        raw.replace(noise, "").replace(dashRemaster, "").replace(quality, "").squeeze().ifBlank { raw.trim() }

    fun artist(raw: String): String {
        val first = raw.split(Regex("""\s*(,|&|;|•|·|\||\bfeat\.?|\bft\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)).first()
        return first.replace(quality, "").squeeze().ifBlank { raw.trim() }
    }

    private fun String.squeeze() = replace(Regex("""\s{2,}"""), " ").trim().trim('-', '–', '—', ',', ':').trim()
}

/** Resultado da busca, para a tela mostrar o estado certo. */
sealed interface LyricsResult {
    data object Loading : LyricsResult
    data object NotFound : LyricsResult
    data object Instrumental : LyricsResult
    data class Found(val lyrics: Lyrics) : LyricsResult
}

/**
 * Busca a letra no LRCLIB (lrclib.net, gratuito e sem chave). Envia so titulo, artista,
 * album e duracao da musica. Prefere letra sincronizada; senao usa o texto simples.
 */
object LyricsFetcher {
    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "Pulse Visualizer 1.2 (app pessoal)"
    private val cache = LruCache<String, LyricsResult>(40)

    private val _version = MutableStateFlow(0)
    /** Muda quando chega uma letra de fora (do celular): a tela busca de novo e acha no cache. */
    val version: StateFlow<Int> = _version

    private fun key(track: NowPlaying) =
        "${MetadataCleaner.title(track.title)}|${MetadataCleaner.artist(track.artist)}".lowercase()

    /** Guarda uma letra ja pronta (recebida do Pulse do celular, na central sem internet). */
    fun put(track: NowPlaying, result: LyricsResult) {
        if (result == LyricsResult.Loading) return
        cache.put(key(track), result)
        _version.value++
    }

    suspend fun fetch(track: NowPlaying): LyricsResult = withContext(Dispatchers.IO) {
        val title = MetadataCleaner.title(track.title)
        val artist = MetadataCleaner.artist(track.artist)
        val key = key(track)
        cache.get(key)?.let { return@withContext it }

        val durSec = (track.durationMs / 1000).toInt()
        val candidates = buildList {
            if (durSec > 0) get(title, artist, track.album, durSec)?.let(::add)
            addAll(search(title, artist))
        }.filter { c ->
            val d = c.optDouble("duration", 0.0)
            durSec <= 0 || d <= 0.0 || abs(d - durSec) <= 10
        }

        val result = pick(candidates)
        if (result != null) cache.put(key, result)
        result ?: LyricsResult.NotFound
    }

    private fun pick(list: List<JSONObject>): LyricsResult? {
        // 1) Tempo por palavra (campo "lyricsfile" do LRCLIB).
        for (c in list) {
            if (!c.optBoolean("hasWordSync")) continue
            val yaml = c.str("lyricsfile") ?: continue
            val lines = runCatching { LyricsFileParser.parse(yaml) }.getOrNull().orEmpty()
            if (lines.count { it.words.isNotEmpty() } >= lines.size / 2 && lines.isNotEmpty()) {
                return LyricsResult.Found(Lyrics(lines, synced = true))
            }
        }
        // 2) Tempo por linha.
        list.firstNotNullOfOrNull { it.str("syncedLyrics") }?.let { lrc ->
            val lines = LrcParser.parse(lrc)
            if (lines.any { it.text.isNotBlank() }) return LyricsResult.Found(Lyrics(lines, synced = true))
        }
        list.firstNotNullOfOrNull { it.str("plainLyrics") }?.let { plain ->
            val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(0, it) }
            if (lines.isNotEmpty()) return LyricsResult.Found(Lyrics(lines, synced = false))
        }
        if (list.any { it.optBoolean("instrumental") }) return LyricsResult.Instrumental
        // Lista vazia pode ser falta de internet: nao guarda, tenta de novo na proxima vez.
        return if (list.isEmpty()) null else LyricsResult.NotFound
    }

    private fun get(title: String, artist: String, album: String, dur: Int): JSONObject? =
        request("$BASE/get?track_name=${enc(title)}&artist_name=${enc(artist)}&album_name=${enc(album)}&duration=$dur")
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun search(title: String, artist: String): List<JSONObject> {
        val body = request("$BASE/search?track_name=${enc(title)}&artist_name=${enc(artist)}") ?: return emptyList()
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun request(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", AGENT)
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
}
