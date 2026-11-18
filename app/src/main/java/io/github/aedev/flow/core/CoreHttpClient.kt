package io.github.aedev.flow.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class CoreHttpClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "http://127.0.0.1:8191"
    private val maxResponseSize = 10 * 1024 * 1024

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL("$baseUrl/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.instanceFollowRedirects = false
            conn.responseCode == 200
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun resolve(request: CoreResolveRequest): Result<CoreResolveResult> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (request.urls.any { !UrlValidator.isAllowed(it) }) {
                return@withContext Result.failure(IllegalArgumentException("URL not allowed"))
            }
            conn = URL("$baseUrl/resolve").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.outputStream.use { it.write(json.encodeToString(request).toByteArray(StandardCharsets.UTF_8)) }

            if (conn.responseCode != 200) {
                val errorBody = readErrorBody(conn)
                return@withContext Result.failure(CoreHttpException(conn.responseCode, errorBody))
            }
            val body = readBounded(conn)
            Result.success(json.decodeFromString<CoreResolveResult>(body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun search(request: CoreSearchRequest): Result<CoreSearchResponse> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            // Validate any URLs in sources
            if (request.sources.any { it.startsWith("http") && !UrlValidator.isAllowed(it) }) {
                return@withContext Result.failure(IllegalArgumentException("Source URL not allowed"))
            }
            conn = URL("$baseUrl/search").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.outputStream.use { it.write(json.encodeToString(request).toByteArray(StandardCharsets.UTF_8)) }

            if (conn.responseCode != 200) {
                val errorBody = readErrorBody(conn)
                return@withContext Result.failure(CoreHttpException(conn.responseCode, errorBody))
            }
            val body = readBounded(conn)
            Result.success(json.decodeFromString<CoreSearchResponse>(body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun readBounded(conn: HttpURLConnection): String {
        val stream = conn.inputStream
        val buffer = ByteArray(8192)
        val result = StringBuilder()
        var total = 0
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            total += read
            if (total > maxResponseSize) throw IllegalStateException("Response too large")
            result.append(String(buffer, 0, read, StandardCharsets.UTF_8))
        }
        return result.toString()
    }

    private fun readErrorBody(conn: HttpURLConnection): String {
        return try {
            val stream = conn.errorStream ?: return ""
            val buffer = ByteArray(4096)
            val result = StringBuilder()
            var total = 0
            var read: Int
            while (stream.read(buffer).also { read = it } != -1 && total < 4096) {
                total += read
                result.append(String(buffer, 0, read, StandardCharsets.UTF_8))
            }
            result.toString()
        } catch (e: Exception) {
            ""
        }
    }
}

class CoreHttpException(val statusCode: Int, val errorBody: String) : Exception("HTTP $statusCode: $errorBody")
