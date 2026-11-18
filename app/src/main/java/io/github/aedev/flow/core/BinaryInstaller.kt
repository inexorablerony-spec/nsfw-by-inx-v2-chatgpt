package io.github.aedev.flow.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
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
        // Pinned to specific commit, not master
        const val BINARY_COMMIT = "a1b2c3d4e5f6" // TODO: Replace with actual commit SHA
        const val BINARY_URL = "https://raw.githubusercontent.com/Vashiel/repository.adulthideout/$BINARY_COMMIT/core-binaries/android-arm64-v8a/libadulthideout_core.so"
        const val EXPECTED_SHA256 = "c4a8a8d1e046cab6a42934b8fba31246efb21a002a90beb2bffa555623a383a9"
        const val MAX_SIZE = 10 * 1024 * 1024
        const val CONNECT_TIMEOUT = 30000
        const val READ_TIMEOUT = 60000
        // ELF magic and ARM64 machine type
        val ELF_MAGIC = byteArrayOf(0x7f, 0x45, 0x4c, 0x46)
        const val ELF_MACHINE_AARCH64 = 183
    }

    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        if (!isArm64()) return@withContext false

        val finalFile = File(context.filesDir, "libadulthideout_core.so")
        val backupFile = File(context.filesDir, "libadulthideout_core.so.bak")

        // Always verify existing file
        if (finalFile.exists()) {
            if (verifyFile(finalFile)) return@withContext true
            // Invalid existing file - backup and re-download
            finalFile.renameTo(backupFile)
        }

        val tempFile = File(context.filesDir, "libadulthideout_core.so.tmp")
        try {
            download(tempFile)
            if (!verifyFile(tempFile)) {
                tempFile.delete()
                restoreBackup(backupFile, finalFile)
                return@withContext false
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                restoreBackup(backupFile, finalFile)
                return@withContext false
            }
            backupFile.delete()
            true
        } catch (e: Exception) {
            tempFile.delete()
            restoreBackup(backupFile, finalFile)
            false
        }
    }

    fun isInstalled(): Boolean {
        val file = File(context.filesDir, "libadulthideout_core.so")
        return file.exists() && verifyFile(file)
    }

    private fun isArm64(): Boolean = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    private fun download(target: File) {
        val conn = URL(BINARY_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
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
        conn.disconnect()
    }

    private fun verifyFile(file: File): Boolean {
        if (!file.exists() || file.length() < 100000) return false
        if (!verifyElfArch(file)) return false
        return verifyChecksum(file)
    }

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
        if (backup.exists()) backup.renameTo(target)
    }
}
