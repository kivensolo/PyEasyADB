package com.easyadb.core.config

/**
 * Data classes for function_templates.xml structure.
 * Maps to XML in res/config/function_templates.xml
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
