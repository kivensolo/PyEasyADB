package com.easyadb.core.config

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader

/**
 * 自定义 INI 解析器，支持 [Section] 标题和 key=value 键值对。
 * 对应 Python 中的 configparser（位于 AppConfigManager.py）
 *
 * P11 增加写回能力（[set] + [save]），供运行时设置（如主题）持久化：
 * - 段与键保持首次出现的顺序（LinkedHashMap），save 按该顺序整体重写；
 * - 重写不保留注释与空行（当前 AppConfig.ini 无注释，可接受）；
 * - [loadMerging] 支持在已有内容上叠加另一个文件的键值（用户配置覆盖种子，不清洗已有段）。
 */
class IniParser {

    private val sections: MutableMap<String, MutableMap<String, String>> = LinkedHashMap()

    /** 整体加载：清空已有内容后重新解析 */
    fun load(file: File) {
        sections.clear()
        loadMerging(file)
    }

    /** 叠加加载：保留已有内容，文件中的同名段/键覆盖旧值 */
    fun loadMerging(file: File) {
        BufferedReader(FileReader(file, Charsets.UTF_8)).use { reader ->
            parse(reader)
        }
    }

    /** 叠加加载（流式）：用于 classpath 种子配置 */
    fun parse(reader: Reader) {
        var currentSection = ""
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

    fun get(section: String, key: String): String? =
        sections[section]?.get(key)

    fun getOrDefault(section: String, key: String, default: String): String =
        sections[section]?.get(key) ?: default

    fun getSection(section: String): Map<String, String> =
        sections[section] ?: emptyMap()

    /** 设置键值，段不存在时自动创建 */
    fun set(section: String, key: String, value: String) {
        sections.getOrPut(section) { LinkedHashMap() }[key] = value
    }

    /** 全量写回；父目录不存在时自动创建 */
    fun save(file: File) {
        val sb = StringBuilder()
        for ((section, kv) in sections) {
            sb.append('[').append(section).append(']').append('\n')
            for ((key, value) in kv) {
                sb.append(key).append('=').append(value).append('\n')
            }
            sb.append('\n')
        }
        file.parentFile?.mkdirs()
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    companion object {
        private val SECTION_REGEX = Regex("""^\[(.+)]$""")
        private val KV_REGEX = Regex("""^([^=]+)=(.*)$""")
    }
}
