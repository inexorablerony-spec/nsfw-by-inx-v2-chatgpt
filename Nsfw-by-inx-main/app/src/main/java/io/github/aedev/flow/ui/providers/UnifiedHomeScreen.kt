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
import io.github.aedev.flow.providers.Post
import io.github.aedev.flow.providers.ProviderCatalogueItem
import io.github.aedev.flow.providers.ProviderRegistry
import io.github.aedev.flow.providers.UnifiedCatalogueRepository
import io.github.aedev.flow.providers.WebViewProvider
import kotlinx.coroutines.CancellationException

@Composable
fun UnifiedHomeScreen(
    onVideoClick: (ProviderCatalogueItem) -> Unit
) {
    var items by remember { mutableStateOf<List<ProviderCatalogueItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var catalogueError by remember { mutableStateOf<String?>(null) }
    var webViewUrl by remember { mutableStateOf("") }
    var webViewUrlError by remember { mutableStateOf<String?>(null) }
    val repository = remember { UnifiedCatalogueRepository() }
    val registryState by ProviderRegistry.state.collectAsState()
    val webViewEnabled = registryState.providers.any { it.value == "webview" && it.enabled }

    LaunchedEffect(Unit) {
        isLoading = true
        catalogueError = null
        try {
            val result = repository.getHomeFeed()
            items = result.items
            errors = result.errors
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            catalogueError = e.message ?: "Failed to load catalogue"
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "All Providers",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

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
                    isError = webViewUrlError != null
                )
                webViewUrlError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        val normalized = WebViewProvider.normalizeInputUrl(webViewUrl)
                        if (normalized == null) {
                            webViewUrlError = "Enter a valid website URL beginning with http:// or https://"
                        } else {
                            webViewUrlError = null
                            val host = runCatching { java.net.URI(normalized).host }.getOrNull().orEmpty()
                            onVideoClick(
                                ProviderCatalogueItem(
                                    providerId = "webview",
                                    providerName = "WebView",
                                    post = Post(title = host.ifBlank { normalized }, link = normalized, image = "")
                                )
                            )
                        }
                    },
                    enabled = webViewUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Extract and play")
                }
            }
        }

        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Loading...", modifier = Modifier.padding(16.dp))
            }
            catalogueError != null -> {
                Text("Error: $catalogueError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            items.isEmpty() && errors.isEmpty() -> {
                Text("No videos found", modifier = Modifier.padding(16.dp))
            }
            items.isEmpty() && errors.isNotEmpty() -> {
                Text("All catalogue providers failed", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                errors.forEach { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            else -> {
                if (errors.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Some providers failed (${errors.size})",
                            color = MaterialTheme.colorScheme.error
                        )
                        errors.forEach { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(items, key = { "${it.providerId}:${it.post.link}" }) { item ->
                        VideoCard(item = item, onClick = { onVideoClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    item: ProviderCatalogueItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            AsyncImage(
                model = item.post.image,
                contentDescription = item.post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = item.post.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = item.providerName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
