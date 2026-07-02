package com.easyadb.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.easyadb.core.config.AppConfigManager
import com.easyadb.core.config.AppPathsConfig
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    appLogger.info { "EasyADB CMP v2.0.0 - P3 Main Window active" }
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
    scope.launch {
        val result = DbManager.initialize(AppPathsConfig.dbFile)
        if (result.success) {
            appLogger.info { "Database initialized at ${AppPathsConfig.dbFile}" }
        } else {
            appLogger.error { "Database init failed: ${result.error}" }
        }
    }

    // ── Step 4: Device Watcher ──
    val watcher = DevicesWatcher()
    watcher.start(intervalSeconds = 2, scope = scope)
    appLogger.info { "DevicesWatcher started" }

    // ── Load Menu Config（优先从 classpath 加载，不受工作目录影响） ──
    val menuConfigs = loadMenuConfigFromClasspath(appLogger)

    // ── Icon ──
    val iconPainter = try {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("logo.png")
        stream?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }
    } catch (_: Exception) { null }

    // ── UI ──
    application {
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

                MainWindowScreen(
                    modifier = Modifier.fillMaxSize(),
                    toolBarActions = toolBarActions,
                    menuConfigs = menuConfigs,
                    onMenuAction = { action -> handleMenuAction(action, appLogger) },
                    bottomTabDefaultHeight = 200.dp
                )
            }
        }
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
        val actions = mutableListOf<com.easyadb.core.config.MenuAction>()
        val actionNodes = menuElement.childNodes

        for (j in 0 until actionNodes.length) {
            val actionElement = actionNodes.item(j)
            if (actionElement !is org.w3c.dom.Element || actionElement.tagName != "action") continue
            val actionName = actionElement.getAttribute("name")
            // 读取 <attr> 子元素
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
            actions.add(com.easyadb.core.config.MenuAction(name = actionName, shortcut = shortcut, icon = icon, action = actionAttr))
        }
        result.add(MenuConfig(name = menuName, actions = actions))
    }
    stream.close()
    return result
}

/**
 * 处理菜单栏操作。
 * P3 阶段仅记录日志，后续阶段实现具体操作。
 */
private fun handleMenuAction(action: MenuAction, logger: AppLogger) {
    logger.info { "Menu action: ${action.name} (action=${action.action})" }
    when (action.action) {
        "m_close_app" -> {
            logger.info { "Exit requested via menu" }
        }
        else -> {
            logger.info { "Menu action ${action.action} not yet implemented" }
        }
    }
}