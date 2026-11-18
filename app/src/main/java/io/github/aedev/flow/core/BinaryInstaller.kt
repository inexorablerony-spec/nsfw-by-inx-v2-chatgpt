package io.github.aedev.flow.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BinaryInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val BINARY_COMMIT = "6ff2f7a9f076b923014bff3059a34471258af6e3"
        const val BINARY_URL = "https://raw.githubusercontent.com/Vashiel/repository.adulthideout/$BINARY_COMMIT/core-binaries/android-arm64-v8a/libadulthideout_core.so"
        const val EXPECTED_SHA256 = "da6b7a9c8d698a88ccc09d8bd03ff925c993c0f4e6b9e2e012d0985a356f37b6"
        const val MAX_SIZE = 10 * 1024 * 1024L
        const val CONNECT_TIMEOUT = 30000
        const val READ_TIMEOUT = 60000
        val ELF_MAGIC = byteArrayOf(0x7f, 0x45, 0x4c, 0x46)
        const val ELF_MACHINE_AARCH64 = 183
    }

    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        if (!isArm64()) return@withContext false

        val finalFile = File(context.filesDir, "libadulthideout_core.so")
        val backupFile = File(context.filesDir, "libadulthideout_core.so.bak")

        // Check existing installation
        if (finalFile.exists()) {
            if (verifyFile(finalFile)) return@withContext true
            // Invalid existing - backup it
            if (!finalFile.renameTo(backupFile)) {
                finalFile.delete()
            }
        }

        val tempFile = File(context.filesDir, "libadulthideout_core.so.tmp")
        var conn: HttpURLConnection? = null
        try {
            conn = URL(BINARY_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.instanceFollowRedirects = false
            conn.connect()

            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }

            val contentLength = conn.contentLengthLong
            if (contentLength > MAX_SIZE) {
                throw IllegalStateException("Content-Length exceeds limit")
            }

            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        total += read
                        if (total > MAX_SIZE) throw IllegalStateException("Exceeded size limit")
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (!verifyFile(tempFile)) {
                tempFile.delete()
                restoreBackup(backupFile, finalFile)
                return@withContext false
            }

            // Atomic rename
            if (!tempFile.renameTo(finalFile)) {
                // Fallback: copy then delete
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
                if (!verifyFile(finalFile)) {
                    finalFile.delete()
                    restoreBackup(backupFile, finalFile)
                    return@withContext false
                }
            }

            // Only delete backup after successful install and verify
            if (verifyFile(finalFile)) {
                backupFile.delete()
            }
            true
        } catch (e: CancellationException) {
            tempFile.delete()
            restoreBackup(backupFile, finalFile)
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            restoreBackup(backupFile, finalFile)
            false
        } finally {
            conn?.disconnect()
        }
    }

    fun isInstalled(): Boolean {
        val file = File(context.filesDir, "libadulthideout_core.so")
        return file.exists() && verifyFile(file)
    }

    fun verifyFile(file: File): Boolean {
        if (!file.exists()) return false
        if (file.length() > MAX_SIZE) return false
        if (file.length() < 100000) return false
        if (!verifyElfArch(file)) return false
        return verifyChecksum(file)
    }

    private fun isArm64(): Boolean = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    private fun verifyElfArch(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.read(magic)
                if (!magic.contentEquals(ELF_MAGIC)) return false
                raf.seek(18)
                val machine = raf.readShort().toInt() and 0xFFFF
                machine == ELF_MACHINE_AARCH64
            }
        } catch (e: Exception) {
            false
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

    private fun restoreBackup(backup: File, target: File) {
        if (backup.exists() && !target.exists()) {
            backup.renameTo(target)
        }
    }
}
