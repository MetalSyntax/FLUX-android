package com.flux.reader.presentation.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flux.reader.data.files.LibraryFiles
import com.flux.reader.data.files.PageSource
import com.flux.reader.data.files.PageSources
import com.flux.reader.data.files.PdfPageSource
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Lector de PDF / CBZ / CBR: páginas como bitmaps con zoom (pellizco y doble toque). */
@Composable
fun PageReader(
    book: Book,
    jumpRequest: Int?,
    onJumpHandled: () -> Unit,
    onPosition: (Int, Int) -> Unit,
    onToggleControls: () -> Unit,
    controlsVisible: Boolean
) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { (context.resources.displayMetrics.widthPixels * 1.5f).toInt() }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
    val source by produceState<PageSource?>(null, book.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(book.fileUri)
                if (book.type == BookType.PDF) PdfPageSource(file)
                else PageSources.openComic(file, File(context.cacheDir, "cbr/${book.id}"))
            }.onFailure { error = it.message ?: "No se pudo abrir el archivo" }.getOrNull()
        }
    }
    // se captura el valor: leer el delegado en onDispose cerraría la fuente nueva
    DisposableEffect(source) { val s = source; onDispose { s?.close() } }

    Box(modifier = Modifier.fillMaxSize()) {
        val src = source
        if (src == null) {
            if (error != null) Text(error!!, modifier = Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.error)
            else CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val total = src.pageCount.coerceAtLeast(1)
            val pager = rememberPagerState(initialPage = (book.currentPage - 1).coerceIn(0, total - 1)) { total }
            LaunchedEffect(pager) {
                snapshotFlow { pager.currentPage }.collect { onPosition(it + 1, total) }
            }
            var seek by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(seek) { seek?.let { pager.scrollToPage(it); seek = null } }
            LaunchedEffect(jumpRequest) {
                jumpRequest?.let { pager.scrollToPage((it - 1).coerceIn(0, total - 1)); onJumpHandled() }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { index ->
                val bmp by produceState<Bitmap?>(null, index) { value = runCatching { src.render(index, widthPx) }.getOrNull() }
                ZoomablePage(bmp, onTap = onToggleControls)
            }
            AnimatedVisibility(visible = controlsVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
                Surface(color = Color(0xE6000000), contentColor = Color.White) {
                    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        var drag by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = drag ?: pager.currentPage.toFloat(),
                            onValueChange = { drag = it },
                            onValueChangeFinished = {
                                drag?.let { v -> seek = v.toInt() }
                                drag = null
                            },
                            valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Página ${pager.currentPage + 1} de $total", style = MaterialTheme.typography.labelMedium)
                            Text("${((pager.currentPage + 1) * 100f / total).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(bitmap: Bitmap?, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }
                )
            }
            .pointerInput(Unit) {
                // Un dedo con zoom 1x NO se consume: deja pasar el deslizamiento al pager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = (scale - 1f) * size.width / 2f
                            val maxY = (scale - 1f) * size.height / 2f
                            offset = if (scale <= 1f) Offset.Zero else Offset(
                                (offset.x + event.calculatePan().x).coerceIn(-maxX, maxX),
                                (offset.y + event.calculatePan().y).coerceIn(-maxY, maxY)
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                }
            )
        }
    }
}
