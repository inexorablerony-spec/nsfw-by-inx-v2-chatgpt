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
import io.github.aedev.flow.providers.Provider
import io.github.aedev.flow.providers.UnifiedCatalogueRepository
import kotlinx.coroutines.CancellationException

@Composable
fun SearchScreen(
    provider: Provider,
    onVideoClick: (Post) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val repository = remember { UnifiedCatalogueRepository() }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                error = null
            },
            label = { Text("Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Button(
            onClick = {
                isLoading = true
                error = null
                posts = emptyList()
            },
            enabled = searchQuery.isNotBlank() && !isLoading,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text("Search")
        }

        LaunchedEffect(isLoading) {
            if (!isLoading) return@LaunchedEffect
            try {
                posts = repository.searchProvider(provider.value, searchQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
            } finally {
                isLoading = false
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (!isLoading && error == null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(posts, key = { it.link }) { post ->
                    Card(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth(),
                        onClick = { onVideoClick(post) }
                    ) {
                        Column {
                            AsyncImage(
                                model = post.image,
                                contentDescription = post.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
