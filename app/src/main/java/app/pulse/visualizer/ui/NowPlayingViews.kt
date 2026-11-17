package app.pulse.visualizer.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.pulse.visualizer.media.NowPlaying

/**
 * Fundo feito da propria imagem: reduzida a poucos pixels e esticada, vira manchas
 * de cor suaves que giram bem devagar. Funciona em qualquer versao do Android;
 * no 12+ ganha um desfoque extra.
 */
@Composable
fun ArtBackground(art: Bitmap, animate: Boolean = true) {
    Crossfade(art, animationSpec = tween(900), label = "artBg") { bmp ->
        val small = remember(bmp) { Bitmap.createScaledBitmap(bmp, 16, 16, true).asImageBitmap() }
        val rotation = if (animate) {
            val t = rememberInfiniteTransition(label = "artBgSpin")
            val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(150_000, easing = LinearEasing)), label = "spin")
            r
        } else 25f
        Box(Modifier.fillMaxSize()) {
            Image(
                small, contentDescription = null,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = 2.2f
                        scaleY = 2.2f
                    }
                    .blur(40.dp),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        }
    }
}

/**
 * Imagem ocupando a lateral inteira e se dissolvendo no fundo para a direita (estilo Spicy Lyrics).
 * Escurece um pouco embaixo para o titulo ficar legivel por cima.
 */
@Composable
fun SideArt(art: Bitmap, modifier: Modifier = Modifier) {
    Crossfade(art, modifier, animationSpec = tween(700), label = "sideArt") { bmp ->
        Image(
            remember(bmp) { bmp.asImageBitmap() }, contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)))
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentScale = ContentScale.Crop,
        )
    }
}

/** Titulo, artista e botoes no canto de baixo, por cima da imagem lateral. */
@Composable
fun SideTrackInfo(track: NowPlaying, controls: MediaActions?, modifier: Modifier = Modifier) {
    Column(modifier) {
        TrackText(track)
        if (controls != null) MediaButtons(controls, Modifier.padding(top = 10.dp).offset(x = (-14).dp))
    }
}

/** Estado e acoes dos botoes de musica. */
class MediaActions(
    val playing: Boolean,
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
)

/** Voltar / tocar-pausar / pular, so com os simbolos, como no Apple Music. Alvos grandes para o carro. */
@Composable
fun MediaButtons(a: MediaActions, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaButton(a.onPrevious, 64.dp, 26.dp) { drawSkip(forward = false) }
        MediaButton(a.onPlayPause, 72.dp, 30.dp, Color.White.copy(alpha = 0.14f)) {
            if (a.playing) drawPause() else drawPlay()
        }
        MediaButton(a.onNext, 64.dp, 26.dp) { drawSkip(forward = true) }
    }
}

@Composable
private fun MediaButton(
    onClick: () -> Unit,
    size: Dp,
    glyph: Dp,
    background: Color = Color.Transparent,
    draw: DrawScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.6f), label = "press")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(glyph)) { draw() }
    }
}

private fun DrawScope.drawPlay() {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.18f, h * 0.06f)
        lineTo(w * 0.94f, h * 0.5f)
        lineTo(w * 0.18f, h * 0.94f)
        close()
    }
    drawPath(p, Color.White, style = Fill)
    drawPath(p, Color.White, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
}

private fun DrawScope.drawPause() {
    val bw = size.width * 0.26f
    val r = CornerRadius(bw * 0.3f)
    drawRoundRect(Color.White, Offset(size.width * 0.16f, size.height * 0.06f), Size(bw, size.height * 0.88f), r)
    drawRoundRect(Color.White, Offset(size.width * 0.58f, size.height * 0.06f), Size(bw, size.height * 0.88f), r)
}

/** Dois triangulos encostados, apontando para a direcao do salto. */
private fun DrawScope.drawSkip(forward: Boolean) {
    val w = size.width
    val h = size.height
    val tri = { x0: Float ->
        Path().apply {
            moveTo(x0, h * 0.16f)
            lineTo(x0 + w * 0.46f, h * 0.5f)
            lineTo(x0, h * 0.84f)
            close()
        }
    }
    withTransform({ if (!forward) scale(-1f, 1f) }) {
        for (x in listOf(w * 0.06f, w * 0.48f)) {
            val p = tri(x)
            drawPath(p, Color.White, style = Fill)
            drawPath(p, Color.White, style = Stroke(w * 0.06f, join = StrokeJoin.Round))
        }
    }
}

/** Capa grande com titulo e artista embaixo, para ficar ao lado do visualizador. */
@Composable
fun NowPlayingPanel(track: NowPlaying, art: Bitmap?, modifier: Modifier = Modifier, controls: MediaActions? = null) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth * 0.78f, maxHeight * (if (controls != null) 0.5f else 0.58f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(art, animationSpec = tween(600), label = "art") { bmp ->
                val shape = RoundedCornerShape(18.dp)
                if (bmp != null) {
                    Image(
                        remember(bmp) { bmp.asImageBitmap() }, contentDescription = null,
                        modifier = Modifier.size(side).shadow(28.dp, shape).clip(shape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    // Sem imagem: capa neutra em vidro com uma nota musical.
                    Box(
                        Modifier.size(side).clip(shape).background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Label("♪", size = 72.sp, color = Color.White.copy(alpha = 0.35f))
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            TrackText(track, Modifier.widthIn(max = side + 40.dp), centered = true)
            if (controls != null) {
                Spacer(Modifier.height(14.dp))
                MediaButtons(controls)
            }
        }
    }
}

/** Titulo e artista; usado embaixo da capa ou no canto quando nao ha imagem. */
@Composable
fun TrackText(track: NowPlaying, modifier: Modifier = Modifier, centered: Boolean = false) {
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val align = if (centered) TextAlign.Center else TextAlign.Start
        Label(
            track.title, if (centered) Modifier.fillMaxWidth() else Modifier,
            size = 26.sp, weight = FontWeight.Bold, align = align, maxLines = 2,
        )
        if (track.artist.isNotBlank()) {
            Label(
                track.artist, if (centered) Modifier.fillMaxWidth() else Modifier,
                size = 19.sp, color = Color.White.copy(alpha = 0.72f), align = align, maxLines = 1,
            )
        }
    }
}
