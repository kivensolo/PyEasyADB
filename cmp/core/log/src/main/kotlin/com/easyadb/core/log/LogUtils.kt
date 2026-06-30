package com.easyadb.core.log

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LogUtils {

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * Build timestamp string like "2024-01-22 11:20:30.188: "
     * Maps to Python LogUtils.build_time_stamp()
     */
    fun buildTimeStamp(): String {
        return LocalDateTime.now().format(timestampFormat) + ": "
    }

    /**
     * Change log color based on level (for HTML rendering).
     * Maps to Python LogUtils.changeLogColor()
     *
     * Color scheme:
     *   ERROR  -> #bf360c (red)
     *   WARN   -> #b07805 (yellow)
     *   INFO   -> #263238 (black)
     *   DEBUG  -> #388e3c (green)
     *   STAMP  -> #005ac7 (blue, when appendPrefix is true)
     */
    fun changeLogColor(appendPrefix: Boolean, level: LogLevel, log: String): String {
        val color = when (level) {
            LogLevel.ERROR -> "#bf360c"
            LogLevel.WARN -> "#b07805"
            LogLevel.INFO -> if (appendPrefix) "#005ac7" else "#263238"
            LogLevel.DEBUG -> "#388e3c"
            else -> "#263238"
        }
        return "<font color=\"$color\">$log</font>"
    }

    /**
     * Highlight URLs in text with HTML <a> tags.
     * Maps to Python LogUtils.highlight_link_addr()
     */
    fun highlightLinkAddr(text: String): String {
        val urlPattern = Regex("""(https?://[^\s,;:，；：)）】\]}」』\)]+)""")
        return text.replace(urlPattern) { match ->
            val url = match.groupValues[1]
            "<a href=\"$url\" style=\"color: #005ac7;\">$url</a>"
        }
    }

    /**
     * Format a complete log line with color and timestamp.
     */
    fun formatLogLine(level: LogLevel, message: String, appendPrefix: Boolean = false): String {
        val stamp = buildTimeStamp()
        val colored = changeLogColor(appendPrefix, level, message)
        val withLinks = highlightLinkAddr(colored)
        return "$stamp$withLinks"
    }
}
