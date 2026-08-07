package com.easyadb.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.easyadb.core.config.AppConfigManager
import com.easyadb.core.config.AppPathsConfig
import com.easyadb.core.config.CmdGroup
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.config.FunctionTemplate
import com.easyadb.core.config.MenuAction
import com.easyadb.core.config.MenuConfig
import com.easyadb.core.config.XmlConfigLoader
import com.easyadb.core.database.DbManager
import com.easyadb.core.device.DevicesWatcher
import com.easyadb.core.log.AppLogger
import com.easyadb.core.log.LogConfig
import com.easyadb.core.adb.AdbExecutor
import com.easyadb.core.adb.LogcatStream
import com.easyadb.ui.designsystem.EasyAdbTheme
import com.easyadb.ui.functions.AppParamState
import com.easyadb.ui.functions.CustomActionHandler
import com.easyadb.ui.home.MainWindowScreen
import com.easyadb.ui.home.rememberDefaultToolBarActions
import com.easyadb.ui.devicelist.DeviceAliasEditDialog
import com.easyadb.ui.devicelist.DeviceListCallbacks
import com.easyadb.ui.console.createLogFlow
import com.easyadb.ui.dialogs.NewConnectDialog
import com.easyadb.ui.dialogs.AboutDialog
import com.easyadb.ui.dialogs.TextInputDialog
import com.easyadb.ui.dialogs.InstallApkDialog
import com.easyadb.ui.dialogs.InstallOptions
import com.easyadb.ui.dialogs.ScreenRecordDialog
import com.easyadb.ui.dialogs.ScreenRecordOptions
import com.easyadb.ui.dialogs.PullApkDialog
import com.easyadb.ui.dialogs.PullableApp
import com.easyadb.ui.dialogs.ApkHelperDialog
import com.easyadb.core.apk.ApkParser
import com.easyadb.core.apk.ApkInfo
import com.easyadb.core.adb.AppListLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

fun main() {
    // ── Step 1: Logging ──
    val logsDir = File(AppPathsConfig.logsPath).apply { mkdirs() }
    System.setProperty(
        "org.slf4j.simpleLogger.logFile",
        File(logsDir, "easyadb.log").absolutePath
    )
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    System.setProperty("easyadb.log.dir", logsDir.absolutePath)

    val appLogger = AppLogger(LogConfig(name = "EasyADB", logDir = logsDir.absolutePath))
    appLogger.info { "EasyADB CMP v2.0.0 - P4 Device/Command Tree active" }
    appLogger.info { "AppData dir: ${AppPathsConfig.localAppDataPath}" }

    // ── Step 2: Config ──
    val configFile = File(AppPathsConfig.appConfigIniFile)
    if (configFile.exists()) {
        AppConfigManager.initialize(configFile)
        appLogger.info { "AppConfig.ini loaded" }
    } else {
        appLogger.warn { "AppConfig.ini not found, skipping" }
    }

    // ── Step 3: Database ──
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val dbDevicesFlow = MutableStateFlow<List<com.easyadb.core.database.DeviceRecord>>(emptyList())
    val packageFlow = MutableStateFlow<List<String>>(emptyList())
    scope.launch {
        val result = DbManager.initialize(AppPathsConfig.dbFile)
        if (result.success) {
            appLogger.info { "Database initialized at ${AppPathsConfig.dbFile}" }
            reloadDevices(dbDevicesFlow, appLogger)
            // 数据库初始化完成后，再加载包名数据（避免异步竞争导致查不到）
            val pkgResult = DbManager.getAllPackages()
            if (pkgResult.success) {
                packageFlow.value = pkgResult.data ?: emptyList()
                appLogger.info { "Loaded ${packageFlow.value.size} packages from DB" }
            } else {
                appLogger.error { "Failed to load packages: ${pkgResult.error}" }
            }
        } else {
            appLogger.error { "Database init failed: ${result.error}" }
        }
    }

    // ── Step 4: Device Watcher ──
    val watcher = DevicesWatcher()
    watcher.start(intervalSeconds = 2, scope = scope)
    appLogger.info { "DevicesWatcher started" }

    // ── Step 5: Load menu & cmd & function configs (优先从 classpath 加载) ──
    val menuConfigs = loadMenuConfigFromClasspath(appLogger)
    val cmdGroups = loadCmdConfigFromClasspath(appLogger)
    val functionTemplates = loadFunctionTemplatesFromClasspath(appLogger)

    // ── Step 6: AdbExecutor ──
    val adbExecutor = AdbExecutor()

    // ── Step 7: 日志桥接 + Logcat ──
    val (consoleLogFlow, logAppender) = createLogFlow()
    val logcatStream = LogcatStream()

    // ── Icon ──
    val iconPainter = try {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("logo.png")
        stream?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }
    } catch (_: Exception) { null }

    // ── UI ──
    application {
        val onlineDevices by watcher.devices.collectAsState(initial = emptyList())
        val dbDevices by dbDevicesFlow.asStateFlow().collectAsState()
        val dbPackages by packageFlow.asStateFlow().collectAsState(initial = emptyList())
        var selectedDeviceIp by remember { mutableStateOf<String?>(null) }
        // 右键"备注设置"要编辑的目标设备；非 null 时弹出 DeviceAliasEditDialog。
        var aliasEditTarget by remember { mutableStateOf<com.easyadb.core.database.DeviceRecord?>(null) }
        // 新建连接对话框；true 时弹出 NewConnectDialog。
        var showNewConnectDialog by remember { mutableStateOf(false) }
        // P7 对话框显示状态
        var showAboutDialog by remember { mutableStateOf(false) }
        var showTextInputDialog by remember { mutableStateOf(false) }
        var showInstallApkDialog by remember { mutableStateOf(false) }
        var showScreenRecordDialog by remember { mutableStateOf(false) }
        var showPullApkDialog by remember { mutableStateOf(false) }
        var showApkHelperDialog by remember { mutableStateOf(false) }
        // 屏幕录制状态
        var isRecording by remember { mutableStateOf(false) }
        var remainingSeconds by remember { mutableStateOf(0) }
        // APK Helper 解析状态
        var parsedApkInfo by remember { mutableStateOf<ApkInfo?>(null) }
        var isParsingApk by remember { mutableStateOf(false) }
        // PullApk 应用列表
        var pullableApps by remember { mutableStateOf<List<PullableApp>>(emptyList()) }
        var isLoadingApps by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                watcher.stop()
                DbManager.close()
                appLogger.close()
                exitApplication()
            },
            title = "EasyADB",
            icon = iconPainter
        ) {
            EasyAdbTheme {
                val toolBarActions = rememberDefaultToolBarActions(
                    onAddDevice = {
                        appLogger.info { "ToolBar: 新建设备连接" }
                        showNewConnectDialog = true
                    },
                    onOpenShell = {
                        appLogger.info { "ToolBar: 打开 Shell" }
                        logAppender("[未实现] 打开 Shell", 3)
                    },
                    onRoot = {
                        appLogger.info { "ToolBar: Root" }
                        logAppender("[未实现] Root", 3)
                    },
                    onUnroot = {
                        appLogger.info { "ToolBar: Unroot" }
                        logAppender("[未实现] Unroot", 3)
                    }
                )

                val deviceListCallbacks = remember(scope, dbDevicesFlow) {
                    DeviceListCallbacks(
                        onDeviceClick = { record -> selectedDeviceIp = record.ip },
                        onDeviceDoubleClick = { record ->
                            appLogger.info { "Device double-click: ${record.ip} (connect)" }
                            logAppender("双击设备: ${record.ip}", 2)
                            selectedDeviceIp = record.ip
                            // 如果设备离线，执行连接
                            val isOnline = onlineDevices.any { it.name == record.ip }
                            if (!isOnline) {
                                scope.launch {
                                    logAppender("> adb connect ${record.ip}", 2)
                                    val result = adbExecutor.connectDevice(record.ip)
                                    if (result.success) {
                                        appLogger.info { "Connected to ${record.ip}" }
                                        logAppender("[连接成功] ${record.ip}", 2)
                                    } else {
                                        appLogger.error { "Connect failed: ${result.output}" }
                                        logAppender("[连接失败] ${record.ip}: ${result.output.take(200)}", 4)
                                    }
                                }
                            }
                        },
                        onDeviceAliasEdit = { record ->
                            appLogger.info { "Device alias edit: ${record.ip}" }
                            logAppender("修改备注: ${record.ip}", 2)
                            aliasEditTarget = record
                        },
                        onDeviceDisconnect = { record ->
                            appLogger.info { "Device disconnect: ${record.ip}" }
                            logAppender("> adb disconnect ${record.ip}", 2)
                            scope.launch {
                                val result = adbExecutor.disconnectDevice(record.ip)
                                if (result.success) {
                                    logAppender("[断开成功] ${record.ip}", 2)
                                } else {
                                    logAppender("[断开失败] ${record.ip}: ${result.output.take(200)}", 4)
                                }
                            }
                        },
                        onDeviceRemove = { record ->
                            appLogger.info { "Device remove: ${record.ip}" }
                            logAppender("删除设备: ${record.ip}", 2)
                            scope.launch {
                                val r = DbManager.deleteDevice(record.ip)
                                if (r.success) {
                                    logAppender("[删除成功] ${record.ip}", 2)
                                    watcher.onDeviceDeleted()
                                    reloadDevices(dbDevicesFlow, appLogger)
                                } else {
                                    logAppender("[删除失败] ${record.ip}: ${r.error}", 4)
                                }
                            }
                        },
                        onCommandClick = { item ->
                            appLogger.info { "Command click: ${item.name}" }
                        },
                        onCommandDoubleClick = { item ->
                            appLogger.info { "Command double-click: ${item.name} cmd=${item.cmd}" }
                            logAppender("执行命令: ${item.name}", 2)
                            val deviceIp = selectedDeviceIp
                            if (deviceIp == null) {
                                logAppender("[错误] 未选中设备，请先双击设备连接", 4)
                                return@DeviceListCallbacks
                            }
                            scope.launch {
                                val params = com.easyadb.core.adb.ActionCmdParams(
                                    isShellMode = item.shell,
                                    needDstPkg = item.needDstPkg,
                                    cmd = item.cmd,
                                    targetDeviceIp = deviceIp
                                )
                                val fullCmd = params.getAdbCmd()
                                logAppender("> $fullCmd", 2)
                                val result = adbExecutor.execAdbCmd(fullCmd)
                                val output = result.output.take(500)
                                if (output.isNotEmpty()) {
                                    logAppender(output, 2)
                                }
                                if (!result.success) {
                                    logAppender("[错误] 命令执行失败 (exitCode=${result.exitCode})", 4)
                                }
                            }
                        }
                    )
                }

                MainWindowScreen(
                    modifier = Modifier.fillMaxSize(),
                    toolBarActions = toolBarActions,
                    menuConfigs = menuConfigs,
                    onMenuAction = { action ->
                        when (action.action) {
                            "m_open_about_page" -> showAboutDialog = true
                            "m_show_apk_helper_dialog" -> {
                                parsedApkInfo = null
                                showApkHelperDialog = true
                            }
                            else -> handleMenuAction(action, appLogger, logAppender)
                        }
                    },
                    dbDevices = dbDevices,
                    onlineDevices = onlineDevices,
                    cmdGroups = cmdGroups,
                    selectedDeviceIp = selectedDeviceIp,
                    deviceListCallbacks = deviceListCallbacks,
                    bottomTabDefaultHeight = 200.dp,
                    // P5 功能区参数
                    functionTemplates = functionTemplates,
                    dbPackages = dbPackages,
                    onPackageAdd = { pkg ->
                        scope.launch {
                            DbManager.insertPackage(pkg)
                            val r = DbManager.getAllPackages()
                            if (r.success) packageFlow.value = r.data ?: emptyList()
                        }
                    },
                    onPackageDelete = { pkg ->
                        scope.launch {
                            DbManager.deletePackage(pkg)
                            val r = DbManager.getAllPackages()
                            if (r.success) packageFlow.value = r.data ?: emptyList()
                        }
                    },
                    onFunctionItemClick = { item: FunctionItem, state: AppParamState ->
                        // P7 对话框类 action 拦截：不走 CustomActionHandler，直接弹对话框
                        when (item.action) {
                            "m_input_text" -> showTextInputDialog = true
                            "m_show_install_app_dialog" -> showInstallApkDialog = true
                            "m_screen_record" -> showScreenRecordDialog = true
                            "m_pull_apk" -> {
                                pullableApps = emptyList()
                                showPullApkDialog = true
                                val deviceIp = selectedDeviceIp
                                if (deviceIp != null) {
                                    isLoadingApps = true
                                    scope.launch {
                                        val apps = AppListLoader.loadAppList(deviceIp)
                                        pullableApps = apps.map {
                                            PullableApp(
                                                packageName = it.packageName,
                                                apkPath = it.apkPath,
                                                isSystem = it.type == "系统"
                                            )
                                        }
                                        isLoadingApps = false
                                        logAppender("[应用列表] 加载 ${apps.size} 个应用", 2)
                                    }
                                }
                            }
                            else -> scope.launch {
                                CustomActionHandler.handle(
                                    item = item,
                                    deviceIp = selectedDeviceIp ?: "",
                                    appParams = state,
                                    executor = adbExecutor,
                                    onUninstallConfirm = { pkg: String ->
                                        appLogger.info { "Uninstall confirmation for: $pkg" }
                                        true
                                    },
                                    onResult = { msg: String ->
                                        appLogger.info { "[P5] $msg" }
                                        logAppender("[P5] $msg", 2)
                                    }
                                )
                            }
                        }
                    },
                    // P6 控制台 + Logcat 参数
                    consoleLogFlow = consoleLogFlow,
                    logcatStream = logcatStream
                )

                // 备注设置弹窗（对齐 Python device_alis_edit_dialog）
                aliasEditTarget?.let { target ->
                    DeviceAliasEditDialog(
                        device = target,
                        onDismiss = { aliasEditTarget = null },
                        onConfirm = { newAlias ->
                            scope.launch {
                                val r = DbManager.updateDeviceAlias(target.ip, newAlias)
                                if (r.success) {
                                    appLogger.info { "Alias updated: ${target.ip} -> $newAlias" }
                                    logAppender("[备注已更新] ${target.ip} -> $newAlias", 2)
                                    reloadDevices(dbDevicesFlow, appLogger)
                                } else {
                                    logAppender("[备注更新失败] ${target.ip}: ${r.error}", 4)
                                }
                            }
                            aliasEditTarget = null
                        }
                    )
                }

                // 新建连接对话框（对齐 Python NewConnectDialog）
                if (showNewConnectDialog) {
                    NewConnectDialog(
                        onDismiss = { showNewConnectDialog = false },
                        onConnect = { deviceIp ->
                            showNewConnectDialog = false
                            scope.launch {
                                logAppender("> adb connect $deviceIp", 2)
                                val connectResult = adbExecutor.connectDevice(deviceIp)
                                if (connectResult.success) {
                                    appLogger.info { "Connected to $deviceIp" }
                                    logAppender("[连接成功] $deviceIp", 2)
                                    // 写入数据库
                                    val (ip, port) = com.easyadb.core.util.NetworkUtils.parseIpPort(deviceIp)
                                    val deviceRecord = com.easyadb.core.database.DeviceRecord(
                                        ip = ip,
                                        port = port,
                                        alias = "",
                                        deviceInfo = "",
                                        active = 1
                                    )
                                    val insertResult = DbManager.insertDevice(deviceRecord)
                                    if (insertResult.success) {
                                        appLogger.info { "Device added to DB: $deviceIp" }
                                        reloadDevices(dbDevicesFlow, appLogger)
                                    } else {
                                        logAppender("[设备入库失败] ${insertResult.error}", 4)
                                    }
                                } else {
                                    logAppender("[连接失败] $deviceIp: ${connectResult.output}", 4)
                                }
                            }
                        }
                    )
                }

                // 关于对话框（菜单「关于」触发）
                if (showAboutDialog) {
                    AboutDialog(onDismiss = { showAboutDialog = false })
                }

                // APK Helper 对话框（菜单「APK Helper」触发）
                if (showApkHelperDialog) {
                    ApkHelperDialog(
                        apkInfo = parsedApkInfo,
                        isParsing = isParsingApk,
                        onDismiss = {
                            showApkHelperDialog = false
                            parsedApkInfo = null
                        },
                        onParse = { apkPath ->
                            isParsingApk = true
                            parsedApkInfo = null
                            scope.launch {
                                val info = ApkParser.parse(apkPath)
                                parsedApkInfo = info
                                isParsingApk = false
                                logAppender("[APK解析] ${info.packageName.ifBlank { "未知包名" }}", 2)
                            }
                        }
                    )
                }

                // 文本输入对话框（m_input_text 触发）
                if (showTextInputDialog) {
                    TextInputDialog(
                        onDismiss = { showTextInputDialog = false },
                        onInput = { text ->
                            showTextInputDialog = false
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                scope.launch {
                                    // 转义：换行→空格，空格→%s（对齐 Python doTextInput）
                                    val escaped = text.replace("\n", " ").replace(" ", "%s")
                                    val cmd = "adb -s $deviceIp shell input text $escaped"
                                    logAppender("> $cmd", 2)
                                    val result = adbExecutor.execAdbCmd(cmd)
                                    if (!result.success) {
                                        logAppender("[输入失败] ${result.output.take(200)}", 4)
                                    }
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        }
                    )
                }

                // 安装应用对话框（m_show_install_app_dialog 触发）
                if (showInstallApkDialog) {
                    InstallApkDialog(
                        onDismiss = { showInstallApkDialog = false },
                        onInstall = { apkPath, options ->
                            showInstallApkDialog = false
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                scope.launch {
                                    val cmd = StringBuilder("adb -s $deviceIp install ")
                                    if (options.replace) cmd.append("-r ")
                                    if (options.testApp) cmd.append("-t ")
                                    if (options.downgrade) cmd.append("-d ")
                                    cmd.append(apkPath)
                                    logAppender("> $cmd", 2)
                                    val result = adbExecutor.execAdbCmd(cmd.toString())
                                    logAppender(result.output.take(500), 2)
                                    if (!result.success) {
                                        logAppender("[安装失败] exitCode=${result.exitCode}", 4)
                                    }
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        }
                    )
                }

                // 屏幕录制对话框（m_screen_record 触发）
                if (showScreenRecordDialog) {
                    ScreenRecordDialog(
                        isRecording = isRecording,
                        remainingSeconds = remainingSeconds,
                        onDismiss = { showScreenRecordDialog = false },
                        onRecordStart = { options ->
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                val saveDialog = java.awt.FileDialog(null as java.awt.Frame?, "保存视频", java.awt.FileDialog.SAVE)
                                saveDialog.file = "screenrecord.mp4"
                                saveDialog.isVisible = true
                                val savePath = if (saveDialog.directory != null && saveDialog.file != null) {
                                    java.io.File(saveDialog.directory, saveDialog.file).absolutePath
                                } else null
                                if (savePath != null) {
                                    isRecording = true
                                    remainingSeconds = options.timeLimit
                                    scope.launch {
                                        // 构建 screenrecord 命令
                                        val cmd = StringBuilder("screenrecord --verbose --time-limit ${options.timeLimit}")
                                        if (options.bitRate != null) cmd.append(" --bit-rate ${options.bitRate}")
                                        if (options.customResolution.isNotBlank()) cmd.append(" --size ${options.customResolution}")
                                        if (options.rotate) cmd.append(" --rotate")
                                        val tmpPath = "/sdcard/easy_screenrecord.mp4"
                                        cmd.append(" $tmpPath")

                                        logAppender("[录屏] 开始录制 ${options.timeLimit} 秒", 2)
                                        // 倒计时显示
                                        for (i in options.timeLimit downTo 1) {
                                            remainingSeconds = i
                                            kotlinx.coroutines.delay(1000)
                                        }
                                        // 录制结束后 pull + rm
                                        adbExecutor.execAdbCmd("adb -s $deviceIp exec-out $cmd")
                                        adbExecutor.execAdbCmd("adb -s $deviceIp pull $tmpPath \"$savePath\"")
                                        adbExecutor.execAdbCmd("adb -s $deviceIp shell rm $tmpPath")
                                        isRecording = false
                                        logAppender("[录屏] 已保存到 $savePath", 2)
                                    }
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        },
                        onRecordStop = {
                            isRecording = false
                            logAppender("[录屏] 已请求终止（等待 pull）", 3)
                        },
                        onPull = {
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                val saveDialog = java.awt.FileDialog(null as java.awt.Frame?, "保存视频", java.awt.FileDialog.SAVE)
                                saveDialog.file = "screenrecord.mp4"
                                saveDialog.isVisible = true
                                if (saveDialog.directory != null && saveDialog.file != null) {
                                    val savePath = java.io.File(saveDialog.directory, saveDialog.file).absolutePath
                                    scope.launch {
                                        val tmpPath = "/sdcard/easy_screenrecord.mp4"
                                        val cmd = "adb -s $deviceIp pull $tmpPath \"$savePath\""
                                        logAppender("> $cmd", 2)
                                        val result = adbExecutor.execAdbCmd(cmd)
                                        if (result.success) {
                                            logAppender("[拉取成功] $savePath", 2)
                                        } else {
                                            logAppender("[拉取失败] ${result.output.take(200)}", 4)
                                        }
                                    }
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        }
                    )
                }

                // 提取 APK 对话框（m_pull_apk 触发）
                if (showPullApkDialog) {
                    PullApkDialog(
                        apps = pullableApps,
                        isLoading = isLoadingApps,
                        onDismiss = { showPullApkDialog = false },
                        onRefresh = {
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                isLoadingApps = true
                                scope.launch {
                                    val apps = AppListLoader.loadAppList(deviceIp)
                                    pullableApps = apps.map {
                                        PullableApp(it.packageName, it.apkPath, it.type == "系统")
                                    }
                                    isLoadingApps = false
                                    logAppender("[应用列表] 加载 ${apps.size} 个应用", 2)
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        },
                        onPull = { app ->
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                val saveDialog = java.awt.FileDialog(null as java.awt.Frame?, "保存 APK", java.awt.FileDialog.SAVE)
                                saveDialog.file = "${app.packageName.replace('.', '_')}.apk"
                                saveDialog.isVisible = true
                                if (saveDialog.directory != null && saveDialog.file != null) {
                                    val savePath = java.io.File(saveDialog.directory, saveDialog.file).absolutePath
                                    scope.launch {
                                        val cmd = "adb -s $deviceIp pull \"${app.apkPath}\" \"$savePath\""
                                        logAppender("> $cmd", 2)
                                        val result = adbExecutor.execAdbCmd(cmd)
                                        if (result.success) {
                                            logAppender("[提取成功] $savePath", 2)
                                        } else {
                                            logAppender("[提取失败] ${result.output.take(200)}", 4)
                                        }
                                    }
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 从数据库重新加载设备列表并推入 [flow]。
 */
private suspend fun reloadDevices(
    flow: MutableStateFlow<List<com.easyadb.core.database.DeviceRecord>>,
    logger: AppLogger
) {
    val r = DbManager.getAllDevices()
    if (r.success) {
        flow.value = r.data ?: emptyList()
        logger.info { "Loaded ${flow.value.size} devices from DB" }
    } else {
        logger.error { "Failed to load devices: ${r.error}" }
    }
}

/**
 * 从 classpath 加载菜单配置（config/menus_ui.xml）。
 * 若 classpath 加载失败，回退到文件系统路径。
 */
private fun loadMenuConfigFromClasspath(logger: AppLogger): List<MenuConfig> {
    // 1. 尝试 classpath 加载
    val classLoader = Thread.currentThread().contextClassLoader
    val stream = classLoader.getResourceAsStream("config/menus_ui.xml")
    if (stream != null) {
        return try {
            val menus = parseMenuConfigXml(stream)
            logger.info { "menus_ui.xml loaded from classpath: ${menus.size} menus" }
            menus
        } catch (e: Exception) {
            logger.error { "Failed to parse menus_ui.xml from classpath: ${e.message}" }
            emptyList()
        }
    }

    // 2. 回退到文件系统
    val menuFile = File(AppPathsConfig.menusUiFile)
    if (menuFile.exists()) {
        return XmlConfigLoader.loadMenuConfig(menuFile).also {
            logger.info { "menus_ui.xml loaded from file: ${it.size} menus (path: ${menuFile.absolutePath})" }
        }
    }

    logger.warn { "menus_ui.xml not found (classpath nor " + menuFile.absolutePath + ")" }
    return emptyList()
}

/**
 * 从 classpath 加载命令配置（config/cmdConfig.xml）。
 */
private fun loadCmdConfigFromClasspath(logger: AppLogger): List<CmdGroup> {
    val classLoader = Thread.currentThread().contextClassLoader
    val stream = classLoader.getResourceAsStream("config/cmdConfig.xml")
    if (stream != null) {
        return try {
            val groups = parseCmdConfigXml(stream)
            logger.info { "cmdConfig.xml loaded from classpath: ${groups.size} groups" }
            groups
        } catch (e: Exception) {
            logger.error { "Failed to parse cmdConfig.xml from classpath: ${e.message}" }
            emptyList()
        }
    }

    val cmdFile = File(AppPathsConfig.cmdConfigFile)
    if (cmdFile.exists()) {
        return XmlConfigLoader.loadCmdConfig(cmdFile).also {
            logger.info { "cmdConfig.xml loaded from file: ${it.size} groups (path: ${cmdFile.absolutePath})" }
        }
    }

    logger.warn { "cmdConfig.xml not found (classpath nor " + cmdFile.absolutePath + ")" }
    return emptyList()
}

/**
 * 从 classpath 加载功能模板配置（config/function_templates.xml）。
 */
private fun loadFunctionTemplatesFromClasspath(logger: AppLogger): List<FunctionTemplate> {
    val classLoader = Thread.currentThread().contextClassLoader
    val stream = classLoader.getResourceAsStream("config/function_templates.xml")
    if (stream != null) {
        return try {
            val templates = parseFunctionTemplateXml(stream)
            logger.info { "function_templates.xml loaded from classpath: ${templates.size} templates" }
            templates
        } catch (e: Exception) {
            logger.error { "Failed to parse function_templates.xml from classpath: ${e.message}" }
            emptyList()
        }
    }

    val funcFile = File(AppPathsConfig.functionTemplatesFile)
    if (funcFile.exists()) {
        return XmlConfigLoader.loadFunctionTemplates(funcFile).also {
            logger.info { "function_templates.xml loaded from file: ${it.size} templates (path: ${funcFile.absolutePath})" }
        }
    }

    logger.warn { "function_templates.xml not found (classpath nor " + funcFile.absolutePath + ")" }
    return emptyList()
}

/**
 * 解析 function_templates.xml 的 InputStream 为 FunctionTemplate 列表。
 */
private fun parseFunctionTemplateXml(stream: InputStream): List<FunctionTemplate> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isIgnoringComments = true
        isIgnoringElementContentWhitespace = true
    }
    val db = factory.newDocumentBuilder()
    val doc = db.parse(stream)
    val templateNodes = doc.documentElement.getElementsByTagName("template")
    val result = mutableListOf<FunctionTemplate>()

    for (i in 0 until templateNodes.length) {
        val templateElement = templateNodes.item(i) as org.w3c.dom.Element
        val name = templateElement.getAttribute("name")
        val layout = templateElement.getAttribute("layout").ifEmpty { "grid" }
        val items = mutableListOf<com.easyadb.core.config.FunctionItem>()

        val itemNodes = templateElement.getElementsByTagName("item")
        for (j in 0 until itemNodes.length) {
            val itemElement = itemNodes.item(j) as org.w3c.dom.Element
            val state = itemElement.getAttribute("state")
            val attrs = parseFunctionTemplateAttrs(itemElement)
            items.add(
                com.easyadb.core.config.FunctionItem(
                    icon = attrs["icon"] ?: "",
                    text = attrs["text"] ?: "",
                    cmd = attrs["cmd"] ?: "",
                    action = attrs["act"] ?: "",
                    shell = attrs["shell"]?.toBoolean() ?: true,
                    needPkgName = attrs["isNeedPkgName"]?.toBoolean() ?: false,
                    needDeviceOnline = attrs["isNeedDeviceOnline"]?.toBoolean() ?: false,
                    state = state
                )
            )
        }
        result.add(FunctionTemplate(name = name, layout = layout, items = items))
    }
    stream.close()
    return result
}

private fun parseFunctionTemplateAttrs(element: org.w3c.dom.Element): Map<String, String> {
    val attrs = mutableMapOf<String, String>()
    val attrNodes = element.getElementsByTagName("attr")
    for (i in 0 until attrNodes.length) {
        val attrElement = attrNodes.item(i) as org.w3c.dom.Element
        val attrName = attrElement.getAttribute("name")
        val attrValue = attrElement.textContent?.trim() ?: ""
        if (attrName.isNotBlank()) {
            attrs[attrName] = attrValue
        }
    }
    return attrs
}

/**
 * 直接解析 menus_ui.xml 的 InputStream 为 MenuConfig 列表。
 */
private fun parseMenuConfigXml(stream: InputStream): List<MenuConfig> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isIgnoringComments = true
        isIgnoringElementContentWhitespace = true
    }
    val db = factory.newDocumentBuilder()
    val doc = db.parse(stream)
    val menuNodes = doc.documentElement.getElementsByTagName("menu")
    val result = mutableListOf<MenuConfig>()

    for (i in 0 until menuNodes.length) {
        val menuElement = menuNodes.item(i) as org.w3c.dom.Element

        val menuName = menuElement.getAttribute("name")
        val actions = mutableListOf<MenuAction>()
        val actionNodes = menuElement.childNodes

        for (j in 0 until actionNodes.length) {
            val actionElement = actionNodes.item(j)
            if (actionElement !is org.w3c.dom.Element || actionElement.tagName != "action") continue
            val actionName = actionElement.getAttribute("name")
            var shortcut = ""
            var icon = ""
            var actionAttr = ""
            for (k in 0 until actionElement.childNodes.length) {
                val attrEl = actionElement.childNodes.item(k)
                if (attrEl !is org.w3c.dom.Element || attrEl.tagName != "attr") continue
                val attrName = attrEl.getAttribute("name")
                val attrValue = attrEl.textContent?.trim() ?: ""
                when (attrName) {
                    "shortcut" -> shortcut = attrValue
                    "icon" -> icon = attrValue
                    "action" -> actionAttr = attrValue
                }
            }
            actions.add(MenuAction(name = actionName, shortcut = shortcut, icon = icon, action = actionAttr))
        }
        result.add(MenuConfig(name = menuName, actions = actions))
    }
    stream.close()
    return result
}

/**
 * 解析 cmdConfig.xml 的 InputStream 为 CmdGroup 列表。
 */
private fun parseCmdConfigXml(stream: InputStream): List<CmdGroup> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isIgnoringComments = true
        isIgnoringElementContentWhitespace = true
    }
    val db = factory.newDocumentBuilder()
    val doc = db.parse(stream)
    val groupNodes = doc.documentElement.getElementsByTagName("group")
    val result = mutableListOf<CmdGroup>()

    for (i in 0 until groupNodes.length) {
        val groupEl = groupNodes.item(i) as org.w3c.dom.Element
        val groupName = groupEl.getAttribute("name")
        val items = mutableListOf<com.easyadb.core.config.CmdItem>()
        val subGroups = mutableListOf<com.easyadb.core.config.CmdSubGroup>()

        for (j in 0 until groupEl.childNodes.length) {
            val child = groupEl.childNodes.item(j)
            if (child !is org.w3c.dom.Element) continue
            when (child.tagName) {
                "item" -> parseCmdItem(child)?.let(items::add)
                "sub_group" -> parseCmdSubGroup(child)?.let(subGroups::add)
            }
        }
        result.add(CmdGroup(name = groupName, items = items, subGroups = subGroups))
    }
    stream.close()
    return result
}

private fun parseCmdItem(element: org.w3c.dom.Element): com.easyadb.core.config.CmdItem? {
    val name = element.getAttribute("name")
    if (name.isBlank()) return null
    val text = element.textContent?.trim() ?: ""
    val desc = element.getAttribute("desc")
    val dstPkg = element.getAttribute("dst_pkg") == "true"
    val shell = element.getAttribute("shell") != "false"
    return com.easyadb.core.config.CmdItem(
        name = name,
        cmd = text,
        shell = shell,
        needDstPkg = dstPkg,
        description = desc
    )
}

private fun parseCmdSubGroup(element: org.w3c.dom.Element): com.easyadb.core.config.CmdSubGroup? {
    val name = element.getAttribute("name")
    if (name.isBlank()) return null
    val items = mutableListOf<com.easyadb.core.config.CmdItem>()
    val itemNodes = element.getElementsByTagName("item")
    for (i in 0 until itemNodes.length) {
        val item = parseCmdItem(itemNodes.item(i) as org.w3c.dom.Element)
        if (item != null) items.add(item)
    }
    return com.easyadb.core.config.CmdSubGroup(name = name, items = items)
}

/**
 * 处理菜单栏操作。
 * P4 阶段仅记录日志，后续阶段实现具体操作（P5/P7）。
 */
private fun handleMenuAction(action: MenuAction, logger: AppLogger, logAppender: (String, Int) -> Unit) {
    logger.info { "Menu action: ${action.name} (action=${action.action})" }
    when (action.action) {
        "m_close_app" -> logger.info { "Exit requested via menu" }
        else -> logAppender("[未实现] 菜单操作: ${action.name}", 3)
    }
}
