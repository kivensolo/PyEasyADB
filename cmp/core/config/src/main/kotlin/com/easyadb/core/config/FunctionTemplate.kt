package com.easyadb.core.config

/**
 * function_templates.xml 结构的数据类。
 * 对应 res/config/function_templates.xml 中的 XML 结构
 */
data class FunctionTemplate(
    val name: String,
    val layout: String = "grid",
    val items: List<FunctionItem> = emptyList()
)

data class FunctionItem(
    val icon: String = "",
    val text: String = "",
    val cmd: String = "",
    val action: String = "",
    val shell: Boolean = true,
    val needPkgName: Boolean = false,
    val needDeviceOnline: Boolean = false,
    val state: String = ""
)
