@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.LogcatStream
import com.easyadb.core.adb.MirrorState
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.config.FunctionTemplate
import com.easyadb.core.config.MenuAction
import com.easyadb.core.config.MenuConfig
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground
import com.easyadb.ui.devicelist.DeviceListCallbacks
import com.easyadb.ui.devicelist.DeviceListPanel
import com.easyadb.ui.functions.AppParamState
import com.easyadb.ui.functions.CommandsPanel
import com.easyadb.ui.functions.FunctionPanel
import com.easyadb.ui.functions.RemoteKeyPad
import com.easyadb.ui.functions.TemplateGrid
import com.easyadb.ui.mirror.MirrorPanel
import com.easyadb.ui.console.LogEntry
import com.easyadb.ui.home.components.EmbeddedConsoleHost
import com.easyadb.ui.home.components.MenuBarDropdown
import com.easyadb.ui.home.components.SplitPane
import kotlinx.coroutines.flow.MutableStateFlow

// ─────────────────────────────────────────────────────────
// 顶部应用头栏（P12 布局大改）
// ─────────────────────────────────────────────────────────

/**
 * 顶部应用头栏（44dp）：品牌标识 + [»]侧栏展开按钮（折叠时） + 行尾设置齿轮
 * 与明暗主题切换按钮（对齐 HTML 参照稿 titlebar win-controls 的 .wc 样式）。
 * 「新建设备连接」入口在设备列表区头部；当前设备 chip 在 ContentTabRow 右侧。
 *
 * 齿轮按钮展开精简后的菜单（当前仅「工具」主题三项）；主题按钮按 H5 行为：
 * 暗色显示太阳（点击切浅色）、浅色显示月亮（点击切暗色），SYSTEM 态按解析值。
 */
@Composable
private fun AppTopBar(
    sidebarCollapsed: Boolean,
    onExpandSidebar: () -> Unit,
    themeIsDark: Boolean,
    onToggleTheme: () -> Unit,
    menuConfigs: List<MenuConfig>,
    onMenuAction: (MenuAction) -> Unit
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Surface(color = easyAdbColors.Surface, elevation = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = easyAdbTokens.spaceMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 品牌标识：品牌蓝竖条 + 应用名
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(easyAdbTokens.shapeSm)
                        .background(easyAdbColors.Primary)
                )
                Spacer(Modifier.width(easyAdbTokens.spaceSm))
                Text(
                    text = "EasyADB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = easyAdbColors.TextPrimary
                )
                Spacer(Modifier.width(easyAdbTokens.spaceLg))

                // 侧栏折叠时显示 [»] 展开按钮
                if (sidebarCollapsed) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(easyAdbTokens.shapeMd)
                            .hoverBackground()
                            .clickable(interactionSource = null, indication = null, onClick = onExpandSidebar),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowRight,
                            contentDescription = "展开设备列表",
                            tint = easyAdbColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(easyAdbTokens.spaceXs))
                }

                Spacer(Modifier.weight(1f))

                // 设置齿轮按钮（H5 .wc：悬停圆角块，点击展开精简菜单）
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(easyAdbTokens.shapeMd)
                            .hoverBackground()
                            .clickable(interactionSource = null, indication = null) {
                                overflowOpen = !overflowOpen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = EasyAdbTileIcons.SettingsGear,
                            contentDescription = "设置",
                            tint = easyAdbColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    MenuBarDropdown(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false }
                    ) {
                        menuConfigs.forEach { config ->
                            // 组名小节头
                            Text(
                                text = config.name,
                                fontSize = 10.sp,
                                color = easyAdbColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                            config.actions.forEach { action ->
                                MenuOverflowItem(action = action) {
                                    overflowOpen = false
                                    onMenuAction(action)
                                }
                            }
                        }
                    }
                }

                // 明暗主题切换按钮（H5 themeToggle：暗色显太阳切浅色，浅色显月亮切暗色）
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(easyAdbTokens.shapeMd)
                        .hoverBackground()
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onToggleTheme
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (themeIsDark) EasyAdbTileIcons.Sun else EasyAdbTileIcons.Moon,
                        contentDescription = if (themeIsDark) "切换到浅色模式" else "切换到暗色模式",
                        tint = easyAdbColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Divider(color = easyAdbColors.Divider, thickness = 1.dp)
        }
    }
}

/** ⋮ 溢出菜单项：圆角行 chip + 悬浮底，与头栏语言一致。 */
@Composable
private fun MenuOverflowItem(action: MenuAction, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .height(26.dp)
            .clip(easyAdbTokens.shapeMd)
            .hoverBackground()
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = action.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (action.shortcut.isNotBlank()) {
                Text(
                    text = action.shortcut,
                    fontSize = 10.sp,
                    color = easyAdbColors.TextSecondary,
                    modifier = Modifier.padding(start = 24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 内容区五 Tab 行（P12 布局大改）
// ─────────────────────────────────────────────────────────

/** 内容区 Tab 索引：0=快捷操作 1=常用命令 2=按键模拟 3=文件传输 4=实时预览 */
private const val TAB_QUICK = 0
private const val TAB_CMDS = 1
private const val TAB_KEYS = 2
private const val TAB_FILES = 3
private const val TAB_MIRROR = 4

private val CONTENT_TAB_TITLES = listOf("快捷操作", "常用命令", "按键模拟", "文件传输", "实时预览")

/**
 * 五 Tab 下划线行（对齐 HTML 参照稿 tabs）：高 44dp、文字 14sp、选中态主文字色
 * + 底部与文字等宽 2.5dp Primary 指示条；行尾为当前设备 chip（只读展示，
 * 选中/切换仍在左侧设备列表，未选中设备时不显示）。
 */
@Composable
private fun ContentTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedDeviceIp: String?,
    dbDevices: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = easyAdbTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CONTENT_TAB_TITLES.forEachIndexed { index, title ->
            val isSelected = selected == index
            Column(
                modifier = Modifier
                    // IntrinsicSize.Min：宽度收窄到文字宽度，等宽 ink 条才不会把同行其余 Tab 挤出
                    .width(IntrinsicSize.Min)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(index) }
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(easyAdbTokens.shapeSm)
                        .background(if (isSelected) easyAdbColors.Primary else Color.Transparent)
                )
            }
            Spacer(Modifier.width(easyAdbTokens.spaceLg))
        }

        Spacer(Modifier.weight(1f))

        // 当前设备 chip（对齐 HTML tabs-right .cur-dev：显示器图标 + 名称 · 地址）
        if (selectedDeviceIp != null) {
            // 选中值是 DeviceRecord.ip（不含端口），在线比对需拼上端口得到完整地址
            val record = remember(dbDevices, selectedDeviceIp) {
                dbDevices.firstOrNull { it.ip == selectedDeviceIp }
            }
            val deviceAddr = remember(record, selectedDeviceIp) {
                record?.let { r -> r.ip + (if (r.port == "0") "" else ":${r.port}") } ?: selectedDeviceIp!!
            }
            val isOnline = remember(onlineDevices, deviceAddr) {
                onlineDevices.any { it.name == deviceAddr }
            }
            CurrentDeviceChip(
                alias = record?.alias.orEmpty(),
                deviceAddr = deviceAddr,
                isOnline = isOnline
            )
        }
    }
}

/**
 * 当前设备 chip（对齐 HTML .cur-dev）：状态点（在线绿/未连接红）+ 显示器线稿
 * 图标 + 「当前设备 名称 · 地址」胶囊；别名非空显示「别名 · 地址」，否则只
 * 显示地址（只读展示）。
 */
@Composable
private fun CurrentDeviceChip(
    alias: String,
    deviceAddr: String,
    isOnline: Boolean
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(easyAdbColors.Hover)
            .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isOnline) easyAdbColors.StatusOnline else easyAdbColors.AccentRed)
        )
        Spacer(Modifier.width(9.dp))
        Icon(
            imageVector = EasyAdbTileIcons.Monitor,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = easyAdbColors.PrimaryLight
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = buildAnnotatedString {
                append("当前设备 ")
                withStyle(
                    SpanStyle(fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextPrimary)
                ) {
                    append(if (alias.isNotBlank()) alias else deviceAddr)
                }
                if (alias.isNotBlank()) {
                    append(" · ")
                    append(deviceAddr)
                }
            },
            fontSize = 12.5.sp,
            color = easyAdbColors.TextSecondary,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────
// 主窗口布局
// ─────────────────────────────────────────────────────────

/**
 * 主窗口布局 —— 整个应用的根级 UI 组合（P12 布局大改重构）。
 *
 * ## 布局结构
 * ```
 * Column
 * ├── AppTopBar           (44dp：品牌 + [»]展开 + ⋮ 菜单；「新建设备连接」在设备列表区头部)
 * └── SplitPane           (水平拖拽分割，支持左栏折叠)
 *       ├── 左栏: DeviceListPanel (设备卡片列表，可折叠为 0 宽)
 *       └── 右栏 Column
 *             ├── ContentTabRow  (五 Tab 下划线行 + 行尾当前设备 chip：快捷操作|常用命令|按键模拟|文件传输|实时预览)
 *             ├── Tab 内容 (weight 1f)
 *             │     ├── 快捷操作：参数卡(2×2 网格) + 功能卡(13 项小卡 + 推送/拉取大卡，安装卡虚线拖拽)
 *             │     ├── 按键模拟：RemoteKeyPad 遥控器（10 键）
 *             │     ├── 文件传输：APK提取/推送文件/拉取文件 3 大卡
 *             │     └── 实时预览：MirrorPanel（原样迁入）
 *             ├── Divider
 *             └── EmbeddedConsoleHost (内嵌双 Tab 控制台：控制台|实时日志，可拖拽调高)
 * ```
 *
 * @param menuConfigs 菜单配置（从 menus_ui.xml 加载，收纳进头栏 ⋮ 溢出菜单）
 * @param onMenuAction 菜单项点击回调
 * @param dbDevices 数据库中的设备列表（含离线设备）
 * @param onlineDevices DevicesWatcher 实时推送的在线设备列表
 * @param deviceVersions 设备 Android 版本缓存（ip→version，DevicePropFetcher 异步查询）
 * @param selectedDeviceIp 当前选中的设备 IP（高亮显示；Tab 行行尾只读 chip 同步展示）
 * @param deviceListCallbacks 设备列表的交互回调集合（单击/双击/右键菜单）
 * @param onCollapseDeviceList 左栏「«」折叠按钮回调（宿主只改 sidebarCollapsed 状态）
 * @param sidebarCollapsed 左栏是否折叠（折叠时 0 宽 + 头栏显示展开按钮）
 * @param functionTemplates 功能模板列表（从 function_templates.xml 加载，item 含 tab 归属）
 * @param dbPackages 数据库中的已保存包名列表（供下拉选择）
 * @param onPackageAdd 用户新增包名时的回调（写入 package 表）
 * @param onPackageDelete 用户删除包名时的回调
 * @param onFunctionItemClick 功能项点击回调（快捷操作/按键模拟共用链路）
 * @param onApkDropped 快捷操作 Tab 安装卡拖入 APK 回调：(APK 绝对路径)，宿主执行 adb install -r
 * @param onPullApk 打开 APK 提取对话框（文件传输 Tab 联动）
 * @param onPushFile 推送文件到设备：(设备路径)，宿主 FileDialog + adb push
 * @param onPullFile 从设备拉取文件：(设备路径)，宿主 FileDialog + adb pull
 * @param consoleLogFlow 控制台日志流（MutableStateFlow，支持清空操作）
 * @param logcatStream 设备 logcat 流管理器（控制启动/停止/过滤）
 * @param mirrorState 镜像状态（Idle / Running，由宿主的 ScrcpyLauncher 推送）
 * @param mirrorBinaryReady scrcpy.exe 是否就绪（缺失时面板显示下载指引）
 * @param mirrorBinaryPath scrcpy.exe 期望路径（指引文案用）
 * @param onMirrorStart 点击「开启实时预览」
 * @param onMirrorStop 点击「关闭实时预览」
 */
@Composable
fun MainWindowScreen(
    modifier: Modifier = Modifier,
    menuConfigs: List<MenuConfig> = emptyList(),
    onMenuAction: (MenuAction) -> Unit = {},
    /** 当前是否暗色（SYSTEM 态为解析后实际值，驱动主题切换按钮图标） */
    themeIsDark: Boolean = false,
    /** 顶栏主题切换按钮点击：暗色切浅色、浅色切暗色（对齐 H5 themeToggle） */
    onToggleTheme: () -> Unit = {},
    dbDevices: List<DeviceRecord> = emptyList(),
    onlineDevices: List<DeviceInfo> = emptyList(),
    deviceVersions: Map<String, String> = emptyMap(),
    selectedDeviceIp: String? = null,
    deviceListCallbacks: DeviceListCallbacks = DeviceListCallbacks(),
    onCollapseDeviceList: () -> Unit = {},
    onExpandDeviceList: () -> Unit = {},
    sidebarCollapsed: Boolean = false,
    // P5 功能区参数
    functionTemplates: List<FunctionTemplate> = emptyList(),
    dbPackages: List<String> = emptyList(),
    onPackageAdd: (String) -> Unit = {},
    onPackageDelete: (String) -> Unit = {},
    onFunctionItemClick: (FunctionItem, AppParamState) -> Unit = { _, _ -> },
    /** 按键模拟 Tab：自定义 KEYCODE 键值列表（"+" 拼接拆分后），由宿主逐条发送 */
    onCustomKey: (List<Int>) -> Unit = {},
    /** 常用命令 Tab：执行模板命令（已填充 {pkg}，宿主按当前设备组装 adb 命令并回显控制台） */
    onRunCommand: (String) -> Unit = {},
    /** 常用命令 Tab：命令已复制到剪贴板（宿主记录控制台日志） */
    onCopyCommand: (String) -> Unit = {},
    onApkDropped: (String) -> Unit = {},
    onPullApk: () -> Unit = {},
    onPushFile: (String) -> Unit = {},
    onPullFile: (String) -> Unit = {},
    // P6 控制台 + Logcat 参数
    consoleLogFlow: MutableStateFlow<List<LogEntry>> = MutableStateFlow(emptyList()),
    logcatStream: LogcatStream = LogcatStream(),
    // P8 实时预览参数
    mirrorState: MirrorState = MirrorState.Idle,
    mirrorBinaryReady: Boolean = false,
    mirrorBinaryPath: String = "",
    onMirrorStart: () -> Unit = {},
    onMirrorStop: () -> Unit = {}
) {
    // 内容区五 Tab 与内嵌控制台 Tab 状态（embeddedTab 提升到本层）
    var contentTab by remember { mutableStateOf(TAB_QUICK) }
    var embeddedTab by remember { mutableStateOf(0) }
    // 快捷操作参数卡状态提升到本层：功能卡执行与常用命令 Tab 的 {pkg} 模板共享包名
    var appParamState by remember { mutableStateOf(AppParamState()) }
    // 内嵌控制台区收缩状态（收缩后仅显示展开按钮条）
    var consoleCollapsed by remember { mutableStateOf(false) }

    // 按 tab 归属拆分功能项（XML 无 tab 属性的项默认 quick）
    val quickItems = functionTemplates.flatMap { it.items }.filter { it.tab == "quick" }
    val keysItems = functionTemplates.flatMap { it.items }.filter { it.tab == "keys" }
    val filesItems = functionTemplates.flatMap { it.items }.filter { it.tab == "files" }

    Column(modifier = modifier.fillMaxSize().background(easyAdbColors.Background)) {
        AppTopBar(
            sidebarCollapsed = sidebarCollapsed,
            onExpandSidebar = onExpandDeviceList,
            themeIsDark = themeIsDark,
            onToggleTheme = onToggleTheme,
            menuConfigs = menuConfigs,
            onMenuAction = onMenuAction
        )

        SplitPane(
            modifier = Modifier.fillMaxWidth().weight(1f),
            initialFraction = 0.2f,
            leftCollapsed = sidebarCollapsed,
            leftPanel = { m ->
                DeviceListPanel(
                    dbDevices = dbDevices,
                    onlineDevices = onlineDevices,
                    selectedDeviceIp = selectedDeviceIp,
                    callbacks = deviceListCallbacks,
                    onCollapse = onCollapseDeviceList,
                    deviceVersions = deviceVersions,
                    modifier = m
                )
            },
            rightPanel = { m ->
                Column(modifier = m.fillMaxSize()) {
                    ContentTabRow(
                        selected = contentTab,
                        onSelect = { contentTab = it },
                        selectedDeviceIp = selectedDeviceIp,
                        dbDevices = dbDevices,
                        onlineDevices = onlineDevices
                    )
                    Divider(color = easyAdbColors.Divider, thickness = 1.dp)

                    // Tab 内容
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (contentTab) {
                            // 常用命令：seg 分组 + 命令行卡片（{pkg} 取参数卡包名）
                            TAB_CMDS -> CommandsPanel(
                                packageName = appParamState.selectedPackage,
                                onRunCommand = onRunCommand,
                                onCopyCommand = onCopyCommand,
                                modifier = Modifier.fillMaxSize()
                            )
                            TAB_KEYS -> RemoteKeyPad(
                                items = keysItems,
                                onKeyClick = { item -> onFunctionItemClick(item, appParamState) },
                                onCustomKey = onCustomKey,
                                modifier = Modifier.fillMaxSize()
                            )
                            TAB_FILES -> TemplateGrid(
                                items = filesItems,
                                onItemClick = { item ->
                                    if (item.action == "m_pull_apk") onPullApk()
                                },
                                onFileItemClick = { _, _ -> },
                                modifier = Modifier.fillMaxSize()
                            )
                            TAB_MIRROR -> MirrorPanel(
                                deviceIp = selectedDeviceIp,
                                mirrorState = mirrorState,
                                binaryReady = mirrorBinaryReady,
                                binaryPath = mirrorBinaryPath,
                                onStart = onMirrorStart,
                                onStop = onMirrorStop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // 快捷操作：参数卡(2×2) + 功能卡(13 项小卡+推送/拉取大卡，安装卡虚线拖拽)
                            else -> FunctionPanel(
                                items = quickItems,
                                appParamState = appParamState,
                                onParamStateChanged = { appParamState = it },
                                dbPackages = dbPackages,
                                onPackageAdd = onPackageAdd,
                                onPackageDelete = onPackageDelete,
                                onItemClick = onFunctionItemClick,
                                onFileItemClick = { item, devicePath ->
                                    when (item.action) {
                                        "m_push_file" -> onPushFile(devicePath)
                                        "m_pull_file" -> onPullFile(devicePath)
                                    }
                                },
                                onApkDropped = onApkDropped,
                                showParamBar = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Divider(color = easyAdbColors.Divider, thickness = 1.dp)
                    // 内嵌控制台区（双 Tab + 拖拽调高 + 收缩/展开）
                    EmbeddedConsoleHost(
                        consoleLogFlow = consoleLogFlow,
                        logcatStream = logcatStream,
                        deviceIp = selectedDeviceIp,
                        selectedTab = embeddedTab,
                        onSelectTab = { embeddedTab = it },
                        collapsed = consoleCollapsed,
                        onToggleCollapse = { consoleCollapsed = !consoleCollapsed },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}
