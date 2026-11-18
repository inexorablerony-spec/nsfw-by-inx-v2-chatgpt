package io.github.aedev.flow.ui.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.providers.Provider
import io.github.aedev.flow.providers.ProviderRegistry

@Composable
fun ProviderListScreen(
    onProviderClick: (Provider) -> Unit
) {
    val registryState by ProviderRegistry.state.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(registryState.providers, key = { it.value }) { info ->
            ProviderItem(
                info = info,
                onClick = { 
                    ProviderRegistry.getByValue(info.value)?.let { onProviderClick(it) }
                }
            )
        }
    }
}

@Composable
fun ProviderItem(
    info: io.github.aedev.flow.providers.ProviderInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(info.name, style = MaterialTheme.typography.titleMedium)
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
