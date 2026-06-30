package com.easyadb.core.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileUtils {

    /**
     * Calculate MD5 hash of a file.
     * Maps to Python FileUtils.calculate_md5() in utils/Utils.py
     */
    fun calculateMd5(file: File, chunkSize: Int = 4096): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(chunkSize)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Recursively delete directory contents.
     * Maps to Python FileUtils.clear_directory() in utils/Utils.py
     */
    fun clearDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
            else if (file.isDirectory) file.deleteRecursively()
        }
    }
}
