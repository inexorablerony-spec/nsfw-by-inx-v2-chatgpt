package io.github.aedev.flow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.providers.ProviderRegistry

@Composable
fun ProviderManagerScreen(
    onOpenCatalogue: () -> Unit = {}
) {
    val registryState by ProviderRegistry.state.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Providers",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        Button(
            onClick = onOpenCatalogue,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Open Provider Catalogue")
        }
        
        LazyColumn {
            items(registryState.providers) { info ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(info.name, style = MaterialTheme.typography.bodyLarge)
                        Text(info.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = info.enabled,
                        onCheckedChange = { enabled ->
                            ProviderRegistry.setEnabled(info.value, enabled)
                        }
                    )
                }
            }
        }
    }
}
