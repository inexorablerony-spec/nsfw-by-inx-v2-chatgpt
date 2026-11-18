package io.github.aedev.flow.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoreSearchRequest(
    val queries: List<String>,
    val sources: List<String>,
    val concurrency: Int = 35,
    val timeoutMs: Int = 6000,
    val maxResultsPerSite: Int = 20,
    val strictMatch: Boolean = true
)

@Serializable
data class CoreSearchResult(
    @SerialName("page_url") val pageUrl: String,
    val source: String,
    val label: String
)

@Serializable
data class CoreSearchResponse(
    val results: List<CoreSearchResult> = emptyList()
)

@Serializable
data class CoreResolveRequest(
    val urls: List<String>,
    val referer: String = "",
    val timeoutMs: Int = 6000,
    val probeStream: Boolean = true
)

@Serializable
data class CoreResolveResult(
    val status: String,
    @SerialName("stream_url") val streamUrl: String? = null,
    val headers: Map<String, String>? = null,
    @SerialName("elapsed_ms") val elapsedMs: Int? = null,
    val hoster: String? = null,
    @SerialName("content_type") val contentType: String? = null
)
