package io.github.aedev.flow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.network.WarpProxyManager
import kotlinx.coroutines.launch

@Composable
fun WarpModeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by WarpProxyManager.state.collectAsState()
    val errorMessage by WarpProxyManager.errorMessage.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Network",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cloudflare WARP",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (state) {
                                WarpProxyManager.State.OFF -> "OFF"
                                WarpProxyManager.State.STARTING -> "STARTING..."
                                WarpProxyManager.State.CONNECTED -> "CONNECTED"
                                WarpProxyManager.State.STOPPING -> "STOPPING..."
                                WarpProxyManager.State.ERROR -> "ERROR"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state) {
                                WarpProxyManager.State.CONNECTED -> MaterialTheme.colorScheme.primary
                                WarpProxyManager.State.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Switch(
                        checked = state == WarpProxyManager.State.CONNECTED,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                if (enabled) {
                                    WarpProxyManager.start(context)
                                } else {
                                    WarpProxyManager.stop()
                                }
                            }
                        },
                        enabled = state != WarpProxyManager.State.STARTING && state != WarpProxyManager.State.STOPPING
                    )
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "This is an app-level proxy, not a system VPN. " +
                    "Connection is verified against Cloudflare before showing CONNECTED. " +
                    "Requires libusque.so.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
