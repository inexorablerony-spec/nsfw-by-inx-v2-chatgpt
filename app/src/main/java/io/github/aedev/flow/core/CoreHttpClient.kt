package io.github.aedev.flow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class CoreHttpClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "http://127.0.0.1:8191"

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$baseUrl/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resolve(request: CoreResolveRequest): Result<CoreResolveResult> = withContext(Dispatchers.IO) {
        try {
            if (request.urls.any { !UrlValidator.isAllowed(it) }) {
                return@withContext Result.failure(IllegalArgumentException("URL not allowed"))
            }
            val conn = URL("$baseUrl/resolve").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(json.encodeToString(request).toByteArray()) }
            
            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
            val body = conn.inputStream.bufferedReader().readText()
            Result.success(json.decodeFromString<CoreResolveResult>(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(request: CoreSearchRequest): Result<CoreSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$baseUrl/search").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(json.encodeToString(request).toByteArray()) }
            
            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
            val body = conn.inputStream.bufferedReader().readText()
            Result.success(json.decodeFromString<CoreSearchResponse>(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
