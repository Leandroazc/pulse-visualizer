package app.pulse.visualizer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.visualizer.settings.Palettes

/** Cores do sistema no modo escuro, no espirito das listas agrupadas do iOS. */
object Ui {
    val Panel = Color(0xF21C1C1E)
    val Card = Color(0xFF2C2C2E)
    val Fill = Color(0x29787880)
    val FillSelected = Color(0xFF636366)
    val Separator = Color(0x29FFFFFF)
    val Label = Color.White
    val Secondary = Color(0x99EBEBF5)
    val Green = Color(0xFF30D158)
    val Glass = Color(0x24FFFFFF)
    val GlassBorder = Color(0x2EFFFFFF)
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 17.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Ui.Label,
    align: TextAlign? = null,
    maxLines: Int = 3,
) {
    Text(
        text, modifier, color = color, fontSize = size, fontWeight = weight,
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Linha com valor e botoes − / + (ajuste fino com alvos grandes, bom para o carro). */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Zerar",
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Label(label)
            Label(valueText, size = 14.sp, color = Ui.Secondary, maxLines = 1)
        }
        if (onReset != null) {
            Box(
                Modifier.height(44.dp).clip(CircleShape).tap(onReset).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Label(resetLabel, size = 15.sp, color = Ui.Secondary) }
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Ui.Fill), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 44.dp).tap(onMinus), contentAlignment = Alignment.Center) {
                Label("−", size = 24.sp, weight = FontWeight.Medium)
            }
            Box(Modifier.size(width = 1.dp, height = 22.dp).background(Ui.Separator))
            Box(Modifier.size(width = 56.dp, height = 44.dp).tap(onPlus), contentAlignment = Alignment.Center) {
                Label("+", size = 24.sp, weight = FontWeight.Medium)
            }
        }
    }
}

/** "No tempo", "Letra 0,3 s antes" ou "Letra 0,3 s depois". */
fun offsetText(ms: Int): String {
    if (ms == 0) return "No tempo"
    val s = String.format(java.util.Locale("pt", "BR"), "%.1f s", kotlin.math.abs(ms) / 1000f)
    return if (ms > 0) "Letra $s antes" else "Letra $s depois"
}

/** clickable sem a ondulacao do Material. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
        Label(
            title.uppercase(), Modifier.padding(start = 16.dp, bottom = 8.dp),
            size = 13.sp, color = Ui.Secondary,
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ui.Card),
            content = content,
        )
        if (footer != null) {
            Label(footer, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), size = 13.sp, color = Ui.Secondary)
        }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Ui.Separator))
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).tap { onChange(!checked) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, Modifier.weight(1f))
        PulseSwitch(checked)
    }
}

@Composable
fun PulseSwitch(checked: Boolean) {
    val track by animateColorAsState(if (checked) Ui.Green else Color(0xFF39393D), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.75f, stiffness = 500f), label = "thumb")
    Box(Modifier.size(width = 53.dp, height = 33.dp).clip(CircleShape).background(track)) {
        Box(Modifier.offset(x = x, y = 2.dp).size(29.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(label, Modifier.weight(1f))
            Label(valueText, color = Ui.Secondary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0xFF48484A),
            ),
        )
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    background: Color = Ui.Fill,
) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(12.dp)).background(background).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, text ->
            val bg by animateColorAsState(if (i == selected) Ui.FillSelected else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(bg).tap { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    text, size = 15.sp,
                    weight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun SegmentedRow(label: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (label != null) Label(label, Modifier.padding(bottom = 10.dp))
        Segmented(options, selected, onSelect, Modifier.fillMaxWidth())
    }
}

@Composable
fun PalettePicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Palettes.all.forEachIndexed { i, p ->
            Column(
                Modifier.width(76.dp).tap { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp)
                        .border(2.5.dp, if (i == selected) Color.White else Color.Transparent, CircleShape)
                        .padding(6.dp).clip(CircleShape)
                        .background(Brush.linearGradient(p.colors)),
                )
                Spacer(Modifier.height(6.dp))
                Label(
                    p.name, size = 12.sp, align = TextAlign.Center,
                    color = if (i == selected) Ui.Label else Ui.Secondary,
                )
            }
        }
    }
}

/** Medidor de nivel de uma fonte de audio: -60 dB a 0 dB. */
@Composable
fun LevelMeter(label: String, db: Float, available: Boolean, active: Boolean, accent: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, Modifier.width(112.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFF48484A))) {
            val frac = ((db + 60f) / 60f).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(if (active) accent else Ui.Secondary))
        }
        Label(
            when {
                !available -> "Indisponível"
                active -> "Em uso"
                else -> "—"
            },
            Modifier.width(112.dp), size = 14.sp, align = TextAlign.End,
            color = if (active) Ui.Green else Ui.Secondary,
        )
    }
}

@Composable
fun GlassPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(Ui.Glass)
            .border(1.dp, Ui.GlassBorder, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
