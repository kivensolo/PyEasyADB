package com.easyadb.ui.functions

import com.easyadb.core.adb.ActionCmdParams
import com.easyadb.core.adb.AdbExecutor
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.log.AppLogger
import com.easyadb.core.util.ExecResult
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 自定义行为分发处理。
 *
 * 对应 Python `src/CenterWindow.py::doCustomAction`（line 327-384）：
 * 根据 [FunctionItem] 的类型（cmd 或 act）执行对应的 ADB 操作。
 *
 * 对于对话框类行为（安装/录屏/文本输入/APK提取），由宿主在 onFunctionItemClick
 * 中拦截弹出对应对话框，不进入本 handler。
 */
object CustomActionHandler {

    private val logger = AppLogger.getLogger(CustomActionHandler::class.java)

    /**
     * 处理 [FunctionItem] 的点击事件。
     *
     * @param item 功能项定义（来自 function_templates.xml）
     * @param deviceIp 当前选中的设备 IP（可能为空）
     * @param appParams 当前用户输入的应用参数
     * @param executor AdbExecutor 实例
     * @param onUninstallConfirm 卸载确认回调，返回 true 表示用户确认
     * @param onResult 结果显示回调（用于控制台输出）
     */
    suspend fun handle(
        item: FunctionItem,
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onUninstallConfirm: suspend (String) -> Boolean,
        onResult: (String) -> Unit
    ) {
        // 验证设备在线
        if (item.needDeviceOnline && deviceIp.isEmpty()) {
            onResult("[错误] 操作需要设备在线，但未选中设备")
            logger.warn { "P5 action skipped: needDeviceOnline but no device selected (item=${item.text})" }
            return
        }

        // 验证包名
        if (item.needPkgName && appParams.selectedPackage.isEmpty()) {
            onResult("[错误] 操作需要包名，但未输入包名")
            logger.warn { "P5 action skipped: needPkgName but no package selected (item=${item.text})" }
            return
        }

        // ── 按类型分发 ──
        if (item.cmd.isNotEmpty()) {
            handleCmdItem(item, deviceIp, appParams, executor, onUninstallConfirm, onResult)
        } else if (item.action.isNotEmpty()) {
            handleActItem(item, deviceIp, appParams, executor, onResult)
        } else {
            onResult("[错误] 未定义操作")
            logger.warn { "P5 action skipped: no cmd or action defined (item=${item.text})" }
        }
    }

    /**
     * 处理 `cmd` 类型的功能项（直接执行 ADB 命令）。
     */
    private suspend fun handleCmdItem(
        item: FunctionItem,
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onUninstallConfirm: suspend (String) -> Boolean,
        onResult: (String) -> Unit
    ) {
        val params = ActionCmdParams(
            isShellMode = item.shell,
            needDstPkg = item.needPkgName,
            cmd = item.cmd,
            targetDeviceIp = deviceIp,
            targetApp = appParams.selectedPackage
        )

        // 卸载需要二次确认
        if (item.cmd.contains("uninstall")) {
            val confirmed = onUninstallConfirm(appParams.selectedPackage)
            if (!confirmed) {
                onResult("[取消] 用户取消了卸载操作")
                return
            }
        }

        val fullCmd = params.getAdbCmd()
        onResult("> $fullCmd")
        logger.infoWithStamp(fullCmd)

        val result = executeWithTimeout(executor, fullCmd)
        onResult(result.output.take(500)) // 限制输出长度
        if (!result.success) {
            onResult("[错误] 命令执行失败 (exitCode=${result.exitCode})")
        }
    }

    /**
     * 处理 `act` 类型的功能项（自定义行为）。
     */
    private suspend fun handleActItem(
        item: FunctionItem,
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onResult: (String) -> Unit
    ) {
        when (item.action) {
            "m_screenshot" -> doScreenshot(deviceIp, executor, onResult)
            "m_start_app" -> doStartApp(deviceIp, appParams, executor, onResult)
            "m_restart_app" -> doRestartApp(deviceIp, appParams, executor, onResult)
            "m_send_broadcast" -> doSendBroadcast(deviceIp, appParams, executor, onResult)
            "m_query_contentprovider" -> doQueryContentProvider(deviceIp, appParams, executor, onResult)
            else -> {
                onResult("[错误] 未知的自定义行为: ${item.action}")
                logger.warn { "P5 unknown action: ${item.action}" }
            }
        }
    }

    // ── 具体行为实现 ──

    /**
     * 屏幕截图：弹出文件保存对话框 → 执行截图命令。
     * 对应 Python `CenterWindow.action_save_screen_shoot`。
     */
    private suspend fun doScreenshot(
        deviceIp: String,
        executor: AdbExecutor,
        onResult: (String) -> Unit
    ) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val defaultName = "screenshot_$timestamp.png"

        // 使用 java.awt.FileDialog 弹出保存对话框
        val dialog = FileDialog(null as Frame?, "保存截图", FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true

        val selectedDir = dialog.directory
        val selectedFile = dialog.file
        if (selectedDir == null || selectedFile == null) {
            onResult("[取消] 用户取消了截图保存")
            return
        }

        val savePath = File(selectedDir, selectedFile).absolutePath
        onResult("> 截图保存到: $savePath")
        logger.infoWithStamp("Screenshot save path: $savePath")

        val result = executor.getScreenShoot(deviceIp, savePath)
        if (result.success) {
            onResult("[成功] 截图已保存: $savePath")
        } else {
            onResult("[错误] 截图失败: ${result.output.take(200)}")
        }
    }

    /**
     * 启动应用：构建 `am start` 命令并执行。
     * 对应 Python `CenterWindow.start_app`（结合 build_am_cmd）。
     */
    private suspend fun doStartApp(
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onResult: (String) -> Unit
    ) {
        val amCmd = AmCommandBuilder.buildAmCmd(
            name = "start",
            packageName = appParams.selectedPackage,
            classPath = appParams.activityClassPath,
            action = appParams.action,
            extendParams = appParams.extendParams
        )
        val fullCmd = "adb -s $deviceIp shell $amCmd"
        onResult("> $fullCmd")
        logger.infoWithStamp(fullCmd)

        val result = executeWithTimeout(executor, fullCmd)
        onResult(result.output.take(500))
        if (!result.success) {
            onResult("[错误] 启动应用失败")
        }
    }

    /**
     * 重启应用：先 force-stop 再 start。
     * 对应 Python `CenterWindow.doCustomAction` 中 m_restart_app 的处理。
     */
    private suspend fun doRestartApp(
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onResult: (String) -> Unit
    ) {
        val pkg = appParams.selectedPackage

        // 1. force-stop
        val stopCmd = "adb -s $deviceIp shell am force-stop $pkg"
        onResult("> $stopCmd")
        executor.execAdbCmd(stopCmd)

        // 2. sleep 1 (Python 中为 time.sleep(1))
        onResult("> sleep 1")
        kotlinx.coroutines.delay(1000)

        // 3. am start
        val amCmd = AmCommandBuilder.buildAmCmd(
            name = "start",
            packageName = pkg,
            classPath = appParams.activityClassPath,
            action = appParams.action,
            extendParams = appParams.extendParams
        )
        val startCmd = "adb -s $deviceIp shell $amCmd"
        onResult("> $startCmd")
        val result = executeWithTimeout(executor, startCmd)
        onResult(result.output.take(500))
        if (!result.success) {
            onResult("[错误] 重启应用失败")
        }
    }

    /**
     * 发送广播：构建 `am broadcast` 命令并执行。
     */
    private suspend fun doSendBroadcast(
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onResult: (String) -> Unit
    ) {
        val amCmd = AmCommandBuilder.buildAmCmd(
            name = "broadcast",
            packageName = appParams.selectedPackage,
            classPath = appParams.activityClassPath,
            action = appParams.action,
            extendParams = appParams.extendParams
        )
        val fullCmd = "adb -s $deviceIp shell $amCmd"
        onResult("> $fullCmd")
        logger.infoWithStamp(fullCmd)

        val result = executeWithTimeout(executor, fullCmd)
        onResult(result.output.take(500))
        if (!result.success) {
            onResult("[错误] 发送广播失败")
        }
    }

    /**
     * 查询 ContentProvider：执行 `adb shell content query --uri <uri>`。
     * 对应 Python `CenterWindow.doCustomAction` 中 m_query_contentprovider 的处理。
     */
    private suspend fun doQueryContentProvider(
        deviceIp: String,
        appParams: AppParamState,
        executor: AdbExecutor,
        onResult: (String) -> Unit
    ) {
        val uri = appParams.extendParams.trim()
        if (uri.isEmpty()) {
            onResult("[错误] 请在扩展参数中输入 ContentProvider URI（如: content://settings/secure）")
            return
        }
        val fullCmd = "adb -s $deviceIp shell content query --uri $uri"
        onResult("> $fullCmd")
        logger.infoWithStamp(fullCmd)

        val result = executeWithTimeout(executor, fullCmd)
        onResult(result.output.take(500))
        if (!result.success) {
            onResult("[错误] 查询 ContentProvider 失败")
        }
    }

    /**
     * 执行命令并设置超时（20s）。
     */
    private suspend fun executeWithTimeout(executor: AdbExecutor, cmd: String): ExecResult {
        return executor.execAdbCmd(cmd)
    }
}