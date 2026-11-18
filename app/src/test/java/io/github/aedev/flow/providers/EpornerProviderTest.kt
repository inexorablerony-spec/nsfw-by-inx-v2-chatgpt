package io.github.aedev.flow.providers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class EpornerProviderTest {
    @Test
    fun `provider metadata satisfies the complete provider info contract`() {
        val info = EpornerProvider().getInfo(enabled = true)

        assertEquals("Eporner", info.name)
        assertEquals("eporner", info.value)
        assertEquals("1.0", info.version)
        assertEquals("Eporner video provider", info.description)
        assertTrue(info.icon.startsWith("https://"))
        assertTrue(info.enabled)
    }

    @Test
    fun `site page URLs require HTTPS and the Eporner host`() {
        val provider = EpornerProvider()

        assertEquals("www.eporner.com", provider.validateSitePageUrl("https://www.eporner.com/hd-porn/example/").host)
        assertThrows(IllegalArgumentException::class.java) {
            provider.validateSitePageUrl("http://www.eporner.com/hd-porn/example/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.validateSitePageUrl("https://eporner.com.evil.example/video/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.validateSitePageUrl("https://user@www.eporner.com/video/")
        }
    }

    @Test
    fun `search URL encodes query characters instead of replacing spaces with hyphens`() {
        val provider = EpornerProvider()
        val searchUrl = provider.searchUrl("cats and dogs & birds", 2)

        assertTrue(searchUrl.contains("cats+and+dogs+%26+birds"))
        assertTrue(searchUrl.endsWith("/2/"))
    }

    @Test
    fun `catalogue parsing resolves links and deduplicates matching anchors`() {
        val provider = EpornerProvider()
        val html = """
            <div>
              <a href="/hd-porn/video-one/"><img alt="One &amp; Only" data-src="/thumb-one.jpg"></a>
              <a href="/hd-porn/video-one/"><img alt="Duplicate" src="/duplicate.jpg"></a>
              <a href="/hd-porn/video-two/"><img alt="Two" src="https://cdn.example/two.webp"></a>
            </div>
        """.trimIndent()

        val posts = provider.parsePosts(html)

        assertEquals(2, posts.size)
        assertEquals("One & Only", posts.first().title)
        assertEquals("https://www.eporner.com/hd-porn/video-one/", posts.first().link)
        assertEquals("https://www.eporner.com/thumb-one.jpg", posts.first().image)
    }

    @Test
    fun `direct media links are returned without opening an HTTP connection`() = runBlocking {
        var openedConnection = false
        val provider = EpornerProvider {
            openedConnection = true
            throw AssertionError("A direct stream must not be fetched as HTML")
        }

        val streams = provider.getStream("https://cdn.example/path/video.m3u8?token=abc")

        assertFalse(openedConnection)
        assertEquals(1, streams.size)
        assertEquals("m3u8", streams.single().type)
        assertEquals("https://cdn.example/path/video.m3u8?token=abc", streams.single().link)
        assertNotNull(streams.single().headers?.get("Referer"))
    }

    @Test
    fun `stream extraction normalizes escaped links filters previews and deduplicates`() {
        val provider = EpornerProvider()
        val html = """
            source: "https:\/\/cdn.example\/media\/main.mp4?token=1\u0026x=2";
            source2: "https://cdn.example/media/main.mp4?token=1&x=2";
            preview: "https://cdn.example/media/preview.mp4";
            hls: "https://cdn.example/media/master.m3u8";
        """.trimIndent()

        val streams = provider.extractStreams(html, "https://www.eporner.com/hd-porn/example/")

        assertEquals(2, streams.size)
        assertEquals("mp4", streams[0].type)
        assertEquals("https://cdn.example/media/main.mp4?token=1&x=2", streams[0].link)
        assertEquals("m3u8", streams[1].type)
        assertEquals("https://www.eporner.com/hd-porn/example/", streams[0].headers?.get("Referer"))
    }

    @Test
    fun `HTTP failure still disconnects the connection`() = runBlocking {
        lateinit var connection: StubConnection
        val provider = EpornerProvider { url ->
            StubConnection(url, 403, "forbidden").also { connection = it }
        }

        val error = try {
            provider.getPosts()
            throw AssertionError("Expected the HTTP failure to be propagated")
        } catch (expected: ProviderOperationException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("Eporner catalogue loading failed"))
        assertTrue(error.cause is IOException)
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun `redirect to a non Eporner host is rejected and connection is closed`() = runBlocking {
        lateinit var connection: StubConnection
        val provider = EpornerProvider { url ->
            StubConnection(url, 302, "").also {
                it.redirectLocation = "https://evil.example/redirected"
                connection = it
            }
        }

        val error = try {
            provider.getPosts()
            throw AssertionError("Expected external redirect to be rejected")
        } catch (expected: ProviderOperationException) {
            expected
        }

        assertTrue(error.cause is IllegalArgumentException)
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun `successful HTTP response disconnects after reading`() = runBlocking {
        lateinit var connection: StubConnection
        val provider = EpornerProvider { url ->
            StubConnection(url, 200, "<html></html>").also { connection = it }
        }

        assertTrue(provider.getPosts().isEmpty())
        assertTrue(connection.wasDisconnected)
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
    }

    private class StubConnection(
        url: URL,
        private val status: Int,
        private val body: String
    ) : HttpURLConnection(url) {
        var wasDisconnected = false
            private set
        var redirectLocation: String? = null

        override fun connect() = Unit
        override fun disconnect() { wasDisconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String?): String? = if (name.equals("Location", ignoreCase = true)) redirectLocation else null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }
}
