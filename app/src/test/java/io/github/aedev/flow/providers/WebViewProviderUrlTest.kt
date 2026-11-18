package io.github.aedev.flow.providers

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.aedev.flow.webview.ExtractionResult
import io.github.aedev.flow.webview.MediaCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewProviderUrlTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `bare website URL is normalized to HTTPS`() {
        assertEquals(
            "https://example.com/watch?id=42",
            WebViewProvider.normalizeInputUrl(" example.com/watch?id=42 ")
        )
    }

    @Test
    fun `explicit HTTP and HTTPS URLs are preserved`() {
        assertEquals("http://example.com/video", WebViewProvider.normalizeInputUrl("http://example.com/video"))
        assertEquals("https://example.com/video", WebViewProvider.normalizeInputUrl("https://example.com/video"))
    }

    @Test
    fun `non web schemes and malformed URLs are rejected`() {
        assertNull(WebViewProvider.normalizeInputUrl("javascript:alert(1)"))
        assertNull(WebViewProvider.normalizeInputUrl("file:///etc/passwd"))
        assertNull(WebViewProvider.normalizeInputUrl("https://user:pass@example.com/video"))
        assertNull(WebViewProvider.normalizeInputUrl("https:///missing-host"))
        assertNull(WebViewProvider.normalizeInputUrl("   "))
    }

    @Test
    fun `normalized URL has a host and HTTP scheme`() {
        val normalized = WebViewProvider.normalizeInputUrl("example.com")
        assertTrue(normalized.orEmpty().startsWith("https://"))
    }

    @Test
    fun `extractor failures are surfaced instead of becoming empty stream lists`() = runBlocking {
        val provider = WebViewProvider(context) {
            ExtractionResult.Error("No media found", ExtractionResult.ErrorType.NO_MEDIA_FOUND)
        }

        val error = assertSuspendThrows(ProviderOperationException::class.java) {
            provider.getStream("example.com/watch")
        }

        assertTrue(error.message.orEmpty().contains("WebView stream extraction"))
        assertTrue(error.message.orEmpty().contains("No media found"))
    }

    @Test
    fun `successful extraction retains referer user agent and cookies`() = runBlocking {
        val candidate = MediaCandidate(
            url = "https://cdn.example/video.mp4",
            type = "mp4",
            sourcePage = "https://example.com/watch",
            userAgent = "TestAgent",
            referer = "https://example.com/watch",
            cookies = "session=abc"
        )
        val provider = WebViewProvider(context) { ExtractionResult.Success(listOf(candidate)) }

        val stream = provider.getStream("example.com/watch").single()

        assertEquals(candidate.url, stream.link)
        assertEquals("TestAgent", stream.headers?.get("User-Agent"))
        assertEquals(candidate.referer, stream.headers?.get("Referer"))
        assertEquals("session=abc", stream.headers?.get("Cookie"))
    }

    @Test
    fun `invalid URL is rejected before extraction starts`() = runBlocking {
        var extractorCalled = false
        val provider = WebViewProvider(context) {
            extractorCalled = true
            ExtractionResult.Success(emptyList())
        }

        assertSuspendThrows(ProviderOperationException::class.java) {
            provider.getStream("javascript:alert(1)")
        }
        assertFalse(extractorCalled)
    }

    @Test
    fun `cancellation from the extractor is propagated`() = runBlocking {
        val provider = WebViewProvider(context) { throw CancellationException("caller cancelled") }

        assertSuspendThrows(CancellationException::class.java) {
            provider.getStream("example.com/watch")
        }
    }

    private suspend fun <T : Throwable> assertSuspendThrows(
        expected: Class<T>,
        block: suspend () -> Any?
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (expected.isInstance(error)) return expected.cast(error)
            throw error
        }
        throw AssertionError("Expected ${expected.simpleName} to be thrown")
    }
}
