package io.github.aedev.flow.providers

import android.content.Context
import io.github.aedev.flow.webview.ExtractionResult
import io.github.aedev.flow.webview.WebViewExtractor
import kotlinx.coroutines.CancellationException
import java.net.URI

/** A URL-based extractor, not a catalogue source. */
class WebViewProvider(
    context: Context,
    private val extractMedia: suspend (String) -> ExtractionResult = WebViewExtractor(context)::extractMedia
) : Provider {
    override val name = "WebView"
    override val value = "webview"
    override val version = "1.0"
    override val description = "Extracts media from JavaScript-rendered websites opened by URL"
    override val icon = ""
    override val supportsCatalogue = false
    override val supportsSearch = false

    override suspend fun getPosts(page: Int): List<Post> =
        throw UnsupportedOperationException("WebView is a URL extractor and does not provide catalogue posts")

    override suspend fun getSearchPosts(query: String, page: Int): List<Post> =
        throw UnsupportedOperationException("WebView is a URL extractor and does not provide catalogue search")

    override suspend fun getMeta(link: String): Info =
        throw ProviderOperationException(
            name,
            "metadata lookup",
            UnsupportedOperationException("WebView extraction does not currently provide page metadata")
        )

    override suspend fun getStream(link: String): List<Stream> {
        val normalizedUrl = normalizeInputUrl(link)
            ?: throw ProviderOperationException(
                name,
                "URL validation",
                IllegalArgumentException("Enter a valid HTTP or HTTPS website URL")
            )

        return when (val result = extractMedia(normalizedUrl)) {
            is ExtractionResult.Success -> result.candidates.map { candidate ->
                val headers = buildMap {
                    put("User-Agent", candidate.userAgent)
                    put("Referer", candidate.referer)
                    candidate.cookies?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                }
                Stream(
                    server = name,
                    link = candidate.url,
                    type = candidate.type,
                    headers = headers
                )
            }
            is ExtractionResult.Error -> throw ProviderOperationException(
                name,
                "stream extraction (${result.type.name.lowercase()})",
                IllegalStateException(result.message)
            )
        }
    }

    companion object {
        /** Adds HTTPS to a bare domain and rejects non-web schemes and malformed URLs. */
        fun normalizeInputUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val hasExplicitScheme = SCHEME_PATTERN.containsMatchIn(trimmed)
            val candidate = if (hasExplicitScheme) trimmed else "https://$trimmed"
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
            if (uri.port != -1 && uri.port !in 1..65535) return null
            return uri.normalize().toASCIIString()
        }

        private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    }
}
