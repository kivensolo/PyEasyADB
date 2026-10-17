package com.easyadb.core.log

enum class LogLevel(val value: Int, val shortName: String) {
    TRACE(0, "V"),
    DEBUG(1, "D"),
    INFO(2, "I"),
    WARN(3, "W"),
    ERROR(4, "E"),
    OFF(5, "-");

    companion object {
        fun fromShortName(name: String): LogLevel =
            entries.find { it.shortName == name } ?: INFO

        fun fromName(name: String): LogLevel =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: INFO
    }
}
