package io.github.aedev.flow.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class EpornerProvider(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : Provider {
    override val name = "Eporner"
    override val value = "eporner"
    override val version = "1.0"
    override val description = "Eporner video provider"
    override val icon = "https://www.eporner.com/favicon.ico"

    private val baseUrl = "https://www.eporner.com"

    override suspend fun getPosts(page: Int): List<Post> {
        require(page >= 1) { "Page must be 1 or greater" }
        return withContext(Dispatchers.IO) {
            runProviderOperation("catalogue loading") {
                parsePosts(fetchHtml(homePageUrl(page)))
            }
        }
    }

    override suspend fun getSearchPosts(query: String, page: Int): List<Post> {
        require(page >= 1) { "Page must be 1 or greater" }
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Search query must not be blank" }
        return withContext(Dispatchers.IO) {
            runProviderOperation("search") {
                parsePosts(fetchHtml(searchUrl(normalizedQuery, page)))
            }
        }
    }

    override suspend fun getMeta(link: String): Info = withContext(Dispatchers.IO) {
        runProviderOperation("metadata lookup") {
            val pageUrl = validateSitePageUrl(link).toString()
            val html = fetchHtml(pageUrl)
            Info(
                title = extractTitle(html),
                synopsis = extractSynopsis(html),
                image = extractImage(html),
                imdbId = "",
                type = "movie",
                linkList = emptyList()
            )
        }
    }

    override suspend fun getStream(link: String): List<Stream> = withContext(Dispatchers.IO) {
        runProviderOperation("stream resolution") {
            val directStream = directStreamOrNull(link)
            if (directStream != null) {
                listOf(directStream)
            } else {
                val pageUrl = validateSitePageUrl(link).toString()
                extractStreams(fetchHtml(pageUrl), pageUrl)
            }
        }
    }

    private fun fetchHtml(urlString: String): String {
        var currentUrl = validateSitePageUrl(urlString)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            var connection: HttpURLConnection? = null
            try {
                connection = connectionFactory(currentUrl)
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                // Redirects are followed manually so every hop is revalidated before a new request.
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Referer", baseUrl)

                val responseCode = connection.responseCode
                if (responseCode in REDIRECT_STATUS_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("HTTP $responseCode response did not include a redirect location")
                    if (redirectCount == MAX_REDIRECTS) throw IOException("Too many redirects while requesting Eporner")
                    val redirectedUrl = runCatching { currentUrl.toURI().resolve(location).toURL().toString() }
                        .getOrElse { throw IOException("Invalid redirect URL", it) }
                    currentUrl = validateSitePageUrl(redirectedUrl)
                } else {
                    if (responseCode !in 200..299) {
                        runCatching { connection.errorStream?.close() }
                        throw IOException("HTTP $responseCode while requesting ${currentUrl.host}")
                    }
                    return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                }
            } finally {
                connection?.disconnect()
            }
        }
        throw IOException("Too many redirects while requesting Eporner")
    }

    private inline fun <T> runProviderOperation(operation: String, block: () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderOperationException) {
            throw e
        } catch (e: Exception) {
            throw ProviderOperationException(name, operation, e)
        }
    }

    internal fun homePageUrl(page: Int): String {
        require(page >= 1) { "Page must be 1 or greater" }
        return if (page == 1) baseUrl else "$baseUrl/?page=$page"
    }

    internal fun searchUrl(query: String, page: Int): String {
        require(page >= 1) { "Page must be 1 or greater" }
        require(query.isNotBlank()) { "Search query must not be blank" }
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        return if (page == 1) "$baseUrl/search/$encodedQuery/" else "$baseUrl/search/$encodedQuery/$page/"
    }

    internal fun validateSitePageUrl(value: String): URL {
        val uri = try {
            URI(value.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Eporner URL", e)
        }
        val host = uri.host?.lowercase(Locale.ROOT)
        val allowedHost = host == "eporner.com" || host == "www.eporner.com"
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                allowedHost &&
                uri.rawUserInfo == null &&
                uri.port in listOf(-1, 443)
        ) {
            "Only HTTPS Eporner page URLs are allowed"
        }
        return uri.toURL()
    }

    internal fun directStreamOrNull(value: String): Stream? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
        val type = when {
            path.endsWith(".m3u8") -> "m3u8"
            path.endsWith(".mp4") -> "mp4"
            else -> return null
        }
        require(scheme == "https" || scheme == "http") { "Direct media URLs must use HTTP or HTTPS" }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535)) {
            "Direct media URL must have a valid host and port"
        }
        return Stream(
            server = name,
            link = uri.toASCIIString(),
            type = type,
            headers = mapOf("User-Agent" to USER_AGENT, "Referer" to baseUrl)
        )
    }

    internal fun parsePosts(html: String): List<Post> {
        val posts = LinkedHashMap<String, Post>()
        ANCHOR_PATTERN.findAll(html).forEach { anchorMatch ->
            val rawLink = anchorMatch.groupValues[1].replace("&amp;", "&")
            val parsedLink = runCatching { URI(baseUrl).resolve(rawLink).toURL() }.getOrNull() ?: return@forEach
            val path = parsedLink.path.lowercase(Locale.ROOT)
            if (
                (!path.startsWith("/hd-porn/") && !path.startsWith("/video-")) ||
                parsedLink.protocol != "https" ||
                parsedLink.host.lowercase(Locale.ROOT) !in ALLOWED_HOSTS ||
                parsedLink.userInfo != null ||
                parsedLink.port !in listOf(-1, 443)
            ) {
                return@forEach
            }

            val anchorHtml = anchorMatch.groupValues[2]
            val imageTag = IMAGE_PATTERN.find(anchorHtml)?.value.orEmpty()
            val title = attribute(imageTag, "alt")
                .ifBlank { attribute(anchorMatch.value, "title") }
                .ifBlank { anchorHtml.replace(TAG_PATTERN, " ").replace(WHITESPACE_PATTERN, " ").trim() }
            if (title.isBlank()) return@forEach

            val rawImage = attribute(imageTag, "data-src")
                .ifBlank { attribute(imageTag, "data-original") }
                .ifBlank { attribute(imageTag, "src") }
            val image = if (rawImage.isBlank()) "" else {
                runCatching { URI(baseUrl).resolve(rawImage.replace("&amp;", "&")).toURL().toString() }.getOrDefault("")
            }
            val canonicalLink = parsedLink.toString()
            posts.putIfAbsent(canonicalLink, Post(title = decodeBasicEntities(title), link = canonicalLink, image = image))
        }
        return posts.values.toList()
    }

    internal fun extractStreams(html: String, referer: String): List<Stream> {
        val normalizedHtml = html
            .replace("\\/", "/")
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\u003d", "=", ignoreCase = true)
            .replace("\\u003f", "?", ignoreCase = true)
            .replace("&amp;", "&")
        val streams = LinkedHashMap<String, Stream>()

        STREAM_PATTERN.findAll(normalizedHtml).forEach { match ->
            val candidate = match.value.trimEnd(')', ']', '}', ',', ';', '.')
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return@forEach
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
            val type = when {
                path.endsWith(".m3u8") -> "m3u8"
                path.endsWith(".mp4") -> "mp4"
                else -> return@forEach
            }
            if (
                scheme !in setOf("http", "https") ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                (uri.port != -1 && uri.port !in 1..65535)
            ) return@forEach
            val lowerCandidate = candidate.lowercase(Locale.ROOT)
            if (BLOCKED_MEDIA_HINTS.any(lowerCandidate::contains)) return@forEach
            streams.putIfAbsent(
                uri.toASCIIString(),
                Stream(
                    server = name,
                    link = uri.toASCIIString(),
                    type = type,
                    headers = mapOf("User-Agent" to USER_AGENT, "Referer" to referer)
                )
            )
        }
        return streams.values.toList()
    }

    private fun extractTitle(html: String): String =
        TITLE_PATTERN.find(html)?.groupValues?.get(1)?.let(::decodeBasicEntities)?.trim().orEmpty().ifBlank { "Eporner" }

    private fun extractSynopsis(html: String): String =
        META_TAG_PATTERN.findAll(html)
            .map { it.value }
            .firstOrNull {
                attribute(it, "property").equals("og:description", ignoreCase = true) ||
                    attribute(it, "name").equals("description", ignoreCase = true)
            }
            ?.let { decodeBasicEntities(attribute(it, "content")) }
            .orEmpty()

    private fun extractImage(html: String): String =
        META_TAG_PATTERN.findAll(html)
            .map { it.value }
            .firstOrNull { attribute(it, "property").equals("og:image", ignoreCase = true) }
            ?.let { attribute(it, "content") }
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { URI(baseUrl).resolve(raw).toURL().toString() }.getOrDefault("") }
            .orEmpty()

    private fun attribute(source: String, name: String): String =
        Regex("(?is)\\b${Regex.escape(name)}\\s*=\\s*(['\"])(.*?)\\1")
            .find(source)
            ?.groupValues
            ?.get(2)
            .orEmpty()

    private fun decodeBasicEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_HOSTS = setOf("eporner.com", "www.eporner.com")
        private val BLOCKED_MEDIA_HINTS = listOf("preview", "trailer", "thumbnail", "thumb")
        private val ANCHOR_PATTERN = Regex("(?is)<a\\b[^>]*\\bhref\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</a\\s*>")
        private val IMAGE_PATTERN = Regex("(?is)<img\\b[^>]*>")
        private val TAG_PATTERN = Regex("(?is)<[^>]+>")
        private val WHITESPACE_PATTERN = Regex("\\s+")
        private val TITLE_PATTERN = Regex("(?is)<title\\b[^>]*>(.*?)</title>")
        private val META_TAG_PATTERN = Regex("(?is)<meta\\b[^>]*>")
        private val STREAM_PATTERN = Regex("(?i)https?://[^\\s\"'<>]+?\\.(?:mp4|m3u8)(?:\\?[^\\s\"'<>]*)?")
    }
}
