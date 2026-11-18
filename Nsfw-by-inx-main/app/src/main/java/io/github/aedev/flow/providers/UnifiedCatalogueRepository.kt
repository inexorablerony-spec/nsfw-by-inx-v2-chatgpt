package io.github.aedev.flow.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates enabled providers without allowing one provider failure to discard successful results
 * from the others. The injectable suppliers also keep the repository deterministic in JVM tests.
 */
class UnifiedCatalogueRepository(
    private val enabledProviders: () -> List<Provider> = { ProviderRegistry.getEnabled() },
    private val providerLookup: (String) -> Provider? = { ProviderRegistry.getByValue(it) },
    private val isProviderEnabled: (String) -> Boolean = { ProviderRegistry.isEnabled(it) }
) {
    suspend fun getHomeFeed(page: Int = 1): CatalogueResult {
        require(page >= 1) { "Page must be 1 or greater" }
        return withContext(Dispatchers.IO) {
            val results = LinkedHashMap<String, ProviderCatalogueItem>()
            val errors = mutableListOf<String>()

            enabledProviders().forEach { provider ->
                if (!provider.supportsCatalogue || !isProviderEnabled(provider.value)) return@forEach
                try {
                    // Recheck immediately before dispatch so a provider disabled during a refresh is not called.
                    if (!isProviderEnabled(provider.value)) return@forEach
                    val posts = provider.getPosts(page)
                    posts.forEach { post ->
                        val key = "${provider.value}\u0000${post.link}"
                        results.putIfAbsent(key, ProviderCatalogueItem(provider.value, provider.name, post))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors += providerError(provider, "catalogue loading", e)
                }
            }

            CatalogueResult(results.values.toList(), errors)
        }
    }

    suspend fun search(query: String, page: Int = 1): CatalogueResult {
        require(page >= 1) { "Page must be 1 or greater" }
        require(query.isNotBlank()) { "Search query must not be blank" }
        return withContext(Dispatchers.IO) {
            val results = LinkedHashMap<String, ProviderCatalogueItem>()
            val errors = mutableListOf<String>()

            enabledProviders().forEach { provider ->
                if (!provider.supportsSearch || !isProviderEnabled(provider.value)) return@forEach
                try {
                    if (!isProviderEnabled(provider.value)) return@forEach
                    val posts = provider.getSearchPosts(query, page)
                    posts.forEach { post ->
                        val key = "${provider.value}\u0000${post.link}"
                        results.putIfAbsent(key, ProviderCatalogueItem(provider.value, provider.name, post))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors += providerError(provider, "search", e)
                }
            }

            CatalogueResult(results.values.toList(), errors)
        }
    }

    suspend fun getProviderPosts(providerId: String, page: Int = 1): List<Post> {
        require(page >= 1) { "Page must be 1 or greater" }
        return withContext(Dispatchers.IO) {
            val provider = requireEnabledProvider(providerId)
            if (!provider.supportsCatalogue) {
                throw ProviderOperationException(
                    provider.name,
                    "catalogue loading",
                    UnsupportedOperationException("This provider does not expose a catalogue")
                )
            }
            runOperation(provider, "catalogue loading") { provider.getPosts(page) }
        }
    }

    suspend fun searchProvider(providerId: String, query: String, page: Int = 1): List<Post> {
        require(page >= 1) { "Page must be 1 or greater" }
        require(query.isNotBlank()) { "Search query must not be blank" }
        return withContext(Dispatchers.IO) {
            val provider = requireEnabledProvider(providerId)
            if (!provider.supportsSearch) {
                throw ProviderOperationException(
                    provider.name,
                    "search",
                    UnsupportedOperationException("This provider does not support catalogue search")
                )
            }
            runOperation(provider, "search") { provider.getSearchPosts(query, page) }
        }
    }

    suspend fun getDetails(item: ProviderCatalogueItem): Info = withContext(Dispatchers.IO) {
        val provider = requireEnabledProvider(item.providerId)
        runOperation(provider, "metadata lookup") { provider.getMeta(item.post.link) }
    }

    suspend fun getStreams(item: ProviderCatalogueItem): List<Stream> = withContext(Dispatchers.IO) {
        val provider = requireEnabledProvider(item.providerId)
        runOperation(provider, "stream resolution") { provider.getStream(item.post.link) }
    }

    private fun requireEnabledProvider(providerId: String): Provider {
        val provider = providerLookup(providerId)
            ?: throw ProviderUnavailableException("Provider '$providerId' is not registered")
        if (!isProviderEnabled(providerId)) {
            throw ProviderUnavailableException("${provider.name} is disabled. Enable it in Provider Manager to continue.")
        }
        return provider
    }

    private inline fun <T> runOperation(provider: Provider, operation: String, block: () -> T): T {
        try {
            // A second check narrows the race between lookup and beginning the provider request.
            if (!isProviderEnabled(provider.value)) {
                throw ProviderUnavailableException("${provider.name} was disabled before $operation started")
            }
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderUnavailableException) {
            throw e
        } catch (e: ProviderOperationException) {
            throw e
        } catch (e: Exception) {
            throw ProviderOperationException(provider.name, operation, e)
        }
    }

    private fun providerError(provider: Provider, operation: String, error: Exception): String =
        if (error is ProviderOperationException) error.message ?: "${provider.name} $operation failed"
        else "${provider.name} $operation failed: ${error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}"
}
