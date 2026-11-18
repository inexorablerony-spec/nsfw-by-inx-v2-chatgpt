package io.github.aedev.flow.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.providers.CatalogueResult
import io.github.aedev.flow.providers.Post
import io.github.aedev.flow.providers.ProviderCatalogueItem
import io.github.aedev.flow.providers.ProviderRegistry
import io.github.aedev.flow.providers.UnifiedCatalogueRepository
import io.github.aedev.flow.providers.WebViewProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Provider-first home screen. It intentionally does not use Flow's YouTube feed. */
@Composable
fun UnifiedHomeScreen(
    onVideoClick: (ProviderCatalogueItem) -> Unit,
) {
    var items by remember { mutableStateOf<List<ProviderCatalogueItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var catalogueError by remember { mutableStateOf<String?>(null) }
    var webViewUrl by remember { mutableStateOf("") }
    var webViewUrlError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }

    val repository = remember { UnifiedCatalogueRepository() }
    val scope = rememberCoroutineScope()
    val registryState by ProviderRegistry.state.collectAsState()
    val webViewEnabled = registryState.providers.any { it.value == "webview" && it.enabled }

    suspend fun loadPage(targetPage: Int, append: Boolean) {
        isLoading = true
        catalogueError = null
        try {
            val result: CatalogueResult = activeQuery
                ?.takeIf { it.isNotBlank() }
                ?.let { repository.search(it, targetPage) }
                ?: repository.getHomeFeed(targetPage)
            items = if (append) {
                (items + result.items).distinctBy { "${it.providerId}:${it.post.link}" }
            } else {
                result.items
            }
            errors = result.errors
            page = targetPage
            // Providers currently expose page-based results but no total-page contract.
            // An empty page is the only reliable end-of-list signal available in this API.
            hasMore = result.items.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            catalogueError = e.message ?: "Failed to load catalogue"
            if (!append) items = emptyList()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPage(targetPage = 1, append = false)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Video catalogue",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search videos") },
            )
            Button(
                onClick = {
                    activeQuery = searchQuery.trim().takeIf { it.isNotEmpty() }
                    scope.launch { loadPage(targetPage = 1, append = false) }
                },
                enabled = !isLoading && searchQuery.isNotBlank(),
            ) {
                Text("Search")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    searchQuery = ""
                    activeQuery = null
                    scope.launch { loadPage(targetPage = 1, append = false) }
                },
                enabled = !isLoading && activeQuery != null,
            ) {
                Text("Clear search")
            }
            TextButton(
                onClick = { scope.launch { loadPage(targetPage = 1, append = false) } },
                enabled = !isLoading,
            ) {
                Text("Refresh")
            }
        }

        if (webViewEnabled) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Open a website", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = webViewUrl,
                    onValueChange = {
                        webViewUrl = it
                        webViewUrlError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Website URL") },
                    placeholder = { Text("https://example.com/video") },
                    isError = webViewUrlError != null,
                )
                webViewUrlError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        val normalized = WebViewProvider.normalizeInputUrl(webViewUrl)
                        if (normalized == null) {
                            webViewUrlError = "Enter a valid HTTP or HTTPS website URL"
                        } else {
                            webViewUrlError = null
                            val host = runCatching { java.net.URI(normalized).host }.getOrNull().orEmpty()
                            onVideoClick(
                                ProviderCatalogueItem(
                                    providerId = "webview",
                                    providerName = "WebView",
                                    post = Post(title = host.ifBlank { normalized }, link = normalized, image = ""),
                                ),
                            )
                        }
                    },
                    enabled = webViewUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Extract and play")
                }
            }
        }

        if (isLoading && items.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            Text("Loading videos…", modifier = Modifier.padding(horizontal = 16.dp))
        } else if (catalogueError != null && items.isEmpty()) {
            Text("Error: $catalogueError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            Button(
                onClick = { scope.launch { loadPage(targetPage = page, append = false) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Retry") }
        } else {
            if (catalogueError != null) {
                Text(catalogueError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (errors.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Some providers failed (${errors.size})", color = MaterialTheme.colorScheme.error)
                    errors.forEach { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            if (items.isEmpty() && !isLoading) {
                Text(
                    if (activeQuery == null) "No videos found" else "No results for ‘${activeQuery}’",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(items, key = { "${it.providerId}:${it.post.link}" }) { item ->
                        VideoCard(item = item, onClick = { onVideoClick(item) })
                    }
                }
            }
            if (hasMore && items.isNotEmpty()) {
                Button(
                    onClick = { scope.launch { loadPage(targetPage = page + 1, append = true) } },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Load more")
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    item: ProviderCatalogueItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(4.dp).fillMaxWidth(),
        onClick = onClick,
    ) {
        Column {
            AsyncImage(
                model = item.post.image,
                contentDescription = item.post.title,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = item.post.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.padding(8.dp),
            )
            Text(
                text = item.providerName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
