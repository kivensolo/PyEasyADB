package com.easyadb.core.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileUtils {

    /**
     * 计算文件的 MD5 哈希值。
     * 对应 Python 中的 FileUtils.calculate_md5()（位于 utils/Utils.py）
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
     * 递归删除目录内容。
     * 对应 Python 中的 FileUtils.clear_directory()（位于 utils/Utils.py）
     */
    fun clearDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
            else if (file.isDirectory) file.deleteRecursively()
        }
    }
}
