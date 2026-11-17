package app.pulse.visualizer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import app.pulse.visualizer.lyrics.LyricLine
import app.pulse.visualizer.lyrics.Lyrics
import app.pulse.visualizer.lyrics.LyricsResult
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Quanto antes a linha acende (o olho le um pouco antes da voz, como no Apple Music). */
private const val LEAD_MS = 250L

/**
 * Pagina da letra: linhas grandes alinhadas a esquerda, a atual em branco e as outras
 * esmaecidas, rolando suave para manter a linha cantada no terco de cima.
 */
@Composable
fun LyricsPage(
    result: LyricsResult?,
    hasTrack: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    wordByWord: Boolean = true,
) {
    Box(modifier.fillMaxSize()) {
        val key = when (result) {
            is LyricsResult.Found -> result.lyrics
            else -> result
        }
        Crossfade(if (hasTrack) key else null, animationSpec = tween(400), label = "lyrics") { k ->
            when {
                k is Lyrics -> LyricsLines(k, positionMs, durationMs, live, wordByWord)
                !hasTrack -> Message("Nenhuma música tocando")
                k == LyricsResult.Instrumental -> Message("Instrumental")
                k == LyricsResult.NotFound -> Message("Letra não encontrada para esta música")
                else -> Message("Buscando letra…")
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Label(
            text, Modifier.padding(start = 8.dp, end = 24.dp),
            size = 30.sp, weight = FontWeight.Bold, color = Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, positionMs: () -> Long, durationMs: Long, live: Boolean, wordByWord: Boolean) {
    val pos by rememberUpdatedState(positionMs)
    var current by remember(lyrics) { mutableIntStateOf(lyrics.indexAt(pos() + LEAD_MS, durationMs)) }
    if (live) {
        LaunchedEffect(lyrics, durationMs) {
            while (true) {
                current = lyrics.indexAt(pos() + LEAD_MS, durationMs)
                delay(60)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val topPad = h * 0.30f
        val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
        LaunchedEffect(current) {
            if (current >= 0) list.animateScrollToItem(current)
        }
        val fadePx = with(LocalDensity.current) { 56.dp.toPx() }

        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = topPad, bottom = h * 0.62f, end = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                // Bordas de cima e de baixo somem suavemente.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val f = (fadePx / size.height).coerceIn(0f, 0.4f)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val distance = if (current < 0) i + 1 else i - current
                if (distance == 0 && wordByWord && line.words.isNotEmpty()) {
                    val nextStart = lyrics.lines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE
                    KaraokeRow(line, nextStart, { pos() + WORD_LEAD_MS }, live)
                } else {
                    LyricRow(line.text, distance, synced = lyrics.synced)
                }
            }
        }
    }
}

/** Atraso menor para as palavras: o preenchimento acompanha a voz, so um pouco a frente. */
private const val WORD_LEAD_MS = 120L

/** Mesmo tamanho e espacamento das outras linhas, para nao pular quando a linha vira atual. */
private val LineSize = 34.sp
private val LinePadding = 12.dp

/**
 * Linha atual com tempo por palavra: o texto aparece apagado e cada palavra vai se
 * preenchendo de branco da esquerda para a direita enquanto e cantada (como no Apple Music).
 */
@Composable
private fun KaraokeRow(line: LyricLine, nextStart: Long, positionMs: () -> Long, live: Boolean) {
    val pos by rememberUpdatedState(positionMs)
    var now by remember { mutableLongStateOf(pos()) }
    if (live) {
        LaunchedEffect(line) {
            while (true) withFrameMillis { now = pos() }
        }
    }
    // Inicio e fim de cada palavra dentro do texto da linha.
    val ranges = remember(line) {
        var at = 0
        line.words.map { w -> (at until at + w.text.length).also { at += w.text.length } }
    }
    val lineEnd = if (line.endMs > 0) line.endMs else nextStart
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        line.text,
        color = Color.White.copy(alpha = 0.32f),
        fontSize = LineSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LinePadding)
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                val t = now
                line.words.forEachIndexed { i, w ->
                    if (t < w.startMs) return@forEachIndexed
                    val r = ranges[i]
                    val trimmed = r.first until (r.first + w.text.trimEnd().length).coerceAtLeast(r.first + 1)
                    val end = line.words.getOrNull(i + 1)?.startMs ?: lineEnd
                    val dur = (end - w.startMs).coerceIn(80, 1500)
                    val p = ((t - w.startMs).toFloat() / dur).coerceIn(0f, 1f)
                    val path = l.getPathForRange(trimmed.first, trimmed.last + 1)
                    val box = path.getBounds()
                    clipPath(path) {
                        if (p >= 1f) {
                            drawText(l, color = Color.White)
                        } else {
                            // Borda suave do preenchimento, avancando pela palavra.
                            val edge = box.left + box.width * p
                            val soft = 14.dp.toPx()
                            clipRect(right = edge + soft) {
                                drawWithLayer {
                                    drawText(l, color = Color.White)
                                    drawRect(
                                        Brush.horizontalGradient(
                                            0f to Color.Black, 1f to Color.Transparent,
                                            startX = edge - soft, endX = edge + soft,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                        }
                    }
                }
            },
    )
}

/** Desenha [block] numa camada propria (para a mascara DstIn so afetar o que esta dentro). */
private inline fun DrawScope.drawWithLayer(block: DrawScope.() -> Unit) {
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    block()
    drawContext.canvas.restore()
}

@Composable
private fun LyricRow(text: String, distance: Int, synced: Boolean) {
    val active = distance == 0
    // Atual em branco; as demais vao esmaecendo com a distancia, as ja cantadas um pouco mais.
    val targetAlpha = when {
        !synced -> 0.85f
        active -> 1f
        distance < 0 -> 0.30f
        else -> (0.46f - 0.06f * (distance - 1)).coerceAtLeast(0.2f)
    }
    val alpha by animateFloatAsState(targetAlpha, tween(450, easing = FastOutSlowInEasing), label = "a")
    val scale by animateFloatAsState(if (active || !synced) 1f else 0.94f, spring(dampingRatio = 0.8f, stiffness = 180f), label = "s")
    val gap = text.isBlank()
    Label(
        if (gap) "• • •" else text,
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (abs(distance) <= 0) 12.dp else 10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        size = if (gap) 26.sp else 34.sp,
        weight = FontWeight.Bold,
        color = Color.White,
        align = TextAlign.Start,
        maxLines = 4,
    )
}
