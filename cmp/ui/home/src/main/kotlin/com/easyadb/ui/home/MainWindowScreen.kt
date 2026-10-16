@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.LogcatStream
import com.easyadb.platform.Win32Interop
import com.easyadb.core.adb.MirrorState
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.config.FunctionTemplate
import com.easyadb.core.config.MenuAction
import com.easyadb.core.config.MenuConfig
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.TextInputFocusTracker
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
import com.easyadb.ui.home.components.WindowControls
import com.easyadb.ui.home.components.tabSwitchTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────
// 顶部应用头栏（P12 布局大改）
// ─────────────────────────────────────────────────────────

/**
 * 顶部应用头栏（44dp，即应用唯一标题栏）：品牌标识 + [»]侧栏展开按钮（折叠时）
 * + 行尾设置齿轮、明暗主题切换与窗口控制按钮（最小化/最大化/关闭，对齐 HTML 参照稿
 * titlebar win-controls 的 .wc 样式）。窗口为 undecorated，头栏空白区承担拖拽移动
 * 与双击最大化；「新建设备连接」入口在设备列表区头部，当前设备 chip 在 ContentTabRow 右侧。
 *
 * 齿轮按钮展开精简后的菜单（当前仅「工具」主题三项）；主题按钮按 H5 行为：
 * 暗色显示太阳（点击切浅色）、浅色显示月亮（点击切暗色），SYSTEM 态按解析值；
 * 关闭按钮悬停红底白图标（H5 .wc-close:hover）。
 */
@Composable
private fun AppTopBar(
    sidebarCollapsed: Boolean,
    onExpandSidebar: () -> Unit,
    themeIsDark: Boolean,
    onToggleTheme: () -> Unit,
    menuConfigs: List<MenuConfig>,
    onMenuAction: (MenuAction) -> Unit,
    windowControls: WindowControls
) {
    var overflowOpen by remember { mutableStateOf(false) }
    // 顶栏垂直渐变底（对齐 HTML .titlebar：暗 #0e1626→#0c1320 / 浅 #ffffff→#f1f5fb）
    Surface(
        color = Color.Transparent,
        elevation = 0.dp,
        modifier = Modifier.background(
            Brush.verticalGradient(listOf(easyAdbColors.TitleBarTop, easyAdbColors.TitleBarBottom))
        )
    ) {
        Column {
            // 头栏整条作为窗口拖拽区（undecorated 窗口的移动手段），空白处双击切换最大化
            windowControls.dragArea {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .titleBarDoubleClick(onDoubleClick = { windowControls.toggleMaximize() })
                    .padding(horizontal = easyAdbTokens.spaceMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 品牌标识（HTML .brand）：青色渐变圆角 logo（终端线稿）+ 品牌名（Easy 主文字 / ADB cyan）
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BrandLogoGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = EasyAdbTileIcons.Terminal,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Easy")
                        withStyle(SpanStyle(color = easyAdbColors.AccentSky)) { append("ADB") }
                    },
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
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
                TopBarButton(
                    icon = if (themeIsDark) EasyAdbTileIcons.Sun else EasyAdbTileIcons.Moon,
                    contentDescription = if (themeIsDark) "切换到浅色模式" else "切换到暗色模式",
                    onClick = onToggleTheme
                )

                // 窗口控制按钮（H5 winMin/winMax/winClose：.wc 同款，关闭悬停红底白图标）
                TopBarButton(
                    icon = EasyAdbTileIcons.WinMinimize,
                    contentDescription = "最小化",
                    onClick = windowControls.minimize
                )
                TopBarButton(
                    icon = EasyAdbTileIcons.WinMaximize,
                    contentDescription = "最大化 / 还原",
                    onClick = windowControls.toggleMaximize
                )
                WindowCloseButton(onClick = windowControls.close)
            }
            }
            Divider(color = easyAdbColors.Divider, thickness = 1.dp)
        }
    }
}

/** 头栏小方按钮（32dp，H5 .wc 同构）：明暗主题切换与窗口最小化/最大化共用 */
@Composable
private fun TopBarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(easyAdbTokens.shapeMd)
            .hoverBackground()
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = easyAdbColors.TextSecondary,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** 关闭窗口按钮：悬停红底白图标（HTML .wc-close:hover） */
@Composable
private fun WindowCloseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(easyAdbTokens.shapeMd)
            .background(if (hovered) CloseHoverRed else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = EasyAdbTileIcons.WinClose,
            contentDescription = "关闭",
            tint = if (hovered) Color.White else easyAdbColors.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** 关闭按钮悬停底色（HTML .wc-close:hover #dc2626，明暗主题同值） */
private val CloseHoverRed = Color(0xFFDC2626)

/** 品牌 logo 渐变底（HTML .logo：145deg #14b8c8→#0a7f9e，明暗主题同值） */
private val BrandLogoGradient = Brush.linearGradient(listOf(Color(0xFF14B8C8), Color(0xFF0A7F9E)))

/**
 * 头栏双击检测（切换最大化）：用 Initial pass 仅观察、不消费任何事件。
 * 常规 Main pass 的 detectTapGestures 在位移超 touch slop 时会消费 move 事件，
 * 与外层 WindowDraggableArea 的拖拽手势竞争（父层 slop 检测见到消费即取消），
 * 导致头栏拖不动窗口；观察式实现让两者互不干扰。
 */
private fun Modifier.titleBarDoubleClick(onDoubleClick: () -> Unit): Modifier =
    pointerInput(onDoubleClick) {
        awaitEachGesture {
            val firstUp = awaitObservedTap() ?: return@awaitEachGesture
            // 双击时间窗内的第二次按下（超时视为单击）
            val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            } ?: return@awaitEachGesture
            val secondUp = awaitObservedTap(resumeFrom = secondDown) ?: return@awaitEachGesture
            if ((secondUp.second - firstUp.second).getDistance() <= viewConfiguration.touchSlop) {
                onDoubleClick()
            }
        }
    }

/**
 * 观察一次完整按压（按下到抬起，均不消费事件）；期间该指针位移超 touch slop
 * 视为拖动（非点击）返回 null。[resumeFrom] 传入已在处理中的按下事件时跳过
 * 首个 down 等待（双击第二次按压场景）。
 */
private suspend fun AwaitPointerEventScope.awaitObservedTap(
    resumeFrom: PointerInputChange? = null
): Pair<Offset, Offset>? {
    val down = resumeFrom ?: awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val downPos = down.position
    while (true) {
        // Initial pass 仅观察：不检查消费状态（桌面平台手势取消即指针抬起，无需 Cancel 分支）
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
        if (!change.pressed) return downPos to change.position
        if ((change.position - downPos).getDistance() > viewConfiguration.touchSlop) return null
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

/** ink 指示条滑动时长（ms，对齐 HTML 参照稿 .tab-ink 的 transition .28s） */
private const val TAB_INK_ANIM_MS = 280

/**
 * 五 Tab 下划线行（对齐 HTML 参照稿 tabs）：高 44dp、文字 14sp、选中态主文字色。
 * 指示条为独立渐变 ink 层（PrimaryLight→Primary，贴行底），切 Tab 时 x/宽度以
 * 280ms 平滑滑动到新位置（对齐 H5 moveInk 的 left/width 过渡），首次落位直接
 * 显示不播动画；行尾为当前设备 chip（只读展示，选中/切换仍在左侧设备列表，
 * 未选中设备时不显示）。
 */
@Composable
private fun ContentTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedDeviceIp: String?,
    dbDevices: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>
) {
    // 各 Tab 的 (x, width) 像素测量（相对 tabs 行，随窗口布局变化自动重报）
    var tabMetrics by remember {
        mutableStateOf(Array(CONTENT_TAB_TITLES.size) { 0f to 0f })
    }
    // tabs 行相对外层 Box 的 x 偏移（当前布局为 0，测量值相加防未来加内边距错位）
    var tabsOffsetX by remember { mutableStateOf(0f) }
    val (inkTargetX, inkTargetW) = tabMetrics[selected].let { (x, w) -> (tabsOffsetX + x) to w }

    val inkX = remember { Animatable(0f) }
    val inkW = remember { Animatable(0f) }
    var inkPlaced by remember { mutableStateOf(false) }

    // 首个有效目标直接落位（避免启动时 ink 从 0 滑出），其后 280ms 滑动过渡
    LaunchedEffect(inkTargetX, inkTargetW) {
        if (inkTargetW <= 0f) return@LaunchedEffect
        if (!inkPlaced) {
            inkX.snapTo(inkTargetX)
            inkW.snapTo(inkTargetW)
            inkPlaced = true
        } else {
            launch {
                inkX.animateTo(inkTargetX, tween(TAB_INK_ANIM_MS, easing = FastOutSlowInEasing))
            }
            launch {
                inkW.animateTo(inkTargetW, tween(TAB_INK_ANIM_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(easyAdbColors.TabsBar) // Tab 栏专色（HTML .tabs：暗 #0d1524 / 浅 #f6f8fc）
            .padding(horizontal = easyAdbTokens.spaceMd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { tabsOffsetX = it.positionInParent().x },
            verticalAlignment = Alignment.CenterVertically
        ) {
            CONTENT_TAB_TITLES.forEachIndexed { index, title ->
                val isSelected = selected == index
                Column(
                    modifier = Modifier
                        // IntrinsicSize.Min：宽度收窄到文字宽度，ink 条与文字等宽
                        .width(IntrinsicSize.Min)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) }
                        .onGloballyPositioned { coords ->
                            tabMetrics = tabMetrics.copyOf().also {
                                it[index] = coords.positionInParent().x to coords.size.width.toFloat()
                            }
                        }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
                    )
                }
                Spacer(Modifier.width(easyAdbTokens.spaceLg))
            }
        }

        // ink 指示条独立层：BottomStart 定位 + x/width 双 Animatable 平滑滑动
        if (inkPlaced) {
            val inkWidth = with(LocalDensity.current) { inkW.value.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(inkX.value.roundToInt(), 0) }
                    .width(inkWidth)
                    .height(2.5.dp)
                    .clip(easyAdbTokens.shapeSm)
                    .background(
                        Brush.horizontalGradient(
                            listOf(easyAdbColors.PrimaryLight, easyAdbColors.Primary)
                        )
                    )
            )
        }

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
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                CurrentDeviceChip(
                    alias = record?.alias.orEmpty(),
                    deviceAddr = deviceAddr,
                    isOnline = isOnline
                )
            }
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
 * @param windowControls 窗口控制能力（undecorated 窗口：头栏拖拽区 + 最小化/最大化/关闭），宿主注入
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
    onMirrorStop: () -> Unit = {},
    /** 窗口控制能力（undecorated 窗口：头栏拖拽 + 最小化/最大化/关闭），由宿主注入 */
    windowControls: WindowControls = WindowControls(
        dragArea = { content -> content() },
        minimize = {},
        toggleMaximize = {},
        close = {}
    )
) {
    // 内容区五 Tab 与内嵌控制台 Tab 状态（embeddedTab 提升到本层）
    var contentTab by remember { mutableStateOf(TAB_QUICK) }
    // 嵌入模式勾选态提升到 Tab 容器层：MirrorPanel 随 Tab 切换销毁重建，
    // 勾选框本身运行中禁用——若状态随面板销毁，切回后嵌入预览将无法恢复
    var mirrorEmbedEnabled by remember { mutableStateOf(false) }
    // 已收编的 scrcpy 子窗口句柄（嵌入区收编成功后经 onEmbeddedHwndChange 登记，脱离/停止清零）：
    // 供根级键盘接管转发使用——句柄生命周期与嵌入区一致，宿主只读不管理
    var embeddedScrcpyHwnd by remember { mutableStateOf(0L) }
    var embeddedTab by remember { mutableStateOf(0) }
    // 快捷操作参数卡状态提升到本层：功能卡执行与常用命令 Tab 的 {pkg} 模板共享包名
    var appParamState by remember { mutableStateOf(AppParamState()) }
    // 内嵌控制台区收缩状态（收缩后仅显示展开按钮条）
    var consoleCollapsed by remember { mutableStateOf(false) }

    // 按 tab 归属拆分功能项（XML 无 tab 属性的项默认 quick）
    val quickItems = functionTemplates.flatMap { it.items }.filter { it.tab == "quick" }
    val keysItems = functionTemplates.flatMap { it.items }.filter { it.tab == "keys" }
    val filesItems = functionTemplates.flatMap { it.items }.filter { it.tab == "files" }

    // ── 实时预览 Tab 激活期间的窗口级键盘接管（P9 键盘透传补强） ──
    // 嵌入预览的原生直达路径会被「点击嵌入区外的控件」打断（Compose 焦点转移，按键不再
    // 经过嵌入区子树的兜底 handler）。此处在窗口根上接管：预览运行中 + 嵌入模式 +
    // 实时预览 Tab 可见时，按键直接转投 scrcpy 子窗口（SetFocus 归还 + PostMessage 转发，
    // 复用 MirrorPanel 嵌入区的同款机制），首个按键后焦点回归子窗口、恢复原生直达。
    // 守卫：文本输入框聚焦时本地优先（过滤框打字不能进设备）；非实时预览 Tab 不接管。
    val mirrorTakeOverActive = mirrorState is MirrorState.Running &&
        mirrorEmbedEnabled && Win32Interop.isWindows &&
        contentTab == TAB_MIRROR && !TextInputFocusTracker.hasFocusedTextInput
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(easyAdbColors.Background)
            .onPreviewKeyEvent { event ->
                if (!mirrorTakeOverActive || event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) {
                    false
                } else {
                    // Main.kt 持有的 scrcpy 子窗口句柄（嵌入区收编后经回调登记；未收编=0 不接管）
                    val child = embeddedScrcpyHwnd
                    val vk = event.key.keyCode.toInt()
                    if (child == 0L || vk == 0) {
                        false
                    } else {
                        if (event.type == KeyEventType.KeyDown) {
                            Win32Interop.setFocusWindow(child)
                            Win32Interop.postKeyDown(child, vk)
                        } else {
                            Win32Interop.postKeyUp(child, vk)
                        }
                        true
                    }
                }
            }
    ) {
        AppTopBar(
            sidebarCollapsed = sidebarCollapsed,
            onExpandSidebar = onExpandDeviceList,
            themeIsDark = themeIsDark,
            onToggleTheme = onToggleTheme,
            menuConfigs = menuConfigs,
            onMenuAction = onMenuAction,
            windowControls = windowControls
        )

        SplitPane(
            modifier = Modifier.fillMaxWidth().weight(1f),
            initialFraction = 0.25f, // 1280 宽窗口下 ≈ 320px，对齐 H5 .sidebar 318px
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

                    // Tab 内容（切换时新面板淡入+上移、旧面板快速淡出）
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = contentTab,
                            transitionSpec = tabSwitchTransition(),
                            label = "contentTab"
                        ) { tab ->
                            when (tab) {
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
                                    embedEnabled = mirrorEmbedEnabled,
                                    onEmbedEnabledChange = { mirrorEmbedEnabled = it },
                                    onEmbeddedHwndChange = { embeddedScrcpyHwnd = it },
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
