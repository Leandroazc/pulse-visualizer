package app.pulse.visualizer.link

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pulse.visualizer.lyrics.LyricsFetcher
import app.pulse.visualizer.media.ArtFetcher
import app.pulse.visualizer.media.NowPlaying
import app.pulse.visualizer.media.NowPlayingMonitor
import app.pulse.visualizer.settings.ArtMode
import app.pulse.visualizer.settings.LinkMode
import app.pulse.visualizer.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Aparelhos Bluetooth pareados (nome, endereco), para escolher a central. */
object BluetoothDevices {
    fun canConnect(context: Context) =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, "android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun bonded(context: Context): List<Pair<String, String>> {
        if (!canConnect(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .map { (it.name ?: it.address) to it.address }
                .sortedBy { it.first.lowercase() }
        }.getOrDefault(emptyList())
    }
}

/**
 * No celular: mantem a ligacao com o Pulse da central e envia a musica que esta tocando,
 * a capa, a foto do artista e a letra (buscadas aqui, onde ha internet).
 */
class PhoneLinkSender(
    private val context: Context,
    private val monitor: NowPlayingMonitor,
    private val online: Boolean,
) {
    /**
     * Espera a central se conectar. E ela que liga para o celular: a central costuma manter o
     * pareamento com o celular mesmo quando o celular "esquece" a central.
     */
    @SuppressLint("MissingPermission")
    suspend fun run() = withContext(Dispatchers.IO) {
        while (isActive) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                delay(5_000)
                continue
            }
            val server = runCatching {
                adapter.listenUsingInsecureRfcommWithServiceRecord(LinkProtocol.SERVICE_NAME, LinkProtocol.SERVICE_UUID)
            }.getOrNull()
            if (server == null) {
                delay(5_000)
                continue
            }
            try {
                val socket = acceptCancellable(server)
                runCatching { server.close() }
                runCatching { session(socket) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(3_000)
            } finally {
                runCatching { server.close() }
            }
        }
    }

    /** accept() bloqueia; fechar o servidor e o unico jeito de soltar quando o servico para. */
    private suspend fun acceptCancellable(server: BluetoothServerSocket): BluetoothSocket = coroutineScope {
        val closer = launch {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                runCatching { server.close() }
            }
        }
        try {
            runInterruptible { server.accept() }
        } finally {
            closer.cancel()
        }
    }

    private suspend fun session(socket: BluetoothSocket) = coroutineScope {
        try {
            val out = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val input = DataInputStream(BufferedInputStream(socket.inputStream))

            // Botoes tocados na central: uma letra por botao; qualquer outro byte e ignorado.
            val reader = launch {
                while (isActive) {
                    val b = runInterruptible { input.read() }
                    if (b < 0) throw java.io.EOFException()
                    val cmd = LinkProtocol.decodeCommand(b) ?: continue
                    withContext(Dispatchers.Main) {
                        when (cmd) {
                            NowPlayingMonitor.CMD_PREVIOUS -> monitor.previous()
                            NowPlayingMonitor.CMD_PLAY_PAUSE -> monitor.playPause()
                            NowPlayingMonitor.CMD_NEXT -> monitor.next()
                        }
                    }
                }
            }

            var lastKey: String? = null
            var lastPlaying = false
            var lastDuration = 0L
            var lastSent = 0L
            var extras: Job? = null
            while (isActive && reader.isActive) {
                val track = monitor.state.value
                val playing = monitor.playing.value
                val key = track?.let { LinkProtocol.trackKey(it.title, it.artist) } ?: ""
                val now = SystemClock.elapsedRealtime()
                val duration = track?.durationMs ?: 0L
                // Posicao a cada 3 s mantem a letra da central em sincronia.
                if (key != lastKey || playing != lastPlaying || duration != lastDuration || now - lastSent > 3_000) {
                    val meta = LinkProtocol.Meta(
                        title = track?.title.orEmpty(),
                        artist = track?.artist.orEmpty(),
                        album = track?.album.orEmpty(),
                        durationMs = duration,
                        positionMs = monitor.positionMs(),
                        playing = playing,
                    )
                    LinkProtocol.write(out, LinkProtocol.META, LinkProtocol.encodeMeta(meta))
                    if (key != lastKey) {
                        extras?.cancel()
                        if (track != null) extras = launch { sendExtras(out, key) }
                    }
                    lastKey = key
                    lastPlaying = playing
                    lastDuration = duration
                    lastSent = now
                }
                delay(400)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Capa, foto do artista e letra da musica [key]; chegam um pouco depois do titulo. */
    private suspend fun sendExtras(out: DataOutputStream, key: String) {
        delay(700) // o player costuma mandar a capa e a duracao logo depois do titulo
        fun current(): NowPlaying? =
            monitor.state.value?.takeIf { LinkProtocol.trackKey(it.title, it.artist) == key }
        val track = current() ?: return
        ArtFetcher.resolve(track, ArtMode.ALBUM, online, context)?.let {
            LinkProtocol.write(out, LinkProtocol.ART, LinkProtocol.encodeImage(key, it))
        }
        if (online) {
            ArtFetcher.artistPhoto(track.artist)?.let {
                LinkProtocol.write(out, LinkProtocol.ARTIST_ART, LinkProtocol.encodeImage(key, it))
            }
            val lyrics = LyricsFetcher.fetch(current() ?: track)
            LinkProtocol.write(out, LinkProtocol.LYRICS, LinkProtocol.encodeLyrics(key, lyrics))
        }
    }
}

/**
 * Servico em primeiro plano no celular: segura a ligacao com a central mesmo com o Pulse fechado
 * (no carro o Pulse do celular fica em segundo plano, e a Samsung congela apps parados).
 */
class PhoneLinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val monitor by lazy { NowPlayingMonitor(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BluetoothDevices.canConnect(this) || !goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                SettingsStore(applicationContext).settings
                    .map { it.linkMode to it.onlineArt }
                    .distinctUntilChanged()
                    .collectLatest { (mode, online) ->
                        if (mode != LinkMode.SEND) {
                            stopSelf()
                            return@collectLatest
                        }
                        monitor.start()
                        try {
                            PhoneLinkSender(applicationContext, monitor, online).run()
                        } finally {
                            monitor.stop()
                        }
                    }
            }
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean = runCatching {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Envio para a central", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Pulse")
            .setContentText("Pronto para enviar a música à central")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }.isSuccess

    override fun onDestroy() {
        scope.cancel()
        monitor.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "link"
        private const val NOTIFICATION_ID = 7

        /** Liga o envio se ele estiver escolhido nos ajustes (ignora se o Android nao deixar agora). */
        fun startIfEnabled(context: Context) {
            val app = context.applicationContext
            val s = runCatching { runBlocking { SettingsStore(app).settings.first() } }.getOrNull() ?: return
            if (s.linkMode != LinkMode.SEND || !BluetoothDevices.canConnect(app)) return
            runCatching { ContextCompat.startForegroundService(app, Intent(app, PhoneLinkService::class.java)) }
        }

        /** Igual a [startIfEnabled], fora da thread principal (le os ajustes do disco). */
        fun startIfEnabledAsync(context: Context) {
            val app = context.applicationContext
            Thread { startIfEnabled(app) }.start()
        }
    }
}

/** Retoma o envio depois de reiniciar o celular ou atualizar o Pulse. */
class LinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                PhoneLinkService.startIfEnabled(context)
            } finally {
                result.finish()
            }
        }.start()
    }
}
