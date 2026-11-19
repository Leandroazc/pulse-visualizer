package app.pulse.visualizer.lyrics

import app.pulse.visualizer.link.LinkProtocol
import java.io.File
import java.security.MessageDigest

/**
 * Letras ja baixadas guardadas no aparelho: tocar de novo a mesma musica nao depende da internet
 * (no carro o sinal cai) e a central recebe a letra do celular mais rapido.
 *
 * Fica na pasta de cache do app, com teto de [maxBytes]: passando dele, apaga as menos usadas ate
 * [targetBytes]. O Android tambem pode limpar essa pasta se faltar espaco. So guarda letras
 * encontradas ou "instrumental"; "nao encontrada" pode aparecer no LRCLIB depois.
 */
class LyricsDiskCache(
    private val dir: File,
    private val maxBytes: Long = 10L * 1024 * 1024,
    private val targetBytes: Long = 8L * 1024 * 1024,
) {
    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    @Synchronized
    fun get(key: String): LyricsResult? {
        val f = file(key)
        if (!f.isFile) return null
        val (stored, result) = runCatching { LinkProtocol.decodeLyrics(f.readBytes()) }.getOrNull() ?: run {
            f.delete()
            return null
        }
        if (stored != key) return null
        // Marca como usada agora: as que ficam mais tempo sem tocar sao apagadas primeiro.
        f.setLastModified(System.currentTimeMillis())
        return result
    }

    @Synchronized
    fun put(key: String, result: LyricsResult) {
        if (result !is LyricsResult.Found && result != LyricsResult.Instrumental) return
        runCatching {
            dir.mkdirs()
            val f = file(key)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeBytes(LinkProtocol.encodeLyrics(key, result))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
            trim()
        }
    }

    /** Tamanho ocupado hoje, em bytes. */
    @Synchronized
    fun size(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= targetBytes) break
            total -= f.length()
            f.delete()
        }
    }
}
