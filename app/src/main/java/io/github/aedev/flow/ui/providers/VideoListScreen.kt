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
fun VideoListScreen(
    provider: Provider,
    onVideoClick: (Post) -> Unit
) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val repository = remember { UnifiedCatalogueRepository() }

    LaunchedEffect(provider.value) {
        isLoading = true
        error = null
        try {
            posts = repository.getProviderPosts(provider.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load provider catalogue"
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = provider.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        } else if (posts.isEmpty()) {
            Text("No videos found", modifier = Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(posts, key = { it.link }) { post ->
                    VideoItem(post = post, onClick = { onVideoClick(post) })
                }
            }
        }
    }
}

@Composable
fun VideoItem(
    post: Post,
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
