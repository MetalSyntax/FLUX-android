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
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    // Desplazamiento automático: px por fotograma; al llegar al final pasa al capítulo siguiente.
    var autoScroll by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableStateOf(1.2f) }
    LaunchedEffect(autoScroll, chapter, ready) {
        if (!autoScroll || !ready) return@LaunchedEffect
        var acc = 0f
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val wv = webRef[0] ?: break
            if (!wv.canScrollVertically(1)) {
                if (chapter < n - 1) { chapter++; pendingFraction = 0f; ready = false } else autoScroll = false
                break
            }
            acc += scrollSpeed * wv.resources.displayMetrics.density
            val step = acc.toInt(); if (step > 0) { wv.scrollBy(0, step); acc -= step }
        }
    }

    // Lectura en voz alta con el TTS del sistema: párrafo a párrafo, resaltando y siguiendo el que se lee.
    var speaking by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableStateOf(1.0f) }
    var ttsReady by remember { mutableStateOf<Boolean?>(null) }
    val ttsRef = remember { arrayOfNulls<android.speech.tts.TextToSpeech>(1) }
    val chapterEnd = remember { intArrayOf(-1) } // id del último fragmento del capítulo en cola
    DisposableEffect(Unit) {
        val tts = android.speech.tts.TextToSpeech(context) { status -> main.post { ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS } }
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String) {
                val idx = id.substringBefore('.')
                main.post { webRef[0]?.evaluateJavascript(TTS_FOCUS_JS.replace("IDX", idx), null) }
            }
            override fun onDone(id: String) {
                if (id.substringBefore('.').toIntOrNull() == chapterEnd[0] && id.endsWith(".last")) main.post {
                    if (!speaking) return@post
                    if (chapter < n - 1) { chapter++; pendingFraction = 0f; ready = false } else speaking = false
                }
            }
            @Deprecated("API vieja") override fun onError(id: String) {}
        })
        ttsRef[0] = tts
        onDispose { tts.stop(); tts.shutdown(); ttsRef[0] = null }
    }
    LaunchedEffect(ttsRate) { ttsRef[0]?.setSpeechRate(ttsRate) }
    LaunchedEffect(speaking, chapter, ready, ttsReady) {
        val tts = ttsRef[0] ?: return@LaunchedEffect
        if (!speaking) { tts.stop(); webRef[0]?.evaluateJavascript(TTS_CLEAR_JS, null); return@LaunchedEffect }
        if (!ready) { tts.stop(); return@LaunchedEffect }
        if (ttsReady != true) return@LaunchedEffect
        webRef[0]?.evaluateJavascript(TTS_COLLECT_JS) { raw ->
            val parsed = runCatching {
                val obj = kotlinx.serialization.json.Json.parseToJsonElement(
                    kotlinx.serialization.json.Json.parseToJsonElement(raw).let { (it as kotlinx.serialization.json.JsonPrimitive).content }
                ) as kotlinx.serialization.json.JsonObject
                val start = (obj["start"] as kotlinx.serialization.json.JsonPrimitive).content.toInt()
                val items = (obj["items"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
                start to items
            }.getOrNull()
            if (parsed == null || parsed.second.isEmpty()) {
                if (chapter < n - 1) { chapter++; pendingFraction = 0f; ready = false } else speaking = false
                return@evaluateJavascript
            }
            val (start, items) = parsed
            tts.stop()
            chapterEnd[0] = items.lastIndex
            val max = (android.speech.tts.TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
            for (i in start..items.lastIndex) {
                val parts = items[i].chunked(max)
                parts.forEachIndexed { k, part ->
                    val id = if (i == items.lastIndex && k == parts.lastIndex) "$i.last" else "$i.$k"
                    tts.speak(part, android.speech.tts.TextToSpeech.QUEUE_ADD, null, id)
                }
            }
        }
    }

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
                wv.keepScreenOn = speaking || autoScroll
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { speaking = !speaking }, enabled = ttsReady != false) {
                            Icon(
                                if (speaking) androidx.compose.material.icons.Icons.Filled.Stop else androidx.compose.material.icons.Icons.Filled.RecordVoiceOver,
                                contentDescription = if (speaking) "Detener lectura en voz alta" else "Leer en voz alta"
                            )
                        }
                        if (speaking) androidx.compose.material3.TextButton(onClick = {
                            ttsRate = when (ttsRate) { 0.8f -> 1.0f; 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; else -> 0.8f }
                        }) { Text("${ttsRate}x", color = barTextColor) }
                        IconButton(onClick = { autoScroll = !autoScroll }) {
                            Icon(
                                if (autoScroll) androidx.compose.material.icons.Icons.Filled.Pause else androidx.compose.material.icons.Icons.Filled.KeyboardDoubleArrowDown,
                                contentDescription = if (autoScroll) "Pausar desplazamiento automático" else "Desplazamiento automático"
                            )
                        }
                        if (autoScroll) Slider(
                            value = scrollSpeed, onValueChange = { scrollSpeed = it }, valueRange = 0.3f..4f,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        if (ttsReady == false && !autoScroll) Text("Sin voz del sistema", style = MaterialTheme.typography.labelSmall)
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

/** Junta los bloques de texto del capítulo, los marca y devuelve desde cuál empezar (el primero visible). */
private const val TTS_COLLECT_JS = "(function(){var els=document.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,dd,dt,td');" +
    "var items=[],start=-1;for(var i=0;i<els.length;i++){var e=els[i];if(e.querySelector('p,li,blockquote'))continue;var t=(e.innerText||'').trim();if(!t)continue;" +
    "e.setAttribute('data-flux-tts',items.length);if(start<0&&e.getBoundingClientRect().bottom>0)start=items.length;items.push(t)}" +
    "if(!items.length){var b=(document.body.innerText||'').split(/\\n+/).map(function(s){return s.trim()}).filter(function(s){return s});items=b;start=0}" +
    "return JSON.stringify({start:Math.max(start,0),items:items})})()"

private const val TTS_FOCUS_JS = "(function(){var o=document.querySelector('.flux-tts');if(o)o.classList.remove('flux-tts');" +
    "var e=document.querySelector('[data-flux-tts=\"IDX\"]');if(!e)return;e.classList.add('flux-tts');" +
    "if(!document.getElementById('flux-tts-style')){var s=document.createElement('style');s.id='flux-tts-style';s.textContent='.flux-tts{background:rgba(0,192,139,.22)!important;border-radius:4px}';document.head.appendChild(s)}" +
    "e.scrollIntoView({block:'center',behavior:'smooth'})})()"

private const val TTS_CLEAR_JS = "(function(){var o=document.querySelector('.flux-tts');if(o)o.classList.remove('flux-tts')})()"

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
