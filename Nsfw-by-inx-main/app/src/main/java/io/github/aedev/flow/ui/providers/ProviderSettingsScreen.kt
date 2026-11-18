package io.github.aedev.flow.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.providers.Provider
import io.github.aedev.flow.providers.ProviderRegistry

@Composable
fun ProviderSettingsScreen() {
    val providers = remember { ProviderRegistry.getAll() }
    val registryState by ProviderRegistry.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Provider Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn {
            items(providers, key = { it.value }) { provider ->
                val isEnabled = registryState.providers.firstOrNull { it.value == provider.value }?.enabled == true
                ProviderToggleItem(
                    provider = provider,
                    isEnabled = isEnabled,
                    onToggle = { enabled -> ProviderRegistry.setEnabled(provider.value, enabled) }
                )
            }
        }
    }
}

@Composable
fun ProviderToggleItem(
    provider: Provider,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = provider.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle
        )
    }
}
