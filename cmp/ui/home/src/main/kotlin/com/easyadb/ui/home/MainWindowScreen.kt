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
// 占位面板（底部 Tab P6 阶段替换）
// ─────────────────────────────────────────────────────────

@Composable
private fun ConsolePanelPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(text = "控制台", fontSize = 12.sp, color = EasyAdbColors.TextSecondary)
    }
}

@Composable
private fun LogcatPanelPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(text = "实时日志", fontSize = 12.sp, color = EasyAdbColors.TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────
// 主窗口布局
// ─────────────────────────────────────────────────────────

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
    onFunctionItemClick: (FunctionItem, AppParamState) -> Unit = { _, _ -> }
) {
    val bottomTabs = remember {
        listOf(
            BottomTab(id = "console", title = "控制台") { ConsolePanelPlaceholder() },
            BottomTab(id = "logcat", title = "实时日志") { LogcatPanelPlaceholder() }
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