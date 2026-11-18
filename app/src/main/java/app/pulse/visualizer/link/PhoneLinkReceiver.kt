package app.pulse.visualizer.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.pulse.visualizer.lyrics.LyricsFetcher
import app.pulse.visualizer.media.NowPlaying
import app.pulse.visualizer.media.NowPlayingMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors

/**
 * Na central: espera o Pulse do celular se conectar pelo Bluetooth e mostra o que ele envia.
 * Funciona sem internet e sem a sessao de midia do Android Auto (que o zLink deixa vazia).
 */
class PhoneLinkReceiver(
    private val context: Context,
    private val monitor: NowPlayingMonitor,
    /** Endereco Bluetooth do celular escolhido na lista de pareados da central. */
    private val phoneAddress: String,
) {
    enum class Status { WAITING, CONNECTED, NO_BLUETOOTH, NO_DEVICE }

    private val _status = MutableStateFlow(Status.WAITING)
    val status: StateFlow<Status> = _status

    @Volatile private var running = false
    @Volatile private var client: BluetoothSocket? = null
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())
    // Comandos saem um de cada vez, fora da thread principal.
    private val commands = Executors.newSingleThreadExecutor()

    fun start() {
        if (running) return
        running = true
        thread = Thread(::listen, "PulseLink").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { client?.close() }
        thread?.interrupt()
        thread = null
        commands.shutdownNow()
        main.post {
            monitor.remoteCommand = null
            monitor.applyRemote(null, false, 0, 1f)
        }
    }

    /** Liga para o Pulse do celular; sem resposta (celular longe, Pulse parado), tenta de novo. */
    @SuppressLint("MissingPermission")
    private fun listen() {
        if (phoneAddress.isBlank()) {
            _status.value = Status.NO_DEVICE
            return
        }
        while (running) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                _status.value = Status.NO_BLUETOOTH
                if (!pause(5_000)) return
                continue
            }
            _status.value = Status.WAITING
            val socket = runCatching {
                adapter.getRemoteDevice(phoneAddress).createInsecureRfcommSocketToServiceRecord(LinkProtocol.SERVICE_UUID)
            }.getOrNull()
            if (socket != null) {
                client = socket
                val connected = runCatching { socket.connect() }.isSuccess
                if (connected && running) {
                    handle(socket)
                } else {
                    runCatching { socket.close() }
                    client = null
                }
            }
            if (!pause(4_000)) return
        }
    }

    private fun pause(ms: Long): Boolean = try {
        Thread.sleep(ms)
        running
    } catch (_: InterruptedException) {
        false
    }

    private fun handle(socket: BluetoothSocket) {
        client = socket
        _status.value = Status.CONNECTED
        val out = DataOutputStream(BufferedOutputStream(socket.outputStream))
        val input = DataInputStream(BufferedInputStream(socket.inputStream))
        main.post {
            monitor.remoteCommand = { cmd ->
                runCatching { commands.execute { runCatching {
                    synchronized(out) {
                        out.write(LinkProtocol.encodeCommand(cmd))
                        out.flush()
                    }
                } } }
            }
        }
        var key = ""
        var track: NowPlaying? = null
        try {
            while (running) {
                val (type, bytes) = LinkProtocol.read(input)
                when (type) {
                    LinkProtocol.META -> {
                        val m = LinkProtocol.decodeMeta(bytes)
                        if (m.title.isBlank()) {
                            key = ""
                            track = null
                            main.post { monitor.applyRemote(null, false, 0, 1f) }
                        } else {
                            val k = LinkProtocol.trackKey(m.title, m.artist)
                            val same = track?.takeIf { k == key }
                            val next = NowPlaying(
                                m.title, m.artist, same?.art,
                                album = m.album, durationMs = m.durationMs, artistArt = same?.artistArt,
                            )
                            // Mesmo objeto quando nada mudou: a tela nao refaz a transicao da capa.
                            val shown = if (same != null && same == next) same else next
                            key = k
                            track = shown
                            main.post { monitor.applyRemote(shown, m.playing, m.positionMs, m.speed) }
                        }
                    }
                    LinkProtocol.ART, LinkProtocol.ARTIST_ART -> {
                        val (k, bmp) = LinkProtocol.decodeImage(bytes)
                        val t = track
                        if (k == key && t != null && bmp != null) {
                            val next = if (type == LinkProtocol.ART) t.copy(art = bmp) else t.copy(artistArt = bmp)
                            track = next
                            main.post { monitor.updateRemoteTrack(next) }
                        }
                    }
                    LinkProtocol.LYRICS -> {
                        val (k, result) = LinkProtocol.decodeLyrics(bytes)
                        val t = track
                        if (k == key && t != null) main.post { LyricsFetcher.put(t, result) }
                    }
                }
            }
        } catch (_: Exception) {
            // Celular desconectou (ou saiu do alcance): volta a esperar.
        } finally {
            runCatching { socket.close() }
            client = null
            if (running) _status.value = Status.WAITING
            main.post {
                monitor.remoteCommand = null
                monitor.applyRemote(null, false, 0, 1f)
            }
        }
    }
}
