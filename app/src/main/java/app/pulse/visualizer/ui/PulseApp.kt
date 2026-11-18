package app.pulse.visualizer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import app.pulse.visualizer.lyrics.LyricsFetcher
import app.pulse.visualizer.lyrics.LyricsResult
import app.pulse.visualizer.media.ArtColors
import app.pulse.visualizer.media.ArtFetcher
import app.pulse.visualizer.media.NowPlaying
import app.pulse.visualizer.media.NowPlayingMonitor
import app.pulse.visualizer.link.PhoneLinkReceiver
import app.pulse.visualizer.settings.ArtMode
import app.pulse.visualizer.settings.LinkMode
import app.pulse.visualizer.settings.Palette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import app.pulse.visualizer.audio.AudioEngine
import app.pulse.visualizer.audio.FrameSmoother
import app.pulse.visualizer.audio.SourceKind
import app.pulse.visualizer.render.StyleSet
import app.pulse.visualizer.render.drawAmbient
import app.pulse.visualizer.settings.Palettes
import app.pulse.visualizer.settings.Settings
import app.pulse.visualizer.settings.SettingsStore
import app.pulse.visualizer.settings.StyleKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Desenha um estilo. [tick] e lido dentro do desenho para redesenhar a cada quadro. */
@Composable
fun VisualizerCanvas(
    kind: StyleKind,
    settings: Settings,
    smoother: FrameSmoother,
    styles: StyleSet,
    tick: () -> Long,
    modifier: Modifier = Modifier,
    palette: Palette = Palettes[settings.palette],
) {
    Canvas(modifier.fillMaxSize()) {
        tick()
        if (settings.ambient) drawAmbient(smoother, settings, palette)
        with(styles[kind]) { draw(smoother, settings, palette) }
    }
}

@Composable
fun PulseApp(
    store: SettingsStore,
    engine: AudioEngine,
    monitor: NowPlayingMonitor,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onMediaAccess: () -> Unit,
    /** Cada incremento abre/fecha os ajustes (botao do Android Auto). */
    settingsToggle: Int = 0,
    /** Area coberta por botoes do sistema (Android Auto): o fundo passa por baixo, o conteudo nao. */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** true quando o Bluetooth pode ser usado (permissao concedida). */
    bluetoothReady: Boolean = true,
    /** Chamado ao escolher Enviar ou a central: pede a permissao e liga o envio. */
    onLinkSend: () -> Unit = {},
) {
    val loaded by store.settings.collectAsState(initial = null)
    val s = loaded
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { f -> scope.launch { store.update(f) } }

    LifecycleStartEffect(s.source, hasPermission) {
        engine.start(s.source, hasPermission)
        onStopOrDispose { engine.stop() }
    }

    // Musica tocando: reinicia a cada onStart, entao pega o acesso recem-concedido nas configuracoes.
    LifecycleStartEffect(s.nowPlaying) {
        if (s.nowPlaying) monitor.start()
        onStopOrDispose { monitor.stop() }
    }
    // Na central: recebe a musica do Pulse do celular pelo Bluetooth.
    val appCtx = LocalContext.current.applicationContext
    val receiver = remember(s.linkMode, s.linkDevice) {
        if (s.linkMode == LinkMode.RECEIVE) PhoneLinkReceiver(appCtx, monitor, s.linkDevice) else null
    }
    DisposableEffect(receiver) {
        receiver?.start()
        onDispose { receiver?.stop() }
    }
    val linkStatus = receiver?.status?.collectAsState()?.value

    val playing by monitor.state.collectAsState()
    val mediaAccess by monitor.access.collectAsState()
    val isPlaying by monitor.playing.collectAsState()
    val track = if (s.nowPlaying) playing else null
    var art by remember { mutableStateOf<Bitmap?>(null) }
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(track, s.artMode, s.onlineArt) {
        // Mostra logo o que o player enviou; a busca online pode trocar depois.
        art = if (s.artMode == ArtMode.NONE) null else track?.art ?: art.takeIf { track != null }
        art = ArtFetcher.resolve(track, s.artMode, s.onlineArt, appContext)
    }
    val shownArt = art
    val artBg = s.artBackground && shownArt != null
    val artPalette = remember(shownArt) { shownArt?.let(ArtColors::palette) }

    // Letra: so busca com a pagina Letra aberta (nada sai do aparelho nos outros estilos).
    val lyricsMode = s.style == StyleKind.LYRICS
    var lyrics by remember { mutableStateOf<LyricsResult?>(null) }
    val latestTrack by rememberUpdatedState(track)
    // Letra que chega do celular (na central) faz a busca rodar de novo e achar no cache.
    val lyricsVersion by LyricsFetcher.version.collectAsState()
    var lyricsFor by remember { mutableStateOf("") }
    LaunchedEffect(track?.title, track?.artist, lyricsMode, lyricsVersion) {
        val t = latestTrack
        if (t == null) {
            lyrics = null
            return@LaunchedEffect
        }
        if (!lyricsMode) return@LaunchedEffect
        // So a versao mudou (mesma musica): mantem a letra na tela enquanto confere o cache.
        val forKey = "${t.title}|${t.artist}"
        if (forKey != lyricsFor || lyrics !is LyricsResult.Found) lyrics = LyricsResult.Loading
        lyricsFor = forKey
        delay(400) // a duracao da musica costuma chegar logo depois do titulo
        lyrics = LyricsFetcher.fetch(latestTrack ?: t)
    }

    val view = LocalView.current
    DisposableEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Laco de animacao: suaviza o ultimo quadro de audio a cada quadro de tela.
    val smoother = remember { FrameSmoother() }
    val styles = remember { StyleSet() }
    val tick = remember { mutableLongStateOf(0L) }
    val current by rememberUpdatedState(s)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                smoother.update(engine.latest, System.nanoTime(), dt, current.sensitivity, current.smoothness)
                tick.longValue = now
            }
        }
    }

    LaunchedEffect(s.autoRotateMin, s.style) {
        if (s.autoRotateMin > 0) {
            delay(s.autoRotateMin * 60_000L)
            // A troca automatica passa so pelos visualizadores; a letra fica ate o usuario sair dela.
            if (s.style != StyleKind.LYRICS) {
                store.update {
                    val n = nextStyle(it.style, 1)
                    it.copy(style = if (n == StyleKind.LYRICS) StyleKind.BARS else n)
                }
            }
        }
    }

    var controls by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(settingsToggle) { if (settingsToggle > 0) sheet = !sheet }
    LaunchedEffect(controls, touches) {
        if (controls) {
            delay(5000)
            controls = false
        }
    }

    var diag by remember { mutableStateOf(AudioDiag()) }
    LaunchedEffect(engine) {
        while (true) {
            diag = AudioDiag(engine.systemDb, engine.micDb, engine.systemOk, engine.micOk, engine.active)
            delay(120)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (sheet) sheet = false else controls = !controls
                }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (!sheet && abs(total) > size.width * 0.08f) {
                            val dir = if (total < 0) 1 else -1
                            update { it.copy(style = nextStyle(it.style, dir)) }
                        }
                    },
                ) { _, d -> total += d }
            },
    ) {
        val mediaActions = MediaActions(isPlaying, monitor::previous, monitor::playPause, monitor::next)
        // Sobre o fundo da capa, o visualizador usa tons claros tirados da propria imagem.
        val palette = (if (artBg) artPalette else null) ?: Palettes[s.palette]
        // O brilho ambiente cobre a tela inteira (inclusive sob os botoes do Android Auto),
        // em vez de ficar recortado na area do visualizador.
        if (s.ambient && !artBg) {
            Canvas(Modifier.fillMaxSize()) {
                tick.longValue
                drawAmbient(smoother, s, palette)
            }
        }
        NowPlayingScene(
            s, track, shownArt, contentPadding = contentPadding, controls = mediaActions, lyricsMode = lyricsMode,
        ) {
            val vs = s.copy(ambient = false)
            Crossfade(s.style, animationSpec = tween(450), label = "style") { kind ->
                if (kind == StyleKind.LYRICS) {
                    // Ajuste de sincronia: positivo adianta a letra em relacao a musica.
                    val offset = s.lyricsOffsetMs
                    LyricsPage(
                        lyrics, track != null, { monitor.positionMs() + offset }, track?.durationMs ?: 0L,
                        wordByWord = s.wordByWord,
                    )
                } else {
                    VisualizerCanvas(kind, vs, smoother, styles, { tick.longValue }, palette = palette)
                }
            }
        }

        if (s.nightDim) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        // Relogio no alto, fora da area reservada (no carro fica acima da faixa dos botoes, longe da capa).
        if (s.clock) {
            Clock(
                Modifier.align(Alignment.TopStart)
                    .padding(start = contentPadding.calculateStartPadding(LayoutDirection.Ltr) + 32.dp, top = 14.dp),
            )
        }

        Box(Modifier.fillMaxSize().padding(contentPadding)) {

        if (!hasPermission) PermissionCard(onRequestPermission, Modifier.align(Alignment.Center))

        AnimatedVisibility(
            visible = controls && !sheet && hasPermission,
            modifier = Modifier.align(Alignment.TopEnd).padding(28.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) { SourceChip(diag) }

        AnimatedVisibility(
            visible = controls && !sheet,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.8f)) { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            ControlsBar(
                style = s.style,
                onStyle = { k ->
                    touches++
                    update { it.copy(style = k) }
                },
                onSettings = { sheet = true },
            )
        }

        AnimatedVisibility(
            visible = sheet,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            SettingsSheet(
                s, diag, update, onClose = { sheet = false },
                mediaAccess = mediaAccess, onMediaAccess = onMediaAccess,
                linkStatus = linkStatus, bluetoothReady = bluetoothReady, onLinkSend = onLinkSend,
            )
        }
        }
    }
}

/**
 * Arruma a tela conforme a musica: com imagem, capa a esquerda e visualizador a direita
 * sobre o fundo feito da capa; sem imagem, visualizador em tela cheia com o titulo no canto.
 */
@Composable
fun NowPlayingScene(
    s: Settings,
    track: NowPlaying?,
    art: Bitmap?,
    animateBackground: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    controls: MediaActions? = null,
    /** Na letra, a coluna da musica aparece mesmo sem imagem (com uma capa neutra). */
    lyricsMode: Boolean = false,
    visualizer: @Composable () -> Unit,
) {
    if (art != null && s.artBackground) ArtBackground(art, animateBackground)
    val buttons = controls.takeIf { s.mediaControls }
    // Imagem na lateral inteira: passa por baixo das margens do sistema, ate a borda da tela.
    val sideArt = s.artFull && track != null && art != null
    if (sideArt) SideArt(art!!, Modifier.fillMaxHeight().fillMaxWidth(0.62f))
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        if (lyricsMode && s.artMode == ArtMode.NONE) {
            // Sem imagem escolhida, a letra ocupa a tela toda.
            Box(Modifier.fillMaxSize().padding(start = 56.dp, end = 32.dp)) { visualizer() }
        } else if (sideArt) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxHeight()) {
                    SideTrackInfo(
                        track!!, buttons,
                        Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 28.dp, end = 16.dp),
                    )
                }
                Box(Modifier.weight(0.58f).fillMaxHeight().padding(end = 24.dp)) { visualizer() }
            }
        } else if (track != null && (art != null || lyricsMode)) {
            Row(Modifier.fillMaxSize()) {
                NowPlayingPanel(track, art, Modifier.weight(0.42f).fillMaxHeight(), buttons)
                Box(Modifier.weight(0.58f).fillMaxHeight().padding(end = 24.dp)) { visualizer() }
            }
        } else {
            visualizer()
            if (track != null) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 28.dp).widthIn(max = 420.dp),
                ) {
                    TrackText(track)
                    if (buttons != null) MediaButtons(buttons, Modifier.padding(top = 10.dp).offset(x = (-14).dp))
                }
            }
        }
    }
}

private fun nextStyle(current: StyleKind, dir: Int): StyleKind {
    val all = StyleKind.entries
    return all[(current.ordinal + dir + all.size) % all.size]
}

@Composable
private fun ControlsBar(style: StyleKind, onStyle: (StyleKind) -> Unit, onSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassPill {
            Segmented(
                StyleKind.entries.map { it.label }, style.ordinal, { onStyle(StyleKind.entries[it]) },
                Modifier.width(480.dp).padding(5.dp), height = 56.dp, background = Color.Transparent,
            )
        }
        GlassPill(Modifier.height(66.dp).tap(onSettings)) {
            Label("Ajustes", Modifier.padding(horizontal = 28.dp), weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SourceChip(diag: AudioDiag) {
    val (text, dot) = when (diag.active) {
        SourceKind.SYSTEM -> "Áudio do sistema" to Ui.Green
        SourceKind.MIC -> "Microfone" to Ui.Green
        null -> "Sem áudio" to Ui.Secondary
    }
    GlassPill {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Label(text, size = 15.sp)
        }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val time by produceState("") {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            value = fmt.format(Date())
            delay(5000)
        }
    }
    Label(time, modifier, size = 46.sp, weight = FontWeight.Light, color = Color.White.copy(alpha = 0.82f))
}

@Composable
private fun PermissionCard(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Panel)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("Permita o acesso ao áudio", size = 24.sp, weight = FontWeight.Bold, align = TextAlign.Center)
        Label(
            "O Pulse usa a permissão de microfone para ler o som que está tocando e desenhar o visualizador. " +
                "Nada é gravado nem enviado.",
            color = Ui.Secondary, align = TextAlign.Center,
        )
        Box(
            Modifier.padding(top = 8.dp).height(60.dp).clip(CircleShape).background(Color.White).tap(onRequest)
                .padding(horizontal = 36.dp),
            contentAlignment = Alignment.Center,
        ) { Label("Permitir", color = Color.Black, weight = FontWeight.SemiBold) }
    }
}
