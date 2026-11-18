package io.github.aedev.flow.providers

data class Post(
    val title: String,
    val link: String,
    val image: String
)

data class Stream(
    val server: String,
    val link: String,
    val type: String,
    val headers: Map<String, String>? = null
)

data class Info(
    val title: String,
    val synopsis: String,
    val image: String,
    val imdbId: String,
    val type: String,
    val linkList: List<LinkItem>
)

data class LinkItem(
    val title: String,
    val quality: String,
    val directLinks: List<DirectLink>
)

data class DirectLink(
    val title: String,
    val link: String,
    val type: String
)

data class ProviderInfo(
    val name: String,
    val value: String,
    val version: String,
    val description: String,
    val icon: String,
    val enabled: Boolean
)

interface Provider {
    val name: String
    val value: String
    val version: String
    val description: String
    val icon: String

    /** Whether this provider contributes cards to the unified home catalogue. */
    val supportsCatalogue: Boolean
        get() = true

    /** Whether this provider participates in unified catalogue searches. */
    val supportsSearch: Boolean
        get() = supportsCatalogue

    suspend fun getPosts(page: Int = 1): List<Post>
    suspend fun getSearchPosts(query: String, page: Int = 1): List<Post>
    suspend fun getMeta(link: String): Info
    suspend fun getStream(link: String): List<Stream>

    fun getInfo(enabled: Boolean): ProviderInfo = ProviderInfo(name, value, version, description, icon, enabled)
}

/** Adds consistent provider and operation context without hiding the original cause. */
class ProviderOperationException(
    val providerName: String,
    val operation: String,
    cause: Throwable
) : Exception(
    "$providerName $operation failed: ${cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName}",
    cause
)

/** Raised when a provider is missing or disabled at the moment an operation is requested. */
class ProviderUnavailableException(message: String) : IllegalStateException(message)
