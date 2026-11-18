package io.github.aedev.flow.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdultHideoutCoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CoreState.STOPPED)
    val state: StateFlow<CoreState> = _state.asStateFlow()

    suspend fun ensureRunning(timeoutMs: Long = 5000): Boolean = mutex.withLock {
        if (_state.value == CoreState.RUNNING) return true
        if (_state.value == CoreState.STARTING) return false

        _state.value = CoreState.STARTING
        return withContext(Dispatchers.IO) {
            try {
                val binaryPath = getBinaryPath() ?: return@withContext fail()
                
                val loadResult = CoreNative.nativeLoad(binaryPath)
                if (loadResult != 0) return@withContext fail()

                val startResult = CoreNative.nativeStart(8191)
                if (startResult != 0) return@withContext fail()

                val healthy = withTimeoutOrNull(timeoutMs) {
                    repeat(25) {
                        if (checkHealth()) return@withTimeoutOrNull true
                        delay(200)
                    }
                    false
                } ?: false

                if (!healthy) {
                    CoreNative.nativeStop()
                    return@withContext fail()
                }

                _state.value = CoreState.RUNNING
                true
            } catch (e: Exception) {
                CoreNative.nativeStop()
                fail()
            }
        }
    }

    fun shutdown() {
        if (_state.value == CoreState.RUNNING) {
            CoreNative.nativeStop()
            _state.value = CoreState.STOPPED
        }
    }

    private fun getBinaryPath(): String? {
        val file = File(context.filesDir, "libadulthideout_core.so")
        return if (file.exists() && file.length() > 100000) file.absolutePath else null
    }

    private fun checkHealth(): Boolean {
        return try {
            val url = java.net.URL("http://127.0.0.1:8191/health")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 500
            conn.readTimeout = 500
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    private fun fail(): Boolean {
        _state.value = CoreState.FAILED
        return false
    }
}
