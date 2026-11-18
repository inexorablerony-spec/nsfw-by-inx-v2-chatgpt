package io.github.aedev.flow.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * WARP Proxy Manager for Cloudflare WARP integration.
 * 
 * Based on Vega's WarpModule implementation.
 * 
 * IMPORTANT: This is an app-level proxy, not a system VPN.
 * Connection is only reported after Cloudflare trace confirms "warp=on".
 * 
 * NOTE: Requires libusque.so native binary (arm64-v8a only).
 * CLI syntax and registration must be verified against usque source.
 */
object WarpProxyManager {
    private const val TAG = "WarpProxyManager"
    private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
    private const val MAX_RETRIES = 10
    private const val RETRY_DELAY_MS = 500L
    private const val SHUTDOWN_TIMEOUT_MS = 3000L
    private const val MAX_LOG_LINES = 100
    private const val MAX_PORT_RETRIES = 5
    
    private val lock = Any()
    private var warpProcess: Process? = null
    private var currentPort: Int? = null
    private var startJob: Job? = null
    private var outputJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processLogs = mutableListOf<String>()
    private val startupGeneration = AtomicLong(0)
    
    enum class State {
        OFF,
        STARTING,
        CONNECTED,
        STOPPING,
        ERROR
    }
    
    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> = _state
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    fun getState(): State = _state.value
    fun getErrorMessage(): String? = _errorMessage.value
    fun isConnected(): Boolean = _state.value == State.CONNECTED
    fun getPort(): Int? = currentPort
    fun getLogs(): List<String> = processLogs.toList()
    
    /**
     * Start WARP proxy.
     * Returns true only after Cloudflare trace confirms "warp=on".
     */
    suspend fun start(context: Context): Boolean = withContext(Dispatchers.IO) {
        val generation: Long
        val shouldStart = synchronized(lock) {
            if (_state.value == State.CONNECTED || _state.value == State.STARTING) {
                false
            } else {
                generation = startupGeneration.incrementAndGet()
                _state.value = State.STARTING
                _errorMessage.value = null
                true
            }
        }
        
        if (!shouldStart) return@withContext _state.value == State.CONNECTED
        
        startJob = coroutineContext[Job]
        var proc: Process? = null
        
        try {
            val binary = getBinaryFile(context)
            if (!binary.exists()) {
                setErrorIfCurrent(generation, "libusque.so not found. Build from usque source for arm64-v8a.")
                return@withContext false
            }
            
            val port = allocatePort()
            if (port == null) {
                setErrorIfCurrent(generation, "Failed to allocate port after $MAX_PORT_RETRIES retries")
                return@withContext false
            }
            
            val configDir = File(context.filesDir, "warp")
            configDir.mkdirs()
            val configPath = File(configDir, "config.json").absolutePath
            
            if (!File(configPath).exists()) {
                if (!registerAccount(binary, configPath)) {
                    setErrorIfCurrent(generation, "Registration failed")
                    return@withContext false
                }
            }
            
            proc = launchProxy(binary, port, configPath)
            if (proc == null) {
                setErrorIfCurrent(generation, "Failed to start proxy process")
                return@withContext false
            }
            
            // Store process atomically if still current
            val stored = synchronized(lock) {
                if (startupGeneration.get() == generation && _state.value == State.STARTING) {
                    warpProcess = proc
                    currentPort = port
                    true
                } else {
                    false
                }
            }
            
            if (!stored) {
                cleanupProcess(proc)
                return@withContext false
            }
            
            // Start output drain
            startOutputDrain(proc, generation)
            
            // Check if cancelled
            if (!isActive || !isGenerationCurrent(generation)) {
                cleanupProcess(proc)
                return@withContext false
            }
            
            if (!verifyConnection(port, proc)) {
                clearProcessIfCurrent(proc, generation)
                cleanupProcess(proc)
                setErrorIfCurrent(generation, "Connection verification failed")
                return@withContext false
            }
            
            // Check if cancelled before setting CONNECTED
            if (!isActive) {
                clearProcessIfCurrent(proc, generation)
                cleanupProcess(proc)
                return@withContext false
            }
            
            val connected = synchronized(lock) {
                if (startupGeneration.get() == generation && _state.value == State.STARTING) {
                    _state.value = State.CONNECTED
                    true
                } else {
                    false
                }
            }
            
            if (!connected) {
                cleanupProcess(proc)
                return@withContext false
            }
            
            // Monitor process
            val monitoredProc = proc
            scope.launch {
                try {
                    monitoredProc.waitFor()
                } catch (_: Exception) {}
                synchronized(lock) {
                    if (warpProcess == monitoredProc) {
                        Log.i(TAG, "WARP process terminated unexpectedly")
                        warpProcess = null
                        currentPort = null
                        _state.value = State.ERROR
                        _errorMessage.value = "Process terminated unexpectedly"
                    }
                }
            }
            
            Log.i(TAG, "WARP connected on port $port")
            true
        } catch (e: CancellationException) {
            cleanupProcess(proc)
            synchronized(lock) {
                if (startupGeneration.get() == generation) {
                    _state.value = State.OFF
                }
            }
            false
        } catch (e: Exception) {
            cleanupProcess(proc)
            setErrorIfCurrent(generation, e.message ?: "Unknown error")
            false
        }
    }
    
    /**
     * Stop WARP proxy.
     */
    fun stop() {
        val proc: Process?
        synchronized(lock) {
            if (_state.value == State.OFF) return
            
            startupGeneration.incrementAndGet()
            startJob?.cancel()
            startJob = null
            
            _state.value = State.STOPPING
            
            proc = warpProcess
            warpProcess = null
            currentPort = null
        }
        
        if (proc != null) {
            cleanupProcess(proc)
        }
        
        synchronized(lock) {
            if (_state.value == State.STOPPING) {
                _state.value = State.OFF
                _errorMessage.value = null
            }
        }
        Log.i(TAG, "WARP stopped")
    }
    
    private fun isGenerationCurrent(generation: Long): Boolean {
        return synchronized(lock) { startupGeneration.get() == generation }
    }
    
    private fun setErrorIfCurrent(generation: Long, message: String) {
        synchronized(lock) {
            if (startupGeneration.get() == generation) {
                _state.value = State.ERROR
                _errorMessage.value = message
                Log.e(TAG, message)
            }
        }
    }
    
    private fun clearProcessIfCurrent(proc: Process, generation: Long) {
        synchronized(lock) {
            if (warpProcess == proc && startupGeneration.get() == generation) {
                warpProcess = null
                currentPort = null
                outputJob?.cancel()
                outputJob = null
            }
        }
    }
    
    private fun cleanupProcess(proc: Process?) {
        proc ?: return
        
        proc.destroy()
        try {
            if (!proc.waitFor(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                proc.waitFor(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        } catch (_: Exception) {}
        
        try {
            proc.inputStream.close()
            proc.outputStream.close()
            proc.errorStream.close()
        } catch (_: Exception) {}
    }
    
    private fun startOutputDrain(proc: Process, generation: Long) {
        val job = scope.launch {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (isActive && reader.readLine().also { line = it } != null) {
                        line?.let { log ->
                            synchronized(processLogs) {
                                processLogs.add(log)
                                if (processLogs.size > MAX_LOG_LINES) {
                                    processLogs.removeAt(0)
                                }
                            }
                            Log.d(TAG, "usque: $log")
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        
        synchronized(lock) {
            if (startupGeneration.get() == generation) {
                outputJob?.cancel()
                outputJob = job
            } else {
                job.cancel()
            }
        }
    }
    
    private fun getBinaryFile(context: Context): File {
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libusque.so")
        if (nativeLib.exists()) return nativeLib
        
        val fallback = File(context.filesDir, "libusque.so")
        if (fallback.exists()) return fallback
        
        return nativeLib
    }
    
    private fun allocatePort(): Int? {
        // Try to find an available port with retries
        repeat(MAX_PORT_RETRIES) { attempt ->
            try {
                java.net.ServerSocket(0).use { socket ->
                    val port = socket.localPort
                    // Try to bind to verify it's available
                    java.net.ServerSocket(port).use { 
                        return port 
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Port allocation attempt ${attempt + 1} failed: ${e.message}")
            }
        }
        Log.e(TAG, "Failed to allocate port after $MAX_PORT_RETRIES retries")
        return null
    }
    
    private fun launchProxy(binary: File, port: Int, configPath: String): Process? {
        return try {
            // Verified against usque commit 6aa03fc97d12848dce34eedbd187fb1077b5d1ea
            // Command: usque -c CONFIG http-proxy -b 127.0.0.1 -p PORT
            Log.i(TAG, "Launching usque http-proxy on port $port")
            ProcessBuilder(
                binary.absolutePath,
                "-c", configPath,
                "http-proxy",
                "-b", "127.0.0.1",
                "-p", port.toString()
            )
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch proxy: ${e.message}")
            null
        }
    }
    
    private fun registerAccount(binary: File, configPath: String): Boolean {
        return try {
            Log.i(TAG, "Registering WARP account")
            val proc = ProcessBuilder(
                binary.absolutePath,
                "-c", configPath,
                "register",
                "--accept-tos"
            )
                .redirectErrorStream(true)
                .start()
            
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor(30, TimeUnit.SECONDS)
            
            if (!exitCode) {
                proc.destroyForcibly()
                Log.e(TAG, "Registration timed out")
                return false
            }
            
            val configFile = File(configPath)
            if (proc.exitValue() == 0 && configFile.exists() && configFile.length() > 0) {
                Log.i(TAG, "Registration successful")
                true
            } else {
                Log.e(TAG, "Registration failed: exit=\${proc.exitValue()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error: \${e.message}")
            false
        }
    }
    
    private suspend fun verifyConnection(port: Int, proc: Process): Boolean = withContext(Dispatchers.IO) {
        repeat(MAX_RETRIES) { attempt ->
            if (!isActive || !proc.isAlive) {
                Log.e(TAG, "Process exited or cancelled during verification")
                return@withContext false
            }
            
            try {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
                val url = URL(TRACE_URL)
                val conn = url.openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                try {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    if (response.contains("warp=on")) {
                        Log.i(TAG, "Verification successful")
                        return@withContext true
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Verification attempt ${attempt + 1} failed: ${e.message}")
            }
            
            delay(RETRY_DELAY_MS)
        }
        false
    }
}
