package io.github.aedev.flow.providers

data class ProviderCatalogueItem(
    val providerId: String,
    val providerName: String,
    val post: Post
)

data class CatalogueResult(
    val items: List<ProviderCatalogueItem>,
    val errors: List<String> = emptyList()
)
