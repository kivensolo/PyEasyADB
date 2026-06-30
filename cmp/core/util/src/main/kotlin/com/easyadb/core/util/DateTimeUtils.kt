package com.easyadb.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateTimeUtils {

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun buildTimeStamp(): String = LocalDateTime.now().format(timestampFormat)

    fun formatDate(pattern: String): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern))

    fun todayDate(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
