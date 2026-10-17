package com.easyadb.core.adb

/**
 * ADB 命令动作参数。
 * 对应 Python 中的 ActionCmdParams（位于 utils/ADBTools.py）
 */
class ActionCmdParams(
    var isShellMode: Boolean = true,
    var needDstPkg: Boolean = false,
    var needDeviceOnline: Boolean = false,
    var cmd: String = "",
    var customAction: String = "",
    var targetDeviceIp: String = "",
    var targetApp: String = ""
) {
    fun hasCustomAction(): Boolean = customAction.isNotEmpty()

    /**
     * 组装完整的 ADB 命令字符串，并进行宏替换。
     */
    fun getAdbCmd(): String {
        val resolvedCmd = cmd.replace("{0}", targetApp)
        return if (isShellMode) {
            "adb -s $targetDeviceIp shell $resolvedCmd"
        } else {
            "adb -s $targetDeviceIp $resolvedCmd"
        }
    }

    /**
     * 验证目标应用包名是否需要且已提供。
     */
    fun verifyTargetApp(): Boolean {
        if (needDstPkg && targetApp.isEmpty()) {
            return false
        }
        return true
    }
}
