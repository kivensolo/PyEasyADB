package com.easyadb.ui.functions

/**
 * 构建 `am`（Activity Manager）ADB 命令字符串。
 *
 * 对应 Python `src/CenterWindow.py::build_am_cmd`（line 386-406）：
 * 将用户输入的包名、类路径、action、扩展参数拼接为 `am <name> [-a <action>] [-n <pkg>/<cls>] [extras]` 格式。
 */
object AmCommandBuilder {

    /**
     * 构建 `am` 命令。
     *
     * @param name 命令名称，如 "start"、"broadcast"、"force-stop" 等
     * @param packageName 目标应用包名
     * @param classPath 目标 Activity 完整类路径（可选）
     * @param action 自定义 Action 字符串（可选）
     * @param extendParams 扩展参数（可选），如 `--es "key1" "value1"`
     * @return 完整的 `am` 命令字符串（不含 `adb shell` 前缀，由调用方补充）
     */
    fun buildAmCmd(
        name: String,
        packageName: String,
        classPath: String = "",
        action: String = "",
        extendParams: String = ""
    ): String {
        val cmd = StringBuilder("am $name")

        if (action.isNotEmpty() || classPath.isNotEmpty()) {
            if (action.isNotEmpty()) {
                cmd.append(" -a $action")
            }
            if (classPath.isNotEmpty()) {
                cmd.append(" -n $packageName/$classPath")
            }
        } else {
            cmd.append(" $packageName")
        }

        if (extendParams.isNotEmpty()) {
            cmd.append(" $extendParams")
        }

        return cmd.toString()
    }
}