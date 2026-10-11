package cn.com.omnimind.nativeui.workspace

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text

/** One PDF page's size in points, for laying out a placeholder before it renders. */
@Immutable
data class PdfPageSize(val width: Int, val height: Int) {
    val aspect: Float get() = if (width <= 0 || height <= 0) 1f / 1.414f else width.toFloat() / height
}

/** An open PDF the preview reads pages from; the app owns the renderer. */
interface PdfPages {
    val pages: List<PdfPageSize>
    /** Renders page [index] [widthPx] wide, or null when the page cannot be drawn. */
    suspend fun render(index: Int, widthPx: Int): ImageBitmap?
}

/** Most zoom over fit-to-width; past this a page is better opened in a PDF app. */
const val PDF_MAX_ZOOM = 4f

/**
 * Re-render width for a zoom level: pages are rendered at the zoom they are
 * read at, in steps so a pinch does not re-render on every frame, and never
 * past [maxPx] so one page cannot exhaust memory.
 */
fun pdfRenderWidth(viewportPx: Int, zoom: Float, maxPx: Int = 2_400): Int {
    val step = when {
        zoom < 1.5f -> 1f
        zoom < 2.5f -> 2f
        else -> 3f
    }
    return (viewportPx * step).toInt().coerceIn(1, maxPx)
}

/**
 * The native PDF preview (batch 5l), replacing the Flutter page's
 * `PdfPreviewChannel` viewer. Pages stack as paper on the page color and
 * render only once they scroll near; pinch zooms the whole document,
 * double-tap toggles 2x. Dart rendered each page as a PNG over the channel
 * and decoded it again, and kept every page it had seen in memory.
 */
@Composable
fun PdfPreview(pdf: PdfPages, modifier: Modifier = Modifier) {
    val palette = LocalOmniPalette.current
    val density = LocalDensity.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val shownZoom by animateFloatAsState(zoom, label = "pdf-zoom")
    val listState = rememberLazyListState()
    var currentPage by remember { mutableStateOf(0) }
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.collect { currentPage = it } }
    BoxWithConstraints(modifier.fillMaxSize().background(palette.secondarySurface)) {
        val viewportPx = with(density) { (maxWidth - 32.dp).roundToPx() }
        val renderWidth = pdfRenderWidth(viewportPx, zoom)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                // Two fingers zoom and pan; one finger stays the list's scroll.
                .pointerInput(Unit) { detectPinch { gestureZoom, pan ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, PDF_MAX_ZOOM)
                    val slack = size.width * (zoom - 1) / 2
                    panX = (panX + pan).coerceIn(-slack, slack)
                } }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        zoom = if (zoom > 1.01f) 1f else 2f
                        panX = 0f
                    })
                }
                .graphicsLayer {
                    scaleX = shownZoom
                    scaleY = shownZoom
                    translationX = panX
                    transformOrigin = TransformOrigin(.5f, 0f)
                },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pdf.pages.size, key = { it }) { index ->
                PdfPage(pdf, index, pdf.pages[index], renderWidth)
            }
        }
        Text("${currentPage + 1} / ${pdf.pages.size}", color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .55f))
                .padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/**
 * Pinch only: reports zoom and horizontal pan while two or more pointers are
 * down, and consumes nothing for one pointer, so the list keeps scrolling.
 */
private suspend fun PointerInputScope.detectPinch(onPinch: (zoom: Float, panX: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                onPinch(event.calculateZoom(), event.calculatePan().x)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun PdfPage(pdf: PdfPages, index: Int, size: PdfPageSize, widthPx: Int) {
    // Keyed by width: zooming in re-renders sharper; scrolling away drops the bitmap with the item.
    val bitmap by produceState<ImageBitmap?>(null, pdf, index, widthPx) { value = pdf.render(index, widthPx) }
    val label = stringResource(R.string.omni_pdf_page, index + 1)
    Box(Modifier.fillMaxWidth().aspectRatio(size.aspect).shadow(2.dp, RoundedCornerShape(4.dp))
        .clip(RoundedCornerShape(4.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        val page = bitmap
        if (page != null) Image(page, label, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else CircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp)
    }
}
