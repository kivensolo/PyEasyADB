package com.easyadb.core.adb

/**
 * ADB command action parameters.
 * Maps to Python ActionCmdParams in utils/ADBTools.py
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
     * Assemble the full ADB command string with macro replacement.
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
     * Verify whether the target app package name is needed and provided.
     */
    fun verifyTargetApp(): Boolean {
        if (needDstPkg && targetApp.isEmpty()) {
            return false
        }
        return true
    }
}
