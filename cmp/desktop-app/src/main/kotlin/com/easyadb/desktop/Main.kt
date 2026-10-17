package com.easyadb.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import com.easyadb.core.config.AppConfigManager
import com.easyadb.core.config.AppPathsConfig
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
import com.easyadb.core.adb.MirrorStartResult
import com.easyadb.core.adb.ScrcpyLauncher
import com.easyadb.platform.Win32Interop
import com.easyadb.ui.designsystem.EasyAdbTheme
import com.easyadb.ui.designsystem.ThemeMode
import com.easyadb.ui.functions.AppParamState
import com.easyadb.ui.functions.CustomActionHandler
import com.easyadb.ui.home.MainWindowScreen
import com.easyadb.ui.home.components.WindowControls
import com.easyadb.core.device.DevicePropFetcher
import com.easyadb.ui.devicelist.DeviceAliasEditDialog
import com.easyadb.ui.devicelist.DeviceListCallbacks
import com.easyadb.ui.console.createLogFlow
import com.easyadb.ui.dialogs.NewConnectDialog
import com.easyadb.ui.dialogs.TextInputDialog
import com.easyadb.ui.dialogs.InstallApkDialog
import com.easyadb.ui.dialogs.InstallOptions
import com.easyadb.ui.dialogs.ScreenRecordDialog
import com.easyadb.ui.dialogs.ScreenRecordOptions
import com.easyadb.core.adb.AppListLoader
import com.easyadb.core.file.TransferCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import java.awt.Frame
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** CMP 侧移除的菜单 action（种子 XML 保留给 Python 版；明细见 doc/待重构功能.md）。 */
private val REMOVED_MENU_ACTIONS = setOf(
    "m_connect_new_device",
    "m_close_app",
    "m_show_apk_helper_dialog",
    "m_open_shell",
    "m_root",
    "m_unroot",
    "m_open_log_page",
    "m_open_about_page"
)

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
    // 用户配置落可写位置（%LOCALAPPDATA%/EasyADB/data/AppConfig.ini），承载运行时设置
    // （[General] Theme 等）；权限表等静态内容做种子：开发态取运行目录 res/config/，
    // 打包态取 classpath（desktop-app 资源内 config/AppConfig.ini）。首次 save 起种子
    // 内容一并并入用户文件，此后不再依赖种子。
    val userConfigFile = File(AppPathsConfig.dataPath, "AppConfig.ini")
    val seedFile = File(AppPathsConfig.appConfigIniFile)
    val seedStream = Thread.currentThread().contextClassLoader
        .getResourceAsStream("/config/AppConfig.ini")
    AppConfigManager.initialize(userConfigFile, seedFile.takeIf { it.exists() }, seedStream)

    // 主题初始态：读 [General] Theme（system|light|dark），缺省跟随系统
    val initialThemeMode = ThemeMode.fromConfigValue(
        AppConfigManager.getOrDefault(AppConfigManager.GENERAL_SECTION, "Theme", "system")
    )
    appLogger.info { "Theme mode: ${initialThemeMode.configValue}" }

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

    // ── Step 5: Load menu & function configs (优先从 classpath 加载) ──
    // CMP 菜单精简（menus_ui.xml 种子保留供 Python 版使用，加载后剔除）：
    // 连接新设备（设备列表区头部按钮入口保留）/ 退出（窗口关闭钮）/
    // APK Helper、查看日志、关于（已列入 doc/待重构功能.md，后续对接）/
    // 打开 Shell、Root、Unroot（未实现占位）
    val menuConfigs = loadMenuConfigFromClasspath(appLogger)
        .map { config -> config.copy(actions = config.actions.filter { it.action !in REMOVED_MENU_ACTIONS }) }
        .filter { it.actions.isNotEmpty() }
    // SP-5001 旧入口清理：过滤 m_pull_apk（APK 提取弹窗入口，提取统一走应用管理 Tab）
    // 与 tab=files 三大卡（推送/拉取已被双栏面板取代）；XML 种子原样保留（Python 版共用）
    val functionTemplates = loadFunctionTemplatesFromClasspath(appLogger)
        .map { t ->
            t.copy(items = t.items.filterNot { it.action == "m_pull_apk" || it.tab == "files" })
        }

    // ── Step 6: AdbExecutor ──
    val adbExecutor = AdbExecutor()

    // ── Step 7: 日志桥接 + Logcat ──
    val (consoleLogFlow, logAppender) = createLogFlow()
    val logcatStream = LogcatStream()

    // SP-5001 文件传输：宿主持有的传输任务中心（App 级单例——面板随 Tab 切换销毁，传输不中断）
    val transferCenter = TransferCenter(logAppender)

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
        var showTextInputDialog by remember { mutableStateOf(false) }
        var showInstallApkDialog by remember { mutableStateOf(false) }
        var showScreenRecordDialog by remember { mutableStateOf(false) }
        // 屏幕录制状态
        var isRecording by remember { mutableStateOf(false) }
        var remainingSeconds by remember { mutableStateOf(0) }
        // 当前录制协程：用于"终止"按钮和关闭对话框时取消（cancel 会杀 adb 进程，对齐原版 SIGINT）
        var recordJob by remember { mutableStateOf<Job?>(null) }
        // 应用管理 Tab（SP-7001）：设备应用列表与加载态
        var appList by remember { mutableStateOf<List<AppListLoader.AppInfo>>(emptyList()) }
        var isAppListLoading by remember { mutableStateOf(false) }
        // P8: Scrcpy 独立窗口镜像（状态由 launcher 的 StateFlow 推送，
        // 用户直接关 scrcpy 窗口也会经 onExit 回置 Idle）
        val scrcpyLauncher = remember { ScrcpyLauncher() }
        val mirrorState by scrcpyLauncher.state.collectAsState()
        // 镜像事件（异常退出诊断 / 转发清理）转到应用控制台，可见性不依赖文件日志
        LaunchedEffect(Unit) {
            scrcpyLauncher.events.collect { event ->
                logAppender(event.message, if (event.isError) 4 else 2)
            }
        }

        // ── 主题三态（P11）：跟随系统/浅色/深色，菜单「工具」下切换，选择即写回配置 ──
        var themeMode by remember { mutableStateOf(initialThemeMode) }
        val applyThemeMode: (ThemeMode) -> Unit = { mode ->
            themeMode = mode
            AppConfigManager.set(AppConfigManager.GENERAL_SECTION, "Theme", mode.configValue)
            if (AppConfigManager.save()) {
                appLogger.info { "Theme switched: ${mode.configValue}" }
            } else {
                appLogger.warn { "Theme switched to ${mode.configValue} but config save failed" }
            }
        }
        val darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        // 「工具」菜单追加主题三项，当前态加 ✓ 前缀（随 themeMode 变化重建）；
        // XML 菜单精简后可能已无「工具」组，缺失时补一个空组承载主题三项
        val menuConfigsForDisplay = remember(menuConfigs, themeMode) {
            val themeActions = listOf(
                MenuAction(
                    name = if (themeMode == ThemeMode.SYSTEM) "✓ 跟随系统" else "跟随系统",
                    action = "m_theme_system"
                ),
                MenuAction(
                    name = if (themeMode == ThemeMode.LIGHT) "✓ 浅色" else "浅色",
                    action = "m_theme_light"
                ),
                MenuAction(
                    name = if (themeMode == ThemeMode.DARK) "✓ 深色" else "深色",
                    action = "m_theme_dark"
                )
            )
            val withTheme = menuConfigs.map { config ->
                if (config.name == "工具") config.copy(actions = config.actions + themeActions) else config
            }
            if (withTheme.any { it.name == "工具" }) withTheme
            else withTheme + MenuConfig(name = "工具", actions = themeActions)
        }
        // 菜单快捷键映射：解析 menus_ui.xml 中各 action 的 shortcut（如 "Ctrl+N"、"Alt+H"）。
        // 快捷键此前仅在菜单项右侧作文本展示，未注册按键监听；现配合 Window.onKeyEvent 实现。
        val shortcutActions = remember(menuConfigsForDisplay) {
            menuConfigsForDisplay.flatMap { it.actions }
                .mapNotNull { action -> parseShortcut(action.shortcut)?.let { spec -> spec to action } }
        }
        // 菜单 action 统一处理入口（菜单项点击与全局快捷键共用；
        // 已移除入口的 action 分支一并清理，功能去向见 doc/待重构功能.md）
        val handleAction: (MenuAction) -> Unit = { action ->
            when (action.action) {
                "m_theme_system" -> applyThemeMode(ThemeMode.SYSTEM)
                "m_theme_light" -> applyThemeMode(ThemeMode.LIGHT)
                "m_theme_dark" -> applyThemeMode(ThemeMode.DARK)
                else -> handleMenuAction(action, appLogger, logAppender)
            }
        }

        // undecorated 窗口的统一关闭入口（头栏关闭按钮与系统 onCloseRequest 共用一套清理）
        val closeApp: () -> Unit = {
            watcher.stop()
            DbManager.close()
            AppLogger.closeAll()
            exitApplication()
        }

        // 应用管理 Tab（SP-7001）：加载当前设备应用列表（pm list packages -f）。
        // 加载前清旧列表——设备切换/刷新时避免闪现上一台设备的数据
        val loadApps: () -> Unit = {
            val deviceIp = selectedDeviceIp
            if (deviceIp == null) {
                logAppender("[错误] 未选中设备，请先在左侧设备列表选择设备", 4)
            } else {
                appList = emptyList()
                isAppListLoading = true
                scope.launch {
                    val apps = AppListLoader.loadAppList(deviceIp)
                    appList = apps
                    isAppListLoading = false
                    logAppender("[应用列表] 加载 ${apps.size} 个应用", 2)
                }
            }
        }

        Window(
            onCloseRequest = closeApp,
            // 显式默认尺寸：平台默认 ~800x600 下侧栏过窄；1280x800 时侧栏 0.25 ≈ 320px，
            // 对齐 H5 参照稿 .sidebar 的 318px 设计宽度
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
            // 去掉系统原生标题栏：头栏 AppTopBar 即唯一标题栏（品牌区/齿轮/主题/窗口按钮一体，
            // 对齐 H5 参照稿 .titlebar），拖拽移动与双击最大化由 WindowControls.dragArea 承担
            undecorated = true,
            title = "EasyADB",
            icon = iconPainter,
            onKeyEvent = { event ->
                // 全局菜单快捷键（如 Alt+H 打开 APK Helper）：
                // 在 KeyUp 时匹配一次，避免按住按键时重复触发
                if (event.type != KeyEventType.KeyUp) {
                    false
                } else {
                    val matched = shortcutActions
                        .firstOrNull { (spec, _) -> matchesShortcut(event, spec) }?.second
                    if (matched != null) {
                        appLogger.info { "Shortcut triggered: ${matched.shortcut}" }
                        handleAction(matched)
                        true
                    } else {
                        false
                    }
                }
            }
        ) {
            val composeWindow = window
            // undecorated 窗口控制能力：拖拽区 slot + 最小化/最大化/关闭（头栏 AppTopBar 渲染按钮）
            // 最大化/还原走 AWT extendedState（ComposeWindow 即 Frame；WindowState.placement 需随
            // Window state 参数托管，这里直接操作系统级窗口状态更直接）
            val windowControls = WindowControls(
                dragArea = { content -> WindowDraggableArea { content() } },
                minimize = { composeWindow.extendedState = composeWindow.extendedState or Frame.ICONIFIED },
                toggleMaximize = {
                    val maximized = composeWindow.extendedState and Frame.MAXIMIZED_BOTH != 0
                    composeWindow.extendedState =
                        if (maximized) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                },
                close = closeApp
            )
            // Win11 系统原生圆角：DWMWA_WINDOW_CORNER_PREFERENCE=33 → DWMWCP_ROUND=2
            // （系统按 Win11 标准把窗口四角做圆 + 自带系统阴影，不依赖桌面背景）
            LaunchedEffect(Unit) {
                val hwnd = Win32Interop.componentHwnd(composeWindow)
                if (hwnd != null) Win32Interop.setWindowCornerPreference(hwnd, round = true)
            }
            EasyAdbTheme(darkTheme = darkTheme) {
                // ── 侧栏折叠/展开（P12 布局大改） ──
                var sidebarCollapsed by remember { mutableStateOf(false) }

                // ── 设备 Android 版本查询（P12）：上线设备异步 getprop，内存缓存不写 DB ──
                val versionFetcher = remember { DevicePropFetcher() }
                val deviceVersionsFlow = remember { MutableStateFlow<Map<String, String>>(emptyMap()) }
                val deviceVersions by deviceVersionsFlow.collectAsState()
                LaunchedEffect(onlineDevices) {
                    val missing = onlineDevices
                        .filter { it.state == com.easyadb.core.device.DeviceState.DEVICE }
                        .map { it.name }
                        .filter { it.isNotBlank() && it !in deviceVersionsFlow.value }
                    missing.forEach { ip ->
                        scope.launch {
                            val version = versionFetcher.fetchDisplayInfo(ip)
                            if (version != null) {
                                deviceVersionsFlow.value = deviceVersionsFlow.value + (ip to version)
                                // 持久化到 device 表（键为 record.ip 口径，无端口），重启后离线设备可回显；
                                // 成功后原位同步 dbDevicesFlow（不整表 reload），卡片 DB 回退即时可见
                                val dbIp = if ('.' in ip && ':' in ip) ip.substringBeforeLast(':') else ip
                                val r = DbManager.updateDeviceInfo(dbIp, version)
                                if (r.success) {
                                    dbDevicesFlow.value = dbDevicesFlow.value.map {
                                        if (it.ip == dbIp) it.copy(deviceInfo = version) else it
                                    }
                                } else {
                                    appLogger.warn { "device_info persist failed for $dbIp: ${r.error}" }
                                }
                            }
                        }
                    }
                }

                // 启动默认选中：首次收到在线设备列表时，若尚未选中任何设备则自动选中第一台在线设备，
                // 使参数卡/命令/文件等无需用户手动点选即可操作；用户后续点选始终优先（只在 null 时兜底）
                LaunchedEffect(onlineDevices) {
                    if (selectedDeviceIp == null) {
                        // 优先正常 device 状态的设备，兜底取列表第一台。selectedDeviceIp 沿用
                        // record.ip 口径（不含端口）——选中高亮按 `selectedDeviceIp == record.ip`
                        // 比较，watcher 的完整地址 ip:port 若不去端口，带端口设备永远不高亮
                        // （P13 坑点「设备地址端口口径不一致」的同族问题）
                        val first = onlineDevices.firstOrNull {
                            it.state == com.easyadb.core.device.DeviceState.DEVICE && it.name.isNotBlank()
                        } ?: onlineDevices.firstOrNull { it.name.isNotBlank() }
                        first?.let { dev ->
                            val name = dev.name
                            selectedDeviceIp =
                                if ('.' in name && ':' in name) name.substringBeforeLast(':') else name
                            appLogger.info { "Auto-selected first online device: $selectedDeviceIp" }
                        }
                    }
                }

                // 命令行 adb connect 的设备自动入库（对齐 Python on_devices_refreshed 的自动更新）：
                // 设备卡片列表以 DB 为源，watcher 发现的新连接设备若不入库则永不显示。
                // 入库规则同 Python：device/offline 状态都收；"ip:port" 拆分保存、"ip" 默认 5555、
                // 非 IP 序列号（USB/emulator）原样保存 port=0。session 内 autoAddedDeviceNames
                // 去重，防 reload 异步未落地时 INSERT OR REPLACE 重复覆盖（冲掉 alias/connectCount）
                val autoAddedDeviceNames = remember { mutableSetOf<String>() }
                LaunchedEffect(onlineDevices) {
                    val candidates = onlineDevices.filter { it.isConnected() && it.name.isNotBlank() }
                    if (candidates.isEmpty()) return@LaunchedEffect
                    val known = dbDevicesFlow.value.mapTo(mutableSetOf()) {
                        it.ip + if (it.port == "0") "" else ":${it.port}"
                    }
                    var added = false
                    for (dev in candidates) {
                        if (!autoAddedDeviceNames.add(dev.name)) continue
                        if (dev.name in known) continue
                        val name = dev.name
                        val sep = name.lastIndexOf(':')
                        val hasDot = '.' in name
                        val (ip, port) = when {
                            sep > 0 && hasDot -> name.substring(0, sep) to name.substring(sep + 1)
                            hasDot -> name to "5555"
                            else -> name to "0"
                        }
                        val result = DbManager.insertDevice(com.easyadb.core.database.DeviceRecord(ip = ip, port = port))
                        if (result.success) {
                            appLogger.info { "Auto-added watched device to DB: $ip:$port" }
                            logAppender("[发现新设备] $name", 2)
                            added = true
                        } else {
                            autoAddedDeviceNames.remove(name) // 失败允许下次轮询重试
                        }
                    }
                    if (added) reloadDevices(dbDevicesFlow, appLogger)
                }

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
                                        // 使用率 +1（设备列表按连接次数排序，下次启动生效）
                                        DbManager.incrementDeviceConnectCount(record.ip)
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
                        onAddDevice = {
                            appLogger.info { "DeviceList: 添加设备" }
                            showNewConnectDialog = true
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
                    menuConfigs = menuConfigsForDisplay,
                    onMenuAction = handleAction,
                    // 顶栏主题切换按钮（H5 themeToggle）：暗色切浅色、浅色切暗色
                    themeIsDark = darkTheme,
                    onToggleTheme = {
                        applyThemeMode(if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                    },
                    dbDevices = dbDevices,
                    onlineDevices = onlineDevices,
                    deviceVersions = deviceVersions,
                    selectedDeviceIp = selectedDeviceIp,
                    deviceListCallbacks = deviceListCallbacks,
                    onCollapseDeviceList = { sidebarCollapsed = true },
                    onExpandDeviceList = { sidebarCollapsed = false },
                    sidebarCollapsed = sidebarCollapsed,
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
                    // 按键模拟 Tab：自定义 KEYCODE 键值（"+" 拼接逐条发送，间隔 200ms，同 H5 稿）
                    onCustomKey = { keycodes: List<Int> ->
                        val deviceIp = selectedDeviceIp
                        if (deviceIp == null) {
                            logAppender("[错误] 未选中设备，请先在左侧设备列表选择设备", 4)
                        } else {
                            scope.launch {
                                if (keycodes.size > 1) {
                                    logAppender("> adb shell input keyevent ${keycodes.joinToString("+")}（逐条发送 · 间隔 200ms）", 2)
                                }
                                keycodes.forEachIndexed { index, kc ->
                                    if (index > 0) delay(200)
                                    val cmd = "adb -s $deviceIp shell input keyevent $kc"
                                    logAppender("> $cmd", 2)
                                    val result = adbExecutor.execAdbCmd(cmd)
                                    if (!result.success) {
                                        logAppender("[错误] KEYCODE $kc 发送失败 (exitCode=${result.exitCode})", 4)
                                    }
                                }
                            }
                        }
                    },
                    // 常用命令 Tab：执行模板命令（shell/tcpip 定向到当前设备，version/help 为本机命令原样执行）
                    onRunCommand = { cmd: String ->
                        val deviceIp = selectedDeviceIp
                        if (deviceIp == null) {
                            logAppender("[错误] 未选中设备，请先在左侧设备列表选择设备", 4)
                        } else {
                            val fullCmd = when {
                                cmd.startsWith("adb shell ") || cmd.startsWith("adb tcpip") ->
                                    "adb -s $deviceIp ${cmd.removePrefix("adb ")}"
                                else -> cmd
                            }
                            scope.launch {
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
                    },
                    // 常用命令 Tab：命令已复制，控制台回显
                    onCopyCommand = { cmd: String ->
                        logAppender("命令已复制到剪贴板：$cmd", 2)
                    },
                    // P12 新增回调：安装卡拖入 APK 直接安装 + 文件传输 + chips 联动
                    onApkDropped = { apkPath ->
                        val deviceIp = selectedDeviceIp
                        if (deviceIp == null) {
                            logAppender("[错误] 未选中设备，请先在左侧设备列表选择设备", 4)
                        } else {
                            val fileName = apkPath.replace('\\', '/').substringAfterLast('/')
                            scope.launch {
                                val cmd = "adb -s $deviceIp install -r \"$apkPath\""
                                logAppender("> $cmd", 2)
                                logAppender("[安装] $fileName 安装中...", 2)
                                val result = adbExecutor.execAdbCmd(cmd)
                                if (result.success) {
                                    logAppender(result.output.take(500), 2)
                                    logAppender("[安装成功] $fileName", 2)
                                } else {
                                    logAppender("[安装失败] ${result.output.take(500)}", 4)
                                }
                            }
                        }
                    },
                    // 应用管理 Tab（SP-7001）：列表状态 + 加载/刷新 + 提取 APK
                    apps = appList,
                    isAppsLoading = isAppListLoading,
                    onLoadApps = loadApps,
                    onExtractApk = { app ->
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
                    },
                    // 文件传输 Tab（SP-5001）：宿主持有的传输任务中心 + 控制台回显
                    transferCenter = transferCenter,
                    onFileTransferLog = logAppender,
                    // P6 控制台 + Logcat 参数
                    consoleLogFlow = consoleLogFlow,
                    logcatStream = logcatStream,
                    // P8 实时预览参数
                    mirrorState = mirrorState,
                    mirrorBinaryReady = scrcpyLauncher.isBinaryReady(),
                    mirrorBinaryPath = scrcpyLauncher.primaryScrcpyExe.absolutePath,
                    onMirrorStart = {
                        val deviceIp = selectedDeviceIp
                        if (deviceIp == null) {
                            logAppender("[错误] 未选中设备，请先在左侧设备列表选择设备", 4)
                        } else {
                            scope.launch {
                                when (val result = scrcpyLauncher.start(deviceIp)) {
                                    is MirrorStartResult.Success ->
                                        logAppender("[镜像] scrcpy 已启动：$deviceIp（独立窗口「${ScrcpyLauncher.WINDOW_TITLE}」）", 2)
                                    is MirrorStartResult.BinaryMissing -> {
                                        logAppender("[镜像] 系统找不到 scrcpy：${result.expectedPath}", 4)
                                        logAppender(
                                            "[镜像] 请从 https://github.com/Genymobile/scrcpy/releases 下载 win64 版本" +
                                                "（如 scrcpy-win64-v3.3.4.zip），解压后将文件夹改名为 scrcpy-win64，放入程序数据目录的 tool/ 下" +
                                                "或运行目录的 tool/ 下；后续升级只需替换 scrcpy-win64 目录内的文件。", 4
                                        )
                                    }
                                    is MirrorStartResult.StartFailed ->
                                        logAppender("[镜像] 启动失败：${result.reason}（请确认设备 adb 连接正常、scrcpy 版本可用）", 4)
                                    MirrorStartResult.AlreadyRunning ->
                                        logAppender("[镜像] 实时预览已在运行中", 3)
                                }
                            }
                        }
                    },
                    onMirrorStop = {
                        scrcpyLauncher.stop()
                        logAppender("[镜像] 已请求关闭实时预览", 2)
                    },
                    windowControls = windowControls
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
                                    // 写入数据库（已存在的设备合并旧记录：别名/信息/使用率保留，连接次数 +1）
                                    val (ip, port) = com.easyadb.core.util.NetworkUtils.parseIpPort(deviceIp)
                                    val existing = DbManager.getDevice(ip).data
                                    val deviceRecord = com.easyadb.core.database.DeviceRecord(
                                        ip = ip,
                                        port = port,
                                        alias = existing?.alias.orEmpty(),
                                        deviceInfo = existing?.deviceInfo.orEmpty(),
                                        active = 1,
                                        connectCount = (existing?.connectCount ?: 0) + 1
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
                        onDismiss = {
                            recordJob?.cancel()
                            recordJob = null
                            isRecording = false
                            showScreenRecordDialog = false
                        },
                        onRecordStart = { options ->
                            val deviceIp = selectedDeviceIp
                            if (deviceIp != null) {
                                isRecording = true
                                remainingSeconds = options.timeLimit
                                recordJob = scope.launch {
                                    val tmpPath = "/sdcard/easy_screenrecord.mp4"
                                    val cmd = buildString {
                                        append("screenrecord --verbose --time-limit ${options.timeLimit}")
                                        if (options.bitRate != null) append(" --bit-rate ${options.bitRate}")
                                        if (options.customResolution.isNotBlank()) append(" --size ${options.customResolution}")
                                        if (options.rotate) append(" --rotate")
                                        append(" $tmpPath")
                                    }
                                    // 并发倒计时：仅 UI 显示，录制靠 --time-limit 自结束
                                    val countdown = launch {
                                        for (i in options.timeLimit downTo 1) {
                                            remainingSeconds = i
                                            kotlinx.coroutines.delay(1000)
                                        }
                                    }
                                    logAppender("[录屏] 开始录制 ${options.timeLimit} 秒（存到设备 $tmpPath）", 2)
                                    try {
                                        // 无超时 flow；协程 cancel 时 executeFlow 自动 destroyForcibly 终止 adb 进程
                                        adbExecutor.screenRecordFlow(deviceIp, cmd).collect { /* 忽略 screenrecord verbose 输出 */ }
                                    } finally {
                                        countdown.cancel()
                                    }
                                    // 录制自然结束：临时文件留设备，不自动拉取；由「拉取」按钮选保存地址并 pull
                                    logAppender("[录屏] 录制完成，点「拉取」保存到本地", 2)
                                    isRecording = false
                                }
                            } else {
                                logAppender("[错误] 未选中设备", 4)
                            }
                        },
                        onRecordStop = {
                            recordJob?.cancel()
                            recordJob = null
                            isRecording = false
                            logAppender("[录屏] 已终止，点「拉取」保存到本地", 3)
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
                    tab = attrs["tab"] ?: "quick",
                    size = attrs["size"] ?: "small",
                    desc = attrs["desc"] ?: "",
                    accent = attrs["accent"] ?: "",
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

/** 快捷键组合描述（由 menus_ui.xml 的 shortcut 属性解析而来，如 "Ctrl+N"、"Alt+H"） */
private data class ShortcutSpec(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val key: Key
)

/** shortcut 字母键名到 Compose Key 的映射（覆盖 A-Z，新增字母快捷键无需改代码） */
private val SHORTCUT_KEY_MAP: Map<String, Key> = mapOf(
    "A" to Key.A, "B" to Key.B, "C" to Key.C, "D" to Key.D, "E" to Key.E,
    "F" to Key.F, "G" to Key.G, "H" to Key.H, "I" to Key.I, "J" to Key.J,
    "K" to Key.K, "L" to Key.L, "M" to Key.M, "N" to Key.N, "O" to Key.O,
    "P" to Key.P, "Q" to Key.Q, "R" to Key.R, "S" to Key.S, "T" to Key.T,
    "U" to Key.U, "V" to Key.V, "W" to Key.W, "X" to Key.X, "Y" to Key.Y,
    "Z" to Key.Z
)

/**
 * 解析 XML 中的 shortcut 描述为 [ShortcutSpec]。
 * 格式约定为「修饰键+字母」的加号分隔（Ctrl+N / Alt+H / Shift+S），最后一个段为字母键；
 * 空串或无法识别的键名返回 null（该快捷键不参与按键匹配）。
 */
private fun parseShortcut(spec: String): ShortcutSpec? {
    if (spec.isBlank()) return null
    val parts = spec.uppercase().split("+").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val key = SHORTCUT_KEY_MAP[parts.last()] ?: return null
    return ShortcutSpec(
        ctrl = "CTRL" in parts,
        alt = "ALT" in parts,
        shift = "SHIFT" in parts,
        key = key
    )
}

/** 判断按键事件是否命中 [spec] 描述的组合键（调用方负责按 KeyUp 过滤避免重复触发） */
private fun matchesShortcut(event: KeyEvent, spec: ShortcutSpec): Boolean =
    event.isCtrlPressed == spec.ctrl &&
        event.isAltPressed == spec.alt &&
        event.isShiftPressed == spec.shift &&
        event.key == spec.key

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
 * 处理菜单栏操作。
 * P4 阶段仅记录日志，后续阶段实现具体操作（P5/P7）。
 */
private fun handleMenuAction(action: MenuAction, logger: AppLogger, logAppender: (String, Int) -> Unit) {
    logger.info { "Menu action: ${action.name} (action=${action.action})" }
    logAppender("[未实现] 菜单操作: ${action.name}", 3)
}


