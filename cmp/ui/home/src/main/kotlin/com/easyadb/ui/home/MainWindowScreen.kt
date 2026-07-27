package com.easyadb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.ui.console.ConsolePanel
import com.easyadb.ui.console.LogEntry
import com.easyadb.ui.logcat.LogcatPanel
import com.easyadb.core.adb.LogcatStream
import com.easyadb.core.config.CmdGroup
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.config.FunctionTemplate
import com.easyadb.core.config.MenuAction
import com.easyadb.core.config.MenuConfig
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbColors
import com.easyadb.ui.devicelist.DeviceListCallbacks
import com.easyadb.ui.devicelist.DeviceListPanel
import com.easyadb.ui.functions.AppParamState
import com.easyadb.ui.functions.FunctionPanel
import com.easyadb.ui.home.components.BottomTab
import com.easyadb.ui.home.components.BottomTabHost
import com.easyadb.ui.home.components.SplitPane
import kotlinx.coroutines.flow.MutableStateFlow

// ─────────────────────────────────────────────────────────
// 自定义菜单栏
// ─────────────────────────────────────────────────────────

@Composable
private fun CustomMenuBar(
    menuConfigs: List<MenuConfig>,
    onMenuAction: (MenuAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp,
        color = MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            menuConfigs.forEach { config ->
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Text(
                        text = config.name,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clickable { expanded = true },
                        fontSize = 12.sp,
                        color = EasyAdbColors.TextPrimary
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        config.actions.forEach { action ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clickable { expanded = false; onMenuAction(action) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = action.name,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (action.shortcut.isNotBlank()) {
                                        Text(
                                            text = action.shortcut,
                                            fontSize = 10.sp,
                                            color = EasyAdbColors.TextSecondary,
                                            modifier = Modifier.padding(start = 24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 工具栏
// ─────────────────────────────────────────────────────────

data class ToolBarAction(
    val id: String,
    val label: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit
)

@Composable
private fun ToolBar(
    actions: List<ToolBarAction>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        color = EasyAdbColors.SurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                TextButton(
                    onClick = action.onClick,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = EasyAdbColors.TextPrimary)
                ) {
                    action.icon()
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = action.label, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 主窗口布局
// ─────────────────────────────────────────────────────────

/**
 * 主窗口布局 —— 整个应用的根级 UI 组合。
 *
 * ## 布局结构
 * ```
 * Column
 * ├── CustomMenuBar     (菜单栏，仅 menuConfigs 非空时显示)
 * ├── ToolBar           (工具栏：新建设备连接 / Shell / Root / Unroot)
 * ├── SplitPane         (水平拖拽分割面板)
 * │     ├── DeviceListPanel  (左栏：设备树 + 命令树)
 * │     └── FunctionPanel    (右栏：功能模板按钮网格) — P5
 * └── BottomTabHost     (底部标签栏，可拖拽调整高度)
 *       ├── ConsolePanel     (控制台：应用自身日志) — P6
 *       └── LogcatPanel      (实时日志：设备 logcat) — P6
 * ```
 *
 * @param toolBarActions 工具栏按钮列表，每个按钮含 id / label / icon / onClick
 * @param menuConfigs 菜单栏配置（从 menus_ui.xml 加载）
 * @param onMenuAction 菜单项点击回调
 * @param dbDevices 数据库中的设备列表（含离线设备）
 * @param onlineDevices DeviceWatcher 实时推送的在线设备列表
 * @param cmdGroups 命令树配置（从 cmdConfig.xml 加载）
 * @param selectedDeviceIp 当前选中的设备 IP（高亮显示）
 * @param deviceListCallbacks 设备/命令树的交互回调集合（单击/双击/右键菜单）
 * @param bottomTabDefaultHeight 底部控制台标签栏的初始高度（dp），如 200.dp
 *
 * ### P5 功能区参数
 * @param functionTemplates 功能模板列表（从 function_templates.xml 加载）
 * @param dbPackages 数据库中的已保存包名列表（供下拉选择）
 * @param onPackageAdd 用户新增包名时的回调（写入 package 表）
 * @param onPackageDelete 用户删除包名时的回调
 * @param onFunctionItemClick 功能按钮点击回调：(FunctionItem, AppParamState) -> Unit
 *
 * ### P6 控制台 + Logcat 参数
 * @param consoleLogFlow 控制台日志流（MutableStateFlow，支持清空操作）
 * @param logcatStream 设备 logcat 流管理器（控制启动/停止/过滤）
 */
@Composable
fun MainWindowScreen(
    modifier: Modifier = Modifier,
    toolBarActions: List<ToolBarAction> = emptyList(),
    menuConfigs: List<MenuConfig> = emptyList(),
    onMenuAction: (MenuAction) -> Unit = {},
    dbDevices: List<DeviceRecord> = emptyList(),
    onlineDevices: List<DeviceInfo> = emptyList(),
    cmdGroups: List<CmdGroup> = emptyList(),
    selectedDeviceIp: String? = null,
    deviceListCallbacks: DeviceListCallbacks = DeviceListCallbacks(),
    bottomTabDefaultHeight: Dp = 200.dp,
    // P5 功能区参数
    functionTemplates: List<FunctionTemplate> = emptyList(),
    dbPackages: List<String> = emptyList(),
    onPackageAdd: (String) -> Unit = {},
    onPackageDelete: (String) -> Unit = {},
    onFunctionItemClick: (FunctionItem, AppParamState) -> Unit = { _, _ -> },
    // P6 控制台 + Logcat 参数
    consoleLogFlow: MutableStateFlow<List<LogEntry>> = MutableStateFlow(emptyList()),
    logcatStream: LogcatStream = LogcatStream()
) {
    val bottomTabs = remember(selectedDeviceIp) {
        listOf(
            BottomTab(id = "console", title = "控制台") {
                ConsolePanel(
                    logFlow = consoleLogFlow,
                    modifier = Modifier.fillMaxSize()
                )
            },
            BottomTab(id = "logcat", title = "实时日志") {
                LogcatPanel(
                    deviceIp = selectedDeviceIp,
                    logcatStream = logcatStream,
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        // 菜单栏 + 1px 分隔线
        if (menuConfigs.isNotEmpty()) {
            Column {
                CustomMenuBar(menuConfigs = menuConfigs, onMenuAction = onMenuAction)
                Divider(color = EasyAdbColors.Divider, thickness = 1.dp)
            }
        }

        // 工具栏
        ToolBar(actions = toolBarActions, modifier = Modifier.wrapContentHeight())

        // 水平分割面板：设备树 | 功能面板
        SplitPane(
            modifier = Modifier.fillMaxWidth().weight(1f),
            initialFraction = 0.2f,
            leftPanel = { m ->
                DeviceListPanel(
                    dbDevices = dbDevices,
                    onlineDevices = onlineDevices,
                    cmdGroups = cmdGroups,
                    selectedDeviceIp = selectedDeviceIp,
                    callbacks = deviceListCallbacks,
                    modifier = m
                )
            },
            rightPanel = { m ->
                FunctionPanel(
                    functionTemplates = functionTemplates,
                    deviceIp = selectedDeviceIp,
                    dbPackages = dbPackages,
                    onPackageAdd = onPackageAdd,
                    onPackageDelete = onPackageDelete,
                    onItemClick = { item, state -> onFunctionItemClick(item, state) },
                    modifier = m
                )
            }
        )

        // 底部 Tab
        BottomTabHost(
            modifier = Modifier.fillMaxWidth(),
            tabs = bottomTabs,
            defaultHeight = bottomTabDefaultHeight
        )
    }
}

// ─────────────────────────────────────────────────────────
// 默认工具栏操作
// ─────────────────────────────────────────────────────────

@Composable
fun rememberDefaultToolBarActions(
    onAddDevice: () -> Unit = {},
    onOpenShell: () -> Unit = {},
    onRoot: () -> Unit = {},
    onUnroot: () -> Unit = {}
): List<ToolBarAction> {
    return remember {
        listOf(
            ToolBarAction(id = "add_device", label = "新建设备连接",
                icon = { Icon(AppIcons.AddNew, null, Modifier.size(18.dp)) },
                onClick = onAddDevice),
            ToolBarAction(id = "open_shell", label = "打开 Shell",
                icon = { Icon(AppIcons.Terminal, null, Modifier.size(18.dp)) },
                onClick = onOpenShell),
            ToolBarAction(id = "root", label = "Root",
                icon = { Icon(AppIcons.Build, null, Modifier.size(18.dp)) },
                onClick = onRoot),
            ToolBarAction(id = "unroot", label = "Unroot",
                icon = { Icon(AppIcons.Settings, null, Modifier.size(18.dp)) },
                onClick = onUnroot)
        )
    }
}