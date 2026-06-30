package com.easyadb.core.config

/**
 * Data classes for menus_ui.xml structure.
 * Maps to XML in res/config/menus_ui.xml
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
