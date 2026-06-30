package com.easyadb.core.log

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LogUtils {

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * 构建时间戳字符串，如 "2024-01-22 11:20:30.188: "
     * 对应 Python 中的 LogUtils.build_time_stamp()
     */
    fun buildTimeStamp(): String {
        return LocalDateTime.now().format(timestampFormat) + ": "
    }

    /**
     * 根据级别更改日志颜色（用于 HTML 渲染）。
     * 对应 Python 中的 LogUtils.changeLogColor()
     *
     * 颜色方案：
     *   ERROR  -> #bf360c（红色）
     *   WARN   -> #b07805（黄色）
     *   INFO   -> #263238（黑色）
     *   DEBUG  -> #388e3c（绿色）
     *   STAMP  -> #005ac7（蓝色，当 appendPrefix 为 true 时）
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
     * 用 HTML <a> 标签高亮文本中的 URL。
     * 对应 Python 中的 LogUtils.highlight_link_addr()
     */
    fun highlightLinkAddr(text: String): String {
        val urlPattern = Regex("""(https?://[^\s,;:，；：)）】\]}」』\)]+)""")
        return text.replace(urlPattern) { match ->
            val url = match.groupValues[1]
            "<a href=\"$url\" style=\"color: #005ac7;\">$url</a>"
        }
    }

    /**
     * 格式化带有颜色和时间戳的完整日志行。
     */
    fun formatLogLine(level: LogLevel, message: String, appendPrefix: Boolean = false): String {
        val stamp = buildTimeStamp()
        val colored = changeLogColor(appendPrefix, level, message)
        val withLinks = highlightLinkAddr(colored)
        return "$stamp$withLinks"
    }
}
