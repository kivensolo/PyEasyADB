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
    val state: String = "",
    /** 内容区 Tab 归属（P12 布局大改）：quick=快捷操作 / keys=按键模拟 / files=文件传输，缺省 quick */
    val tab: String = "quick",
    /** 卡片副文案（大卡显示在标题下方，说明功能用途） */
    val desc: String = "",
    /** 卡片尺寸：large=大卡（图左文右 + 副文案，文件传输类含设备路径输入行）/ small=小卡，缺省 small */
    val size: String = "small",
    /** 卡片图标语义色（快捷操作 Tab）：green/red/blue/orange/grey/sky/rose，空=不着色 */
    val accent: String = ""
)
