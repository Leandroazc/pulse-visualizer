package app.pulse.visualizer.link

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.pulse.visualizer.lyrics.LyricLine
import app.pulse.visualizer.lyrics.LyricWord
import app.pulse.visualizer.lyrics.Lyrics
import app.pulse.visualizer.lyrics.LyricsResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/**
 * Formato das mensagens entre o Pulse do celular e o da central, por um canal Bluetooth (RFCOMM).
 * Cada mensagem: tipo (int), tamanho (int) e o conteudo.
 */
object LinkProtocol {
    /**
     * Porta serial padrao (SPP). Centrais com modulo Bluetooth proprio (Goodocom, "goc") trocam o
     * BluetoothSocket do Android por uma ponte para o modulo, que so procura este servico no celular.
     */
    val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val SERVICE_NAME = "Pulse"

    /** Celular -> central: musica, estado e posicao (JSON). */
    const val META = 1
    /** Celular -> central: capa (chave da musica + JPEG). */
    const val ART = 2
    /** Celular -> central: foto do artista (chave da musica + JPEG). */
    const val ARTIST_ART = 3
    /** Celular -> central: letra (JSON). */
    const val LYRICS = 4
    /** Central -> celular: um botao (NowPlayingMonitor.CMD_*). Veja [encodeCommand]. */
    const val CMD = 5

    /**
     * Botoes vao da central ao celular como texto ("P", "T", "N" + quebra de linha), nao em
     * mensagem binaria: a ponte do modulo Goodocom perde os bytes zero no sentido central -> celular.
     */
    fun encodeCommand(cmd: Int): ByteArray = when (cmd) {
        app.pulse.visualizer.media.NowPlayingMonitor.CMD_PREVIOUS -> "P\n"
        app.pulse.visualizer.media.NowPlayingMonitor.CMD_PLAY_PAUSE -> "T\n"
        app.pulse.visualizer.media.NowPlayingMonitor.CMD_NEXT -> "N\n"
        else -> "\n"
    }.toByteArray(Charsets.US_ASCII)

    /** Le um byte de comando; devolve o CMD_* ou null para qualquer outro byte (ignorado). */
    fun decodeCommand(b: Int): Int? = when (b.toChar()) {
        'P' -> app.pulse.visualizer.media.NowPlayingMonitor.CMD_PREVIOUS
        'T' -> app.pulse.visualizer.media.NowPlayingMonitor.CMD_PLAY_PAUSE
        'N' -> app.pulse.visualizer.media.NowPlayingMonitor.CMD_NEXT
        else -> null
    }

    private const val MAX_FRAME = 4 * 1024 * 1024
    private const val MAX_IMAGE = 720

    fun write(out: DataOutputStream, type: Int, payload: ByteArray) {
        synchronized(out) {
            out.writeInt(type)
            out.writeInt(payload.size)
            out.write(payload)
            out.flush()
        }
    }

    fun read(input: DataInputStream): Pair<Int, ByteArray> {
        val type = input.readInt()
        val size = input.readInt()
        if (size !in 0..MAX_FRAME) throw IOException("mensagem invalida ($size bytes)")
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return type to bytes
    }

    /** Identifica a musica, para descartar capa ou letra que chegue atrasada de outra. */
    fun trackKey(title: String, artist: String) = "$title\u0000$artist"

    data class Meta(
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0,
        val positionMs: Long = 0,
        val playing: Boolean = false,
        val speed: Float = 1f,
    )

    fun encodeMeta(m: Meta): ByteArray = JSONObject()
        .put("title", m.title)
        .put("artist", m.artist)
        .put("album", m.album)
        .put("duration", m.durationMs)
        .put("position", m.positionMs)
        .put("playing", m.playing)
        .put("speed", m.speed.toDouble())
        .toString().toByteArray(Charsets.UTF_8)

    fun decodeMeta(bytes: ByteArray): Meta {
        val j = JSONObject(String(bytes, Charsets.UTF_8))
        return Meta(
            title = j.optString("title"),
            artist = j.optString("artist"),
            album = j.optString("album"),
            durationMs = j.optLong("duration"),
            positionMs = j.optLong("position"),
            playing = j.optBoolean("playing"),
            speed = j.optDouble("speed", 1.0).toFloat(),
        )
    }

    fun encodeImage(key: String, bitmap: Bitmap): ByteArray {
        val big = max(bitmap.width, bitmap.height)
        val scaled = if (big > MAX_IMAGE) {
            val f = MAX_IMAGE.toFloat() / big
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val buf = ByteArrayOutputStream()
        DataOutputStream(buf).use { out ->
            out.writeUTF(key)
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        return buf.toByteArray()
    }

    fun decodeImage(bytes: ByteArray): Pair<String, Bitmap?> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val key = input.readUTF()
        return key to BitmapFactory.decodeStream(input)
    }

    fun encodeLyrics(key: String, result: LyricsResult): ByteArray {
        val j = JSONObject().put("key", key)
        when (result) {
            is LyricsResult.Found -> {
                j.put("status", "found").put("synced", result.lyrics.synced)
                val lines = JSONArray()
                for (l in result.lyrics.lines) {
                    val words = JSONArray()
                    for (w in l.words) words.put(JSONArray().put(w.startMs).put(w.text))
                    lines.put(JSONObject().put("t", l.timeMs).put("x", l.text).put("e", l.endMs).put("w", words))
                }
                j.put("lines", lines)
            }
            LyricsResult.Instrumental -> j.put("status", "instrumental")
            else -> j.put("status", "notfound")
        }
        return j.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodeLyrics(bytes: ByteArray): Pair<String, LyricsResult> {
        val j = JSONObject(String(bytes, Charsets.UTF_8))
        val key = j.optString("key")
        val result = when (j.optString("status")) {
            "found" -> {
                val arr = j.optJSONArray("lines") ?: JSONArray()
                val lines = (0 until arr.length()).map { i ->
                    val l = arr.getJSONObject(i)
                    val w = l.optJSONArray("w") ?: JSONArray()
                    LyricLine(
                        timeMs = l.optLong("t"),
                        text = l.optString("x"),
                        endMs = l.optLong("e", -1),
                        words = (0 until w.length()).map { k ->
                            val pair = w.getJSONArray(k)
                            LyricWord(pair.getLong(0), pair.getString(1))
                        },
                    )
                }
                LyricsResult.Found(Lyrics(lines, j.optBoolean("synced")))
            }
            "instrumental" -> LyricsResult.Instrumental
            else -> LyricsResult.NotFound
        }
        return key to result
    }
}
