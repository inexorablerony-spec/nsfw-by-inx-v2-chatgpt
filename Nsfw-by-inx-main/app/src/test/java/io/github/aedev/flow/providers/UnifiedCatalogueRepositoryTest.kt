package io.github.aedev.flow.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedCatalogueRepositoryTest {
    @Test
    fun `one failed provider does not discard another provider successful results`() = runTest {
        val good = FakeProvider("Good", "good", posts = listOf(Post("Video", "https://good.example/video", "")))
        val bad = FakeProvider("Bad", "bad", postsFailure = IllegalStateException("network down"))
        val repository = repository(listOf(good, bad), setOf("good", "bad"))

        val result = repository.getHomeFeed()

        assertEquals(1, result.items.size)
        assertEquals("good", result.items.single().providerId)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("Bad"))
        assertTrue(result.errors.single().contains("network down"))
    }

    @Test
    fun `legitimate empty catalogue has no provider errors`() = runTest {
        val empty = FakeProvider("Empty", "empty")
        val result = repository(listOf(empty), setOf("empty")).getHomeFeed()

        assertTrue(result.items.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `cancellation is propagated instead of being reported as provider failure`() = runTest {
        val cancelled = FakeProvider("Cancelled", "cancelled", postsFailure = CancellationException("caller cancelled"))
        val repository = repository(listOf(cancelled), setOf("cancelled"))

        assertSuspendThrows(CancellationException::class.java) {
            repository.getHomeFeed()
        }
    }

    @Test
    fun `providers without catalogue capability are not queried`() = runTest {
        val webView = FakeProvider("WebView", "webview", catalogue = false)
        val result = repository(listOf(webView), setOf("webview")).getHomeFeed()

        assertEquals(0, webView.postsCalls)
        assertTrue(result.items.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `disabled provider cannot serve direct catalogue requests`() = runTest {
        val provider = FakeProvider("Disabled", "disabled")
        val repository = repository(listOf(provider), emptySet())

        assertSuspendThrows(ProviderUnavailableException::class.java) {
            repository.getProviderPosts("disabled")
        }
        assertEquals(0, provider.postsCalls)
    }

    @Test
    fun `disabled provider cannot serve direct search requests`() = runTest {
        val provider = FakeProvider("Disabled", "disabled")
        val repository = repository(listOf(provider), emptySet())

        assertSuspendThrows(ProviderUnavailableException::class.java) {
            repository.searchProvider("disabled", "cats")
        }
        assertEquals(0, provider.searchCalls)
    }

    @Test
    fun `unsupported catalogue providers reject direct feed calls clearly`() = runTest {
        val provider = FakeProvider("WebView", "webview", catalogue = false)
        val repository = repository(listOf(provider), setOf("webview"))

        assertSuspendThrows(ProviderOperationException::class.java) {
            repository.getProviderPosts("webview")
        }
        assertEquals(0, provider.postsCalls)
    }

    @Test
    fun `disabled provider cannot serve metadata`() = runTest {
        val provider = FakeProvider("Disabled", "disabled")
        val repository = repository(listOf(provider), emptySet())
        val item = ProviderCatalogueItem("disabled", "Disabled", Post("Video", "https://example.com/video", ""))

        assertSuspendThrows(ProviderUnavailableException::class.java) {
            repository.getDetails(item)
        }
        assertEquals(0, provider.metaCalls)
    }

    @Test
    fun `disabled provider cannot resolve playback streams`() = runTest {
        val provider = FakeProvider("Disabled", "disabled")
        val repository = repository(listOf(provider), emptySet())
        val item = ProviderCatalogueItem("disabled", "Disabled", Post("Video", "https://example.com/video", ""))

        assertSuspendThrows(ProviderUnavailableException::class.java) {
            repository.getStreams(item)
        }
        assertEquals(0, provider.streamCalls)
    }

    @Test
    fun `catalogue deduplicates identical posts from the same provider`() = runTest {
        val post = Post("Video", "https://example.com/video", "")
        val provider = FakeProvider("Test", "test", posts = listOf(post, post))
        val result = repository(listOf(provider), setOf("test")).getHomeFeed()

        assertEquals(1, result.items.size)
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

    private fun repository(providers: List<Provider>, enabled: Set<String>) =
        UnifiedCatalogueRepository(
            enabledProviders = { providers },
            providerLookup = { id -> providers.firstOrNull { it.value == id } },
            isProviderEnabled = { it in enabled }
        )

    private class FakeProvider(
        override val name: String,
        override val value: String,
        private val posts: List<Post> = emptyList(),
        private val postsFailure: Throwable? = null,
        catalogue: Boolean = true
    ) : Provider {
        override val version = "test"
        override val description = "test provider"
        override val icon = ""
        override val supportsCatalogue = catalogue
        override val supportsSearch = catalogue

        var postsCalls = 0
        var searchCalls = 0
        var metaCalls = 0
        var streamCalls = 0

        override suspend fun getPosts(page: Int): List<Post> {
            postsCalls++
            postsFailure?.let { throw it }
            return posts
        }

        override suspend fun getSearchPosts(query: String, page: Int): List<Post> {
            searchCalls++
            return posts
        }

        override suspend fun getMeta(link: String): Info {
            metaCalls++
            return Info("Title", "", "", "", "movie", emptyList())
        }

        override suspend fun getStream(link: String): List<Stream> {
            streamCalls++
            return listOf(Stream("Test", "https://cdn.example/video.mp4", "mp4"))
        }
    }
}
