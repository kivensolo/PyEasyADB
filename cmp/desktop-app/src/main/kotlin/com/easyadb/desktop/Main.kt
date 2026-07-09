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
import com.easyadb.core.config.MenuAction
import com.easyadb.core.config.MenuConfig
import com.easyadb.core.config.XmlConfigLoader
import com.easyadb.core.database.DbManager
import com.easyadb.core.device.DevicesWatcher
import com.easyadb.core.log.AppLogger
import com.easyadb.core.log.LogConfig
import com.easyadb.ui.designsystem.EasyAdbTheme
import com.easyadb.ui.home.MainWindowScreen
import com.easyadb.ui.home.rememberDefaultToolBarActions
import com.easyadb.ui.devicelist.DeviceAliasEditDialog
import com.easyadb.ui.devicelist.DeviceListCallbacks
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
    scope.launch {
        val result = DbManager.initialize(AppPathsConfig.dbFile)
        if (result.success) {
            appLogger.info { "Database initialized at ${AppPathsConfig.dbFile}" }
            reloadDevices(dbDevicesFlow, appLogger)
        } else {
            appLogger.error { "Database init failed: ${result.error}" }
        }
    }

    // ── Step 4: Device Watcher ──
    val watcher = DevicesWatcher()
    watcher.start(intervalSeconds = 2, scope = scope)
    appLogger.info { "DevicesWatcher started" }

    // ── Step 5: Load menu & cmd configs (优先从 classpath 加载) ──
    val menuConfigs = loadMenuConfigFromClasspath(appLogger)
    val cmdGroups = loadCmdConfigFromClasspath(appLogger)

    // ── Icon ──
    val iconPainter = try {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("logo.png")
        stream?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }
    } catch (_: Exception) { null }

    // ── UI ──
    application {
        val onlineDevices by watcher.devices.collectAsState(initial = emptyList())
        val dbDevices by dbDevicesFlow.asStateFlow().collectAsState()
        var selectedDeviceIp by remember { mutableStateOf<String?>(null) }
        // 右键"备注设置"要编辑的目标设备；非 null 时弹出 DeviceAliasEditDialog。
        var aliasEditTarget by remember { mutableStateOf<com.easyadb.core.database.DeviceRecord?>(null) }

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
                    onAddDevice = { appLogger.info { "ToolBar: 新建设备连接" } },
                    onOpenShell = { appLogger.info { "ToolBar: 打开 Shell" } },
                    onRoot = { appLogger.info { "ToolBar: Root" } },
                    onUnroot = { appLogger.info { "ToolBar: Unroot" } }
                )

                val deviceListCallbacks = remember(scope, dbDevicesFlow) {
                    DeviceListCallbacks(
                        onDeviceClick = { record -> selectedDeviceIp = record.ip },
                        onDeviceDoubleClick = { record ->
                            appLogger.info { "Device double-click: ${record.ip} (connect)" }
                            selectedDeviceIp = record.ip
                        },
                        onDeviceAliasEdit = { record ->
                            appLogger.info { "Device alias edit: ${record.ip}" }
                            aliasEditTarget = record
                        },
                        onDeviceDisconnect = { record ->
                            appLogger.info { "Device disconnect: ${record.ip}" }
                        },
                        onDeviceRemove = { record ->
                            appLogger.info { "Device remove: ${record.ip}" }
                            scope.launch {
                                val r = DbManager.deleteDevice(record.ip)
                                if (r.success) {
                                    watcher.onDeviceDeleted()
                                    reloadDevices(dbDevicesFlow, appLogger)
                                }
                            }
                        },
                        onCommandClick = { item ->
                            appLogger.info { "Command click: ${item.name}" }
                        },
                        onCommandDoubleClick = { item ->
                            appLogger.info { "Command double-click: ${item.name} cmd=${item.cmd}" }
                        }
                    )
                }

                MainWindowScreen(
                    modifier = Modifier.fillMaxSize(),
                    toolBarActions = toolBarActions,
                    menuConfigs = menuConfigs,
                    onMenuAction = { action -> handleMenuAction(action, appLogger) },
                    dbDevices = dbDevices,
                    onlineDevices = onlineDevices,
                    cmdGroups = cmdGroups,
                    selectedDeviceIp = selectedDeviceIp,
                    deviceListCallbacks = deviceListCallbacks,
                    bottomTabDefaultHeight = 200.dp
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
                                    reloadDevices(dbDevicesFlow, appLogger)
                                } else {
                                    appLogger.error { "Alias update failed: ${r.error}" }
                                }
                            }
                            aliasEditTarget = null
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
private fun handleMenuAction(action: MenuAction, logger: AppLogger) {
    logger.info { "Menu action: ${action.name} (action=${action.action})" }
    when (action.action) {
        "m_close_app" -> logger.info { "Exit requested via menu" }
        else -> logger.info { "Menu action ${action.action} not yet implemented" }
    }
}
