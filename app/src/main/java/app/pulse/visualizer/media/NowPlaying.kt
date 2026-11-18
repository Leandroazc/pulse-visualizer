package app.pulse.visualizer.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Servico vazio: existe so para o usuario poder conceder "acesso a notificacoes",
 * que e o que o Android exige para ler a musica que outro app esta tocando.
 */
class MediaListenerService : NotificationListenerService() {
    // O sistema religa este servico sozinho: aproveita para retomar o envio para a central.
    override fun onListenerConnected() {
        app.pulse.visualizer.link.PhoneLinkService.startIfEnabledAsync(applicationContext)
    }
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val album: String = "",
    val durationMs: Long = 0,
    /** Endereco da capa original informado pelo player (costuma ter mais resolucao que o bitmap). */
    val artUri: String = "",
    /** Foto do artista ja pronta (vinda do celular pela ligacao Bluetooth, onde ha internet). */
    val artistArt: Bitmap? = null,
)

/** Acompanha a sessao de midia ativa (Spotify, YouTube Music, player Bluetooth da central...). */
class NowPlayingMonitor(private val context: Context) {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private val _access = MutableStateFlow(false)
    /** true quando o acesso a notificacoes foi concedido. */
    val access: StateFlow<Boolean> = _access

    private val _playing = MutableStateFlow(false)
    /** true enquanto o player esta tocando (para o botao tocar/pausar). */
    val playing: StateFlow<Boolean> = _playing

    private var active: MediaController? = null

    // Ultima posicao informada pelo player; entre um aviso e outro a posicao e estimada pelo relogio.
    @Volatile private var basePos = 0L
    @Volatile private var baseTime = 0L
    @Volatile private var speed = 1f
    @Volatile private var advancing = false

    /** Posicao atual da musica em ms (para a letra sincronizada). */
    fun positionMs(): Long {
        if (!advancing) return basePos
        val elapsed = SystemClock.elapsedRealtime() - baseTime
        return basePos + (elapsed * speed).toLong()
    }

    /** Musica vinda do Pulse do celular pelo Bluetooth; enquanto existir, tem prioridade sobre a sessao local. */
    private class Remote(val track: NowPlaying, val playing: Boolean)
    @Volatile private var remote: Remote? = null

    /** Envia um comando (LinkProtocol.CMD_*) ao celular; definido enquanto a ligacao esta aberta. */
    @Volatile var remoteCommand: ((Int) -> Unit)? = null

    /**
     * Mostra a musica recebida do celular (chamar na thread principal). [track] nulo volta para a
     * sessao de midia local.
     */
    fun applyRemote(track: NowPlaying?, playing: Boolean, positionMs: Long, playbackSpeed: Float) {
        if (track == null) {
            if (remote != null) {
                remote = null
                _state.value = null
                publish()
            }
            return
        }
        remote = Remote(track, playing)
        basePos = positionMs.coerceAtLeast(0)
        baseTime = SystemClock.elapsedRealtime()
        speed = if (playbackSpeed > 0f) playbackSpeed else 1f
        advancing = playing
        _playing.value = playing
        if (_state.value != track) _state.value = track
    }

    /** Troca so os dados da musica recebida (capa que chegou depois), sem mexer na posicao. */
    fun updateRemoteTrack(track: NowPlaying) {
        val r = remote ?: return
        remote = Remote(track, r.playing)
        _state.value = track
    }

    private fun sendRemote(cmd: Int): Boolean {
        val send = remoteCommand ?: return false
        if (remote == null) return false
        send(cmd)
        return true
    }

    fun previous() {
        if (sendRemote(CMD_PREVIOUS)) return
        active?.transportControls?.skipToPrevious()
    }

    fun next() {
        if (sendRemote(CMD_NEXT)) return
        active?.transportControls?.skipToNext()
    }

    fun playPause() {
        if (sendRemote(CMD_PLAY_PAUSE)) {
            _playing.value = !_playing.value
            return
        }
        val c = active ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
        // Resposta imediata na tela; o player confirma logo depois.
        _playing.value = !_playing.value
    }

    private val manager =context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { bind(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
    }

    fun start() {
        stop()
        remote?.let {
            _state.value = it.track
            _playing.value = it.playing
        }
        _access.value = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!_access.value) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            bind(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            _access.value = false
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        active = null
        _state.value = null
        _playing.value = false
    }

    private fun bind(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list.filter { it.packageName != context.packageName }
        controllers.forEach { it.registerCallback(callback, handler) }
        publish()
    }

    private fun publish() {
        // A musica vinda do celular manda enquanto a ligacao estiver aberta.
        if (remote != null) return
        // Prefere quem esta tocando; senao, a sessao mais recente.
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        active = c
        val ps = c?.playbackState
        _playing.value = ps?.state == PlaybackState.STATE_PLAYING
        if (ps != null) {
            basePos = ps.position.coerceAtLeast(0)
            baseTime = ps.lastPositionUpdateTime.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
            speed = if (ps.playbackSpeed > 0f) ps.playbackSpeed else 1f
            advancing = ps.state == PlaybackState.STATE_PLAYING
        } else {
            basePos = 0
            advancing = false
        }
        val md = c?.metadata
        val title = md?.text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (md == null || title.isNullOrBlank()) {
            _state.value = null
            return
        }
        val artist = md.text(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        ).orEmpty()
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val next = NowPlaying(
            title.trim(), artist.trim(), art,
            album = md.text(MediaMetadata.METADATA_KEY_ALBUM).orEmpty().trim(),
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            artUri = md.text(
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            ).orEmpty(),
        )
        // Players reenviam os dados a todo momento (posicao, estado) com uma capa "nova" a cada vez.
        // Mesma musica: mantem o objeto anterior, senao a tela refaz a transicao da capa e pisca.
        val prev = _state.value
        if (prev != null && prev.title == next.title && prev.artist == next.artist) {
            val newArt = if (prev.art == null) art else prev.art
            // Duracao e album as vezes chegam depois; atualiza sem trocar a imagem ja mostrada.
            val merged = prev.copy(
                art = newArt,
                album = next.album.ifBlank { prev.album },
                artUri = next.artUri.ifBlank { prev.artUri },
                durationMs = if (next.durationMs > 0) next.durationMs else prev.durationMs,
            )
            if (merged != prev) _state.value = merged
            return
        }
        _state.value = next
    }

    private fun MediaMetadata.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> getText(k)?.toString()?.takeIf { it.isNotBlank() } }

    companion object {
        const val CMD_PREVIOUS = 1
        const val CMD_PLAY_PAUSE = 2
        const val CMD_NEXT = 3
    }
}
