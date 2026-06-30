package com.easyadb.core.config

/**
 * menus_ui.xml 结构的数据类。
 * 对应 res/config/menus_ui.xml 中的 XML 结构
 */
data class MenuConfig(
    val name: String,
    val actions: List<MenuAction> = emptyList()
)

data class MenuAction(
    val name: String,
    val shortcut: String = "",
    val icon: String = "",
    val action: String = "",
    val state: String = ""
)
