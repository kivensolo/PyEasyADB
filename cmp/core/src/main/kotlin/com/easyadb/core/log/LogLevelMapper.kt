package com.easyadb.core.log

object LogLevelMapper {
    val simpleNameToLevel: Map<String, LogLevel> = mapOf(
        "V" to LogLevel.TRACE,
        "D" to LogLevel.DEBUG,
        "I" to LogLevel.INFO,
        "W" to LogLevel.WARN,
        "E" to LogLevel.ERROR,
        "A" to LogLevel.ERROR
    )

    val nameToLevel: Map<String, LogLevel> = mapOf(
        "Verbose" to LogLevel.TRACE,
        "Debug" to LogLevel.DEBUG,
        "Info" to LogLevel.INFO,
        "Warn" to LogLevel.WARN,
        "Error" to LogLevel.ERROR,
        "Assert" to LogLevel.ERROR
    )

    val filterOptions: List<String> = listOf(
        "No Filter",
        "Show only selected application"
    )
}
