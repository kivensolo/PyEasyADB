package com.easyadb.core.config

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Custom INI parser supporting [Section] headers and key=value pairs.
 * Maps to Python configparser in AppConfigManager.py
 */
class IniParser {

    private val sections: MutableMap<String, MutableMap<String, String>> = LinkedHashMap()

    fun load(file: File) {
        sections.clear()
        var currentSection = ""
        BufferedReader(FileReader(file, Charsets.UTF_8)).use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) return@forEachLine

                val sectionMatch = SECTION_REGEX.find(trimmed)
                if (sectionMatch != null) {
                    currentSection = sectionMatch.groupValues[1]
                    sections.putIfAbsent(currentSection, LinkedHashMap())
                    return@forEachLine
                }

                val kvMatch = KV_REGEX.find(trimmed)
                if (kvMatch != null) {
                    val key = kvMatch.groupValues[1].trim()
                    val value = kvMatch.groupValues[2].trim()
                    sections.getOrPut(currentSection) { LinkedHashMap() }[key] = value
                }
            }
        }
    }

    fun get(section: String, key: String): String? =
        sections[section]?.get(key)

    fun getOrDefault(section: String, key: String, default: String): String =
        sections[section]?.get(key) ?: default

    fun getSection(section: String): Map<String, String> =
        sections[section] ?: emptyMap()

    companion object {
        private val SECTION_REGEX = Regex("""^\[(.+)]$""")
        private val KV_REGEX = Regex("""^([^=]+)=(.*)$""")
    }
}
