package com.flux.reader.presentation.reader

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flux.reader.data.files.EpubPackage
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.ReaderFont
import com.flux.reader.domain.model.UserSettings
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.roundToInt

private const val HOST = "https://flux.epub/"

/**
 * Lector EPUB: un capítulo del spine por vez en un WebView que sirve los archivos desde el zip
 * (shouldInterceptRequest). Posición guardada como 1..1000 sobre el total del libro.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun EpubReader(
    book: Book,
    prefs: UserSettings,
    jumpRequest: Int?,
    onJumpHandled: () -> Unit,
    onPosition: (Int, Int) -> Unit,
    onToggleControls: () -> Unit,
    controlsVisible: Boolean,
    barColor: Color,
    barTextColor: Color
) {
    val pkg = remember(book.id) { runCatching { EpubPackage(File(book.fileUri)) } }
    DisposableEffect(pkg) { onDispose { pkg.getOrNull()?.close() } }

    val epub = pkg.getOrNull()
    if (epub == null || epub.spine.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text("No se pudo abrir este EPUB.", modifier = Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.error)
        }
    } else {
        EpubContent(book, epub, prefs, jumpRequest, onJumpHandled, onPosition, onToggleControls, controlsVisible, barColor, barTextColor)
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun EpubContent(
    book: Book,
    epub: EpubPackage,
    prefs: UserSettings,
    jumpRequest: Int?,
    onJumpHandled: () -> Unit,
    onPosition: (Int, Int) -> Unit,
    onToggleControls: () -> Unit,
    controlsVisible: Boolean,
    barColor: Color,
    barTextColor: Color
) {
    val n = epub.spine.size
    val startPos = ((book.currentPage - 1) / 1000f * n).coerceIn(0f, n - 0.0001f)
    var chapter by remember { mutableIntStateOf(startPos.toInt()) }
    var pendingFraction by remember { mutableStateOf(startPos - startPos.toInt()) }
    var fraction by remember { mutableStateOf(pendingFraction) }
    var ready by remember { mutableStateOf(false) }
    val webRef = remember { arrayOfNulls<WebView>(1) }
    val loaded = remember { intArrayOf(chapter) } // capítulo ya pedido al WebView (-1 fuerza recarga)
    val toggle by rememberUpdatedState(onToggleControls)
    val position by rememberUpdatedState(onPosition)
    val css = styleScript(prefs)

    fun report(ch: Int, frac: Float) {
        val virtual = (((ch + frac) / n) * 1000f).roundToInt().coerceIn(1, 1000)
        position(virtual, 1000)
    }

    LaunchedEffect(jumpRequest) {
        jumpRequest?.let {
            val p = (it - 1) / 1000f * n
            chapter = p.toInt().coerceIn(0, n - 1)
            pendingFraction = p - p.toInt()
            ready = false
            loaded[0] = -1
            onJumpHandled()
        }
    }
    // cambio de tema / fuente: se reinyecta sin recargar
    LaunchedEffect(css) { webRef[0]?.evaluateJavascript(css, null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webRef[0] = this
                    settings.javaScriptEnabled = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.builtInZoomControls = false
                    isVerticalScrollBarEnabled = false
                    setBackgroundColor(AColor.TRANSPARENT)
                    val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean { toggle(); return false }
                    })
                    setOnTouchListener { _, ev -> gd.onTouchEvent(ev); false }
                    setOnScrollChangeListener { v, _, y, _, _ ->
                        val wv = v as WebView
                        val range = wv.contentHeight * wv.resources.displayMetrics.density - wv.height
                        if (ready && range > 0) {
                            fraction = (y / range).coerceIn(0f, 1f)
                            report(chapter, fraction)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val path = android.net.Uri.decode(request.url.path.orEmpty()).removePrefix("/")
                            val stream = epub.open(path) ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            val ext = path.substringAfterLast('.', "").lowercase()
                            val mime = when (ext) {
                                "xhtml", "html", "htm" -> "text/html"
                                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                            }
                            return WebResourceResponse(mime, "utf-8", stream)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            // enlaces internos: ir al capítulo; externos: ignorados
                            val u = request.url
                            if (u.host == "flux.epub") {
                                val target = android.net.Uri.decode(u.path.orEmpty()).removePrefix("/")
                                val idx = epub.spine.indexOf(target)
                                if (idx >= 0) { chapter = idx; pendingFraction = 0f; ready = false }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(css, null)
                            val frac = pendingFraction
                            view.postDelayed({
                                val range = view.contentHeight * view.resources.displayMetrics.density - view.height
                                if (range > 0 && frac > 0f) view.scrollTo(0, (frac * range).toInt())
                                ready = true
                                report(chapter, frac)
                            }, 350)
                        }
                    }
                    loadUrl(HOST + epub.spine[chapter])
                }
            },
            update = { wv ->
                wv.setBackgroundColor(barColor.toArgb())
                if (loaded[0] != chapter) { loaded[0] = chapter; ready = false; wv.loadUrl(HOST + epub.spine[chapter]) }
            },
            onRelease = { it.destroy(); webRef[0] = null }
        )

        AnimatedVisibility(visible = controlsVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = barColor.copy(alpha = 0.94f), contentColor = barTextColor) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (chapter > 0) { chapter--; pendingFraction = 0f; ready = false } }) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Capítulo anterior")
                        }
                        Slider(
                            value = chapter.toFloat(),
                            onValueChange = {},
                            enabled = false,
                            valueRange = 0f..(n - 1).coerceAtLeast(1).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { if (chapter < n - 1) { chapter++; pendingFraction = 0f; ready = false } }) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Capítulo siguiente")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sección ${chapter + 1} de $n", style = MaterialTheme.typography.labelMedium)
                        Text("${(((chapter + fraction) / n) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun cssColor(c: Long) = "#%06X".format(c and 0xFFFFFF)

private fun styleScript(s: UserSettings): String {
    val family = when (s.readerFont) {
        ReaderFont.SYSTEM_SANS -> "sans-serif"
        ReaderFont.SERIF -> "Georgia, 'Noto Serif', serif"
        ReaderFont.OUTFIT -> "sans-serif; letter-spacing:0.03em; word-spacing:0.12em"
        ReaderFont.MONOSPACE -> "monospace"
    }
    val bg = cssColor(s.readerTheme.backgroundHex)
    val fg = cssColor(s.readerTheme.textHex)
    val size = (18 * s.readerFontScale).roundToInt()
    val css = "html,body{background:$bg!important;color:$fg!important}" +
        "body{font-family:$family!important;font-size:${size}px!important;line-height:1.7!important;padding:72px 20px 96px!important;max-width:42em;margin:0 auto!important}" +
        "p,div,span,li,h1,h2,h3,h4{color:$fg!important}a{color:#00C08B!important}img,svg{max-width:100%!important;height:auto!important}"
    return "(function(){var s=document.getElementById('flux-style');if(!s){s=document.createElement('style');s.id='flux-style';document.head.appendChild(s)}" +
        "s.textContent='${css.replace("'", "\\'")}';" +
        "var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m)}m.content='width=device-width,initial-scale=1'})();"
}
