package com.easyadb.core.config

/**
 * cmdConfig.xml 结构的数据类。
 * 对应 res/config/cmdConfig.xml 中的 XML 结构
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
