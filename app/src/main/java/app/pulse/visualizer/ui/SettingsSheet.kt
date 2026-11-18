package app.pulse.visualizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.pulse.visualizer.audio.SourceKind
import app.pulse.visualizer.link.BluetoothDevices
import app.pulse.visualizer.link.PhoneLinkReceiver
import app.pulse.visualizer.settings.ArtMode
import app.pulse.visualizer.settings.LinkMode
import app.pulse.visualizer.settings.LYRICS_OFFSET_MAX
import app.pulse.visualizer.settings.Palettes
import app.pulse.visualizer.settings.Settings
import app.pulse.visualizer.settings.SourceMode
import app.pulse.visualizer.settings.StyleKind
import kotlin.math.roundToInt

/** Estado das fontes de audio para o diagnostico. */
data class AudioDiag(
    val systemDb: Float = -120f,
    val micDb: Float = -120f,
    val systemOk: Boolean = false,
    val micOk: Boolean = false,
    val active: SourceKind? = null,
)

/** Ligacao Bluetooth com o outro Pulse: Enviar (celular) ou Receber (central multimidia). */
@Composable
private fun LinkSection(
    s: Settings,
    update: ((Settings) -> Settings) -> Unit,
    status: PhoneLinkReceiver.Status?,
    bluetoothReady: Boolean,
    onLinkSend: () -> Unit,
    accent: Color,
) {
    val footer = when (s.linkMode) {
        LinkMode.OFF -> "No celular, escolha Enviar; na central, Receber. O celular manda a música, a capa e a " +
            "letra pelo Bluetooth, e a central mostra tudo sem precisar de internet."
        LinkMode.SEND -> "Na central, escolha Receber e toque neste celular na lista. O envio continua com o " +
            "Pulse fechado, enquanto houver música tocando."
        LinkMode.RECEIVE -> "Escolha o celular na lista (pareado com a central). Deixe o Pulse aberto na central: " +
            "ele recebe a música do Pulse do celular, e os botões voltar, pausar e pular controlam o celular."
    }
    Section("Central multimídia", footer = footer) {
        SegmentedRow(null, LinkMode.entries.map { it.label }, s.linkMode.ordinal) { i ->
            val mode = LinkMode.entries[i]
            update { it.copy(linkMode = mode) }
            if (mode == LinkMode.SEND) onLinkSend()
        }
        when (s.linkMode) {
            LinkMode.SEND -> {
                if (!bluetoothReady) {
                    RowDivider()
                    Box(
                        Modifier.fillMaxWidth().height(60.dp).tap(onLinkSend).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Label("Permitir o uso do Bluetooth…", color = accent, weight = FontWeight.Medium) }
                }
            }
            LinkMode.RECEIVE -> {
                RowDivider()
                val (text, dot) = when (status) {
                    PhoneLinkReceiver.Status.CONNECTED -> "Conectado ao celular" to Ui.Green
                    PhoneLinkReceiver.Status.NO_BLUETOOTH -> "Bluetooth desligado" to Ui.Secondary
                    PhoneLinkReceiver.Status.NO_DEVICE -> "Escolha o celular abaixo" to Ui.Secondary
                    else -> "Procurando o celular…" to Ui.Secondary
                }
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.padding(end = 12.dp).width(9.dp).height(9.dp).clip(CircleShape).background(dot))
                    Label(text)
                }
                RowDivider()
                run {
                    val context = LocalContext.current
                    val devices = remember(bluetoothReady) { BluetoothDevices.bonded(context) }
                    if (devices.isEmpty()) {
                        Label(
                            "Nenhum aparelho pareado. Pareie o celular nas configurações de Bluetooth da central.",
                            Modifier.padding(16.dp), color = Ui.Secondary, size = 15.sp,
                        )
                    }
                    devices.forEachIndexed { i, (name, address) ->
                        if (i > 0) RowDivider()
                        val chosen = address == s.linkDevice
                        Row(
                            Modifier.fillMaxWidth().height(60.dp)
                                .tap { update { it.copy(linkDevice = address) } }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Label(name, Modifier.weight(1f), maxLines = 1)
                            if (chosen) Label("✓", color = accent, weight = FontWeight.Bold)
                        }
                    }
                }
            }
            LinkMode.OFF -> Unit
        }
    }
}

@Composable
fun SettingsSheet(
    s: Settings,
    diag: AudioDiag,
    update: ((Settings) -> Settings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    mediaAccess: Boolean = true,
    onMediaAccess: () -> Unit = {},
    linkStatus: PhoneLinkReceiver.Status? = null,
    bluetoothReady: Boolean = true,
    onLinkSend: () -> Unit = {},
) {
    val accent = Palettes[s.palette].colorAt(0.5f)
    Column(
        modifier
            .padding(12.dp)
            .width(460.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Panel)
            // Toques no painel nao devem chegar ao visualizador atras dele.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Ajustes", Modifier.weight(1f), size = 30.sp, weight = FontWeight.Bold)
            Box(
                Modifier.height(48.dp).clip(CircleShape).background(Ui.Fill).tap(onClose).padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) { Label("OK", weight = FontWeight.SemiBold) }
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section("Estilo") {
                SegmentedRow(null, StyleKind.entries.map { it.label }, s.style.ordinal) { i ->
                    update { it.copy(style = StyleKind.entries[i]) }
                }
                RowDivider()
                SegmentedRow(
                    "Trocar automaticamente",
                    Settings.ROTATE_OPTIONS.map { if (it == 0) "Nunca" else "$it min" },
                    Settings.ROTATE_OPTIONS.indexOf(s.autoRotateMin).coerceAtLeast(0),
                ) { i -> update { it.copy(autoRotateMin = Settings.ROTATE_OPTIONS[i]) } }
            }

            Section(
                "Música",
                footer = if (mediaAccess) {
                    "A foto do artista (e a capa, quando o player não a envia) é buscada na Deezer usando " +
                        "apenas o nome do artista e o título da música."
                } else {
                    "Para ler o título, o artista e a capa da música que outro app está tocando, " +
                        "o Android exige o acesso às notificações. O Pulse não lê nem guarda as suas notificações."
                },
            ) {
                ToggleRow("Mostrar música e artista", s.nowPlaying) { v -> update { it.copy(nowPlaying = v) } }
                if (s.nowPlaying && !mediaAccess) {
                    RowDivider()
                    Box(
                        Modifier.fillMaxWidth().height(60.dp).tap(onMediaAccess).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Label("Permitir acesso às notificações…", color = accent, weight = FontWeight.Medium) }
                }
                RowDivider()
                SegmentedRow("Imagem", ArtMode.entries.map { it.label }, s.artMode.ordinal) { i ->
                    update { it.copy(artMode = ArtMode.entries[i]) }
                }
                if (s.artMode != ArtMode.NONE) {
                    RowDivider()
                    ToggleRow("Imagem ocupando a lateral", s.artFull) { v -> update { it.copy(artFull = v) } }
                }
                RowDivider()
                ToggleRow("Botões voltar, pausar e pular", s.mediaControls) { v -> update { it.copy(mediaControls = v) } }
                RowDivider()
                ToggleRow("Fundo com as cores da imagem", s.artBackground) { v -> update { it.copy(artBackground = v) } }
                RowDivider()
                ToggleRow("Buscar imagens na internet", s.onlineArt) { v -> update { it.copy(onlineArt = v) } }
            }

            LinkSection(s, update, linkStatus, bluetoothReady, onLinkSend, accent)

            Section(
                "Letra",
                footer = "Se a letra aparecer antes da voz, toque em −; se aparecer depois, em +. " +
                    "As letras vêm do LRCLIB; nem toda música tem tempo por palavra.",
            ) {
                ToggleRow("Destaque palavra por palavra", s.wordByWord) { v -> update { it.copy(wordByWord = v) } }
                RowDivider()
                StepperRow(
                    "Sincronia",
                    offsetText(s.lyricsOffsetMs),
                    onMinus = { update { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs - 100).coerceAtLeast(-LYRICS_OFFSET_MAX)) } },
                    onPlus = { update { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + 100).coerceAtMost(LYRICS_OFFSET_MAX)) } },
                    onReset = if (s.lyricsOffsetMs != 0) ({ update { it.copy(lyricsOffsetMs = 0) } }) else null,
                )
            }

            Section("Cores") {
                PalettePicker(s.palette) { i -> update { it.copy(palette = i) } }
                RowDivider()
                ToggleRow("Cores por frequência", s.spectrumColor) { v -> update { it.copy(spectrumColor = v) } }
                RowDivider()
                ToggleRow("Brilho de fundo", s.ambient) { v -> update { it.copy(ambient = v) } }
            }

            Section("Movimento") {
                SliderRow("Sensibilidade", s.sensitivity, 0.5f..2f, "${(s.sensitivity * 100).roundToInt()}%", accent) { v ->
                    update { it.copy(sensitivity = v) }
                }
                RowDivider()
                SliderRow("Suavidade", s.smoothness, 0f..1f, "${(s.smoothness * 100).roundToInt()}%", accent) { v ->
                    update { it.copy(smoothness = v) }
                }
                RowDivider()
                SliderRow("Pulso na batida", s.pulse, 0f..1f, "${(s.pulse * 100).roundToInt()}%", accent) { v ->
                    update { it.copy(pulse = v) }
                }
            }

            Section("Forma", footer = "Picos e reflexo valem para o estilo Barras.") {
                SliderRow("Quantidade de barras", s.barCount.toFloat(), 16f..64f, "${s.barCount}", accent) { v ->
                    update { it.copy(barCount = v.roundToInt()) }
                }
                RowDivider()
                SliderRow("Espessura", s.thickness, 0.3f..0.9f, "${(s.thickness * 100).roundToInt()}%", accent) { v ->
                    update { it.copy(thickness = v) }
                }
                RowDivider()
                ToggleRow("Espelhar", s.mirror) { v -> update { it.copy(mirror = v) } }
                RowDivider()
                ToggleRow("Picos", s.peaks) { v -> update { it.copy(peaks = v) } }
                RowDivider()
                ToggleRow("Reflexo", s.reflection) { v -> update { it.copy(reflection = v) } }
            }

            Section("Tela") {
                ToggleRow("Relógio", s.clock) { v -> update { it.copy(clock = v) } }
                RowDivider()
                ToggleRow("Modo noturno", s.nightDim) { v -> update { it.copy(nightDim = v) } }
                RowDivider()
                ToggleRow("Manter a tela ligada", s.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            }

            Section(
                "Fonte de áudio",
                footer = "No modo Automático, o áudio do sistema é usado quando há sinal nele; " +
                    "caso contrário (comum com Bluetooth), o microfone assume. Nada é gravado.",
            ) {
                SegmentedRow(null, SourceMode.entries.map { it.label }, s.source.ordinal) { i ->
                    update { it.copy(source = SourceMode.entries[i]) }
                }
                RowDivider()
                LevelMeter("Sistema", diag.systemDb, diag.systemOk, diag.active == SourceKind.SYSTEM, accent)
                RowDivider()
                LevelMeter("Microfone", diag.micDb, diag.micOk, diag.active == SourceKind.MIC, accent)
            }
        }
    }
}
