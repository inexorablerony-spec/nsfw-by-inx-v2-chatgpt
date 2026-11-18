package io.github.aedev.flow.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BinaryInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val BINARY_URL = "https://raw.githubusercontent.com/Vashiel/repository.adulthideout/master/core-binaries/android-arm64-v8a/libadulthideout_core.so"
        const val EXPECTED_SHA256 = "c4a8a8d1e046cab6a42934b8fba31246efb21a002a90beb2bffa555623a383a9"
        const val MAX_SIZE = 10 * 1024 * 1024 // 10 MB
    }

    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        if (!isArm64()) return@withContext false

        val finalFile = File(context.filesDir, "libadulthideout_core.so")
        if (finalFile.exists() && verifyChecksum(finalFile)) {
            return@withContext true
        }

        val tempFile = File(context.filesDir, "libadulthideout_core.so.tmp")
        try {
            download(tempFile)
            if (!verifyChecksum(tempFile)) {
                tempFile.delete()
                return@withContext false
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                return@withContext false
            }
            true
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    fun isInstalled(): Boolean {
        val file = File(context.filesDir, "libadulthideout_core.so")
        return file.exists() && file.length() > 100000
    }

    private fun isArm64(): Boolean {
        return Build.SUPPORTED_ABIS.contains("arm64-v8a")
    }

    private fun download(target: File) {
        URL(BINARY_URL).openStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    total += read
                    if (total > MAX_SIZE) throw IllegalStateException("Download exceeded size limit")
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun verifyChecksum(file: File): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual == EXPECTED_SHA256
    }
}
