package app.pulse.visualizer.media

import android.graphics.Bitmap
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import app.pulse.visualizer.settings.ArtMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca foto do artista e capa do album na API publica da Deezer (sem chave).
 * So e usado quando "Buscar imagens na internet" esta ligado; envia apenas
 * o nome do artista e o titulo da musica.
 */
object ArtFetcher {
    // Ate ~48 MB de imagens grandes (1000x1000 ocupa 4 MB).
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Escolhe a imagem a mostrar para [track] conforme o modo, na maior resolucao que encontrar:
     * a capa original pelo endereco que o player informa, a capa de 1000 px da Deezer
     * (so se for parecida com a do player, para nao trocar por outro album) ou o bitmap do player.
     */
    suspend fun resolve(track: NowPlaying?, mode: ArtMode, online: Boolean, context: Context? = null): Bitmap? {
        if (track == null || mode == ArtMode.NONE) return null
        if (mode == ArtMode.ARTIST) {
            track.artistArt?.let { return it }
            if (online) artistPhoto(track.artist)?.let { return it }
        }

        var best = track.art
        fun consider(candidate: Bitmap?) {
            if (candidate != null && candidate.width > (best?.width ?: 0)) best = candidate
        }
        if (track.art == null || track.art.width < HIGH_RES) {
            // A capa que o player manda costuma ser pequena (Spotify ~300 px): baixa a original pela web,
            // do maior tamanho para o menor, e fica com a primeira que vier.
            for (uri in artUriCandidates(track.artUri)) {
                val img = fromUri(uri, online, context) ?: continue
                consider(img)
                break
            }
        }
        if (online && (best?.width ?: 0) < HIGH_RES) {
            val deezer = albumCover(track.artist, track.title)
            val ref = track.art
            if (deezer != null && (ref == null || ArtColors.similar(ref, deezer))) consider(deezer)
        }
        return best
    }

    /** Abaixo disso a capa fica borrada em tela cheia; tenta achar uma maior. */
    private const val HIGH_RES = 900

    private val spotifyId = Regex("""^(?:spotify:image:|https://i\.scdn\.co/image/)([0-9a-f]+)$""")
    private val spotifySized = Regex("""^(ab67616d)([0-9a-f]{8})([0-9a-f]+)$""")

    /**
     * Enderecos para baixar a capa informada pelo player, do maior para o menor. Para o Spotify
     * ("spotify:image:..." ou i.scdn.co) troca o codigo de tamanho no id: 000082c1 (~1000 px) e
     * 0000b273 (640 px) sao a mesma capa, so maior que a do player. Funcao pura, testada na JVM.
     */
    fun artUriCandidates(uri: String): List<String> {
        if (uri.isBlank()) return emptyList()
        val id = spotifyId.find(uri)?.groupValues?.get(1) ?: return listOf(uri)
        val sized = spotifySized.find(id) ?: return listOf("https://i.scdn.co/image/$id")
        val (prefix, _, rest) = sized.destructured
        return (listOf("000082c1", "0000b273").map { "https://i.scdn.co/image/$prefix$it$rest" } +
            "https://i.scdn.co/image/$id").distinct()
    }

    /** Capa pelo endereco do player: https (precisa de internet ligada) ou content:// local. */
    private suspend fun fromUri(uri: String, online: Boolean, context: Context?): Bitmap? {
        if (uri.isBlank()) return null
        return cached("u:$uri") {
            when {
                uri.startsWith("https://") && online -> get(uri).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                (uri.startsWith("content://") || uri.startsWith("android.resource://")) && context != null ->
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
                else -> null
            }
        }
    }

    suspend fun artistPhoto(artist: String): Bitmap? {
        val name = mainArtist(artist)
        if (name.isBlank()) return null
        return cached("a:$name") {
            fetchImage("https://api.deezer.com/search/artist?limit=1&q=${enc(name)}") { it.optString("picture_xl") }
        }
    }

    suspend fun albumCover(artist: String, title: String): Bitmap? {
        val name = mainArtist(artist)
        if (title.isBlank()) return null
        val query = if (name.isBlank()) title else "artist:\"$name\" track:\"$title\""
        return cached("t:$name|$title") {
            fetchImage("https://api.deezer.com/search?limit=1&q=${enc(query)}") {
                it.optJSONObject("album")?.optString("cover_xl").orEmpty()
            }
        }
    }

    /** "Justin Timberlake, JAY-Z" -> "Justin Timberlake". */
    fun mainArtist(artist: String): String =
        artist.split(",", " feat.", " feat ", " ft.", " & ", ";", " x ", ignoreCase = true).first().trim()

    private suspend fun cached(key: String, load: () -> Bitmap?): Bitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching(load).getOrNull()?.also { cache.put(key, it) }
        }
    }

    private fun fetchImage(searchUrl: String, pick: (JSONObject) -> String): Bitmap? {
        val json = JSONObject(String(get(searchUrl), Charsets.UTF_8))
        val first = json.optJSONArray("data")?.optJSONObject(0) ?: return null
        val url = pick(first)
        if (!url.startsWith("https://")) return null
        val bytes = get(url)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
