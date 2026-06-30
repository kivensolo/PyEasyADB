package com.easyadb.core.config

/**
 * Data classes for cmdConfig.xml structure.
 * Maps to XML in res/config/cmdConfig.xml
 */
data class CmdGroup(
    val name: String,
    val items: List<CmdItem> = emptyList(),
    val subGroups: List<CmdSubGroup> = emptyList()
)

data class CmdItem(
    val name: String,
    val cmd: String = "",
    val shell: Boolean = true,
    val needDstPkg: Boolean = false,
    val description: String = ""
)

data class CmdSubGroup(
    val name: String,
    val items: List<CmdItem> = emptyList()
)
