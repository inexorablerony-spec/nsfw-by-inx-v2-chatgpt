package io.github.aedev.flow.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class MediaCandidate(
    val url: String,
    val type: String,
    val sourcePage: String,
    val userAgent: String,
    val referer: String,
    val cookies: String? = null
)

sealed class ExtractionResult {
    data class Success(val candidates: List<MediaCandidate>) : ExtractionResult()
    data class Error(val message: String, val type: ErrorType) : ExtractionResult()
    
    enum class ErrorType {
        TIMEOUT,
        PAGE_LOAD_FAILED,
        NO_MEDIA_FOUND,
        CANCELLED,
        INVALID_URL
    }
}

class WebViewExtractor(private val context: android.content.Context) {
    
    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val EXTRACTION_TIMEOUT_MS = 20000L
        private const val OBSERVATION_PERIOD_MS = 8000L
        private const val POLL_INTERVAL_MS = 500L
        private val mainHandler = Handler(Looper.getMainLooper())
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractMedia(url: String): ExtractionResult {
        if (!isValidPageUrl(url)) {
            return ExtractionResult.Error("Enter a valid HTTP or HTTPS website URL", ExtractionResult.ErrorType.INVALID_URL)
        }

        return withContext(Dispatchers.Main) {
            try {
                withTimeout(EXTRACTION_TIMEOUT_MS) {
                    extractMediaInternal(url)
                }
            } catch (e: TimeoutCancellationException) {
                // Do not swallow cancellation initiated by a parent coroutine or navigation change.
                currentCoroutineContext().ensureActive()
                ExtractionResult.Error("Extraction timed out", ExtractionResult.ErrorType.TIMEOUT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExtractionResult.Error(e.message ?: "Unknown error", ExtractionResult.ErrorType.PAGE_LOAD_FAILED)
            }
        }
    }

    private fun isValidPageUrl(value: String): Boolean {
        val uri = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port in 1..65535)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractMediaInternal(url: String): ExtractionResult = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val observationStarted = AtomicBoolean(false)
        val polling = AtomicBoolean(false)
        val webView = WebView(context)
        val candidates = mutableMapOf<String, MediaCandidate>()
        
        fun complete(result: ExtractionResult) {
            if (!completed.compareAndSet(false, true)) return
            polling.set(false)
            destroyWebViewSafely(webView)
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) {
                polling.set(false)
                destroyWebViewSafely(webView)
            }
        }
        
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = USER_AGENT
            settings.mediaPlaybackRequiresUserGesture = false
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (completed.get()) return
                    if (!observationStarted.compareAndSet(false, true)) return
                    
                    injectObserver(view ?: return)
                    startObservation(view, url, polling, candidates) { result ->
                        complete(result)
                    }
                }
                
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true && !completed.get()) {
                        complete(ExtractionResult.Error("Page load failed", ExtractionResult.ErrorType.PAGE_LOAD_FAILED))
                    }
                }
            }
        }
        
        webView.loadUrl(url)
    }
    
    private fun destroyWebViewSafely(webView: WebView) {
        val cleanup = Runnable {
            try {
                webView.stopLoading()
                webView.webViewClient = WebViewClient()
                webView.webChromeClient = null
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Exception) {}
        }
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run()
        } else {
            mainHandler.post(cleanup)
        }
    }
    
    private fun injectObserver(webView: WebView) {
        val js = """
            (function () {
                if (window.__flowExtractorInstalled) return;
                window.__flowExtractorInstalled = true;
                window.__flowMediaCandidates = [];
                const seen = new Set();
                
                function addCandidate(url, type) {
                    if (!url || typeof url !== "string") return;
                    try {
                        const parsed = new URL(url, location.href);
                        if (!["http:", "https:"].includes(parsed.protocol)) return;
                        const normalized = parsed.href;
                        if (seen.has(normalized)) return;
                        seen.add(normalized);
                        const lower = normalized.toLowerCase();
                        if (lower.includes("thumbnail") || lower.includes("preview") || lower.includes("poster") || lower.includes("analytics") || lower.includes("tracking")) return;
                        if (!/\.(mp4|m3u8)(?:$|[?#])/i.test(lower)) return;
                        window.__flowMediaCandidates.push({url: normalized, type: type || "unknown"});
                    } catch (_) {}
                }
                
                function inspectVideo(video) {
                    if (!video) return;
                    addCandidate(video.currentSrc, "video");
                    addCandidate(video.src, "video");
                    video.querySelectorAll("source").forEach(source => {
                        addCandidate(source.src, source.type || "source");
                    });
                }
                
                function inspectDocument() {
                    document.querySelectorAll("video").forEach(inspectVideo);
                    if (window.playlist && Array.isArray(window.playlist.sources)) {
                        window.playlist.sources.forEach(source => {
                            if (source.file) addCandidate(source.file, source.type);
                        });
                    }
                    document.querySelectorAll("iframe").forEach(function(iframe) {
                        try {
                            var src = iframe.src || iframe.getAttribute("src");
                            if (src && (src.includes(".mp4") || src.includes(".m3u8"))) {
                                addCandidate(src, "iframe");
                            }
                        } catch (_) {}
                    });
                    document.querySelectorAll("embed").forEach(function(embed) {
                        try {
                            var src = embed.src || embed.getAttribute("src");
                            if (src && (src.includes(".mp4") || src.includes(".m3u8"))) {
                                addCandidate(src, "embed");
                            }
                        } catch (_) {}
                    });
                    try {
                        performance.getEntriesByType("resource").forEach(entry => {
                            addCandidate(entry.name, "resource");
                        });
                    } catch (_) {}
                }
                
                const observer = new MutationObserver(() => { inspectDocument(); });
                observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ["src"]});
                window.__flowExtractorObserver = observer;
                inspectDocument();
                window.__flowExtractorCleanup = function () {
                    observer.disconnect();
                    window.__flowExtractorObserver = null;
                };
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }
    
    private fun startObservation(
        webView: WebView,
        sourcePage: String,
        polling: AtomicBoolean,
        candidates: MutableMap<String, MediaCandidate>,
        onComplete: (ExtractionResult) -> Unit
    ) {
        polling.set(true)
        val deadline = SystemClock.elapsedRealtime() + OBSERVATION_PERIOD_MS
        
        fun poll() {
            if (!polling.get()) return
            
            if (SystemClock.elapsedRealtime() >= deadline) {
                polling.set(false)
                val result = if (candidates.isNotEmpty()) {
                    ExtractionResult.Success(candidates.values.toList())
                } else {
                    ExtractionResult.Error("No media found", ExtractionResult.ErrorType.NO_MEDIA_FOUND)
                }
                onComplete(result)
                return
            }
            
            webView.evaluateJavascript("JSON.stringify(window.__flowMediaCandidates || [])") { result ->
                if (!polling.get()) return@evaluateJavascript
                
                val newCandidates = parseJsonResult(result, sourcePage)
                newCandidates.forEach { candidate ->
                    candidates[candidate.url] = candidate
                }
                
                mainHandler.postDelayed({ poll() }, POLL_INTERVAL_MS)
            }
        }
        
        mainHandler.postDelayed({ poll() }, POLL_INTERVAL_MS)
    }
    
    private fun parseJsonResult(result: String?, sourcePage: String): List<MediaCandidate> {
        val array = parseJavascriptArray(result)
        val cookieManager = CookieManager.getInstance()
        
        return (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url", "")
                val type = obj.optString("type", "unknown")
                
                if (isValidMediaUrl(url)) {
                    val cookies = cookieManager.getCookie(url)
                    MediaCandidate(
                        url = url,
                        type = determineMediaType(url, type),
                        sourcePage = sourcePage,
                        userAgent = USER_AGENT,
                        referer = sourcePage,
                        cookies = cookies?.takeIf { it.isNotBlank() }
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parseJavascriptArray(result: String?): JSONArray {
        if (result.isNullOrBlank() || result == "null") return JSONArray()
        
        return try {
            val decoded = JSONTokener(result).nextValue()
            when (decoded) {
                is JSONArray -> decoded
                is String -> JSONArray(decoded)
                else -> JSONArray()
            }
        } catch (_: Exception) {
            JSONArray()
        }
    }
    
    private fun isValidMediaUrl(value: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(value)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            val path = uri.path?.lowercase().orEmpty()
            
            scheme in setOf("http", "https") &&
                !host.isNullOrBlank() &&
                (path.endsWith(".mp4") || path.endsWith(".m3u8"))
        } catch (_: Exception) {
            false
        }
    }
    
    private fun determineMediaType(url: String, hint: String): String {
        return when {
            url.contains(".m3u8") -> "m3u8"
            url.contains(".mp4") -> "mp4"
            else -> hint
        }
    }
}
