package com.easyadb.ui.mirror

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.MirrorState
import com.easyadb.core.adb.ScrcpyLauncher
import com.easyadb.platform.Win32Interop
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.GradientButton
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import kotlinx.coroutines.delay
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel

/**
 * 设备实时预览面板（P8 独立窗口 + P9 嵌入模式）。
 *
 * 对齐 Python `win32Wrapper.py::ScrcpyEmbedWidget`，UI 对齐 H5 参照稿 `#pane-mirror`：
 * - 工具栏（去卡片化贴顶行）：三态状态点（灰停止 / 琥珀闪烁连接中 / 绿发光运行）+
 *   标题 + 开关（Switch 替代原开启/关闭双按钮，瘦身 36×20，开启轨恒品牌蓝）+ 三态状态
 *   文案 +「嵌入应用窗口/独立窗口」分段控件（--field 底 + 品牌蓝渐变选中项，默认嵌入，
 *   运行中锁定并琥珀提示）+ 运行时长与分辨率双徽标（.mirror-stats）；窄宽度下不换行
 *   增高——提示行 weight 全占剩余空间并右对齐，溢出端直接裁切（对齐 H5 nowrap 语义）
 * - 镜像区卡片（设备屏幕本体恒深色 .scr-outer）：未开启空态（图标+说明+品牌按钮）/
 *   连接中过渡态（spinner + 「正在连接设备画面…」）/ scrcpy 缺失指引 / 嵌入画面 /
 *   独立窗口运行提示
 * - 嵌入后键盘焦点自动处理（收编时自动 SetFocus + 点击画面即原生聚焦），
 *   替代 Python 版“键盘无法控制请点红字”的手工 workaround。
 * - scrcpy 二进制缺失时显示红色下载指引（对齐 Python WARRING 文案）。
 *
 * 进程生命周期由宿主持有的 [ScrcpyLauncher] 管理，本面板只按 [mirrorState] 渲染
 * 并回调 [onStart]/[onStop]；嵌入仅是呈现方式切换，reparent 失败自动保持独立窗口。
 *
 * Tab 切走会销毁本面板组合：嵌入态子窗口在 onDispose 中**先隐藏再脱离**（而非还原
 * 可见的独立窗口）——scrcpy 进程继续在后台运行，宿主销毁不产生窗口弹出现象；
 * 切回 Tab 时 [embedEnabled] 仍为 true（状态提升在宿主），嵌入区重新组合并走
 * 收编流程把隐藏的窗口重新 reparent + SW_SHOW，恢复嵌入预览。
 *
 * @param deviceIp 当前选中设备 IP（null 时「开启」禁用）
 * @param mirrorState 镜像状态（Idle / Connecting / Running）
 * @param resolutionText 设备分辨率徽标文本（如 `1920×1080`，null/空 时隐藏徽标；
 *   宿主在镜像启动成功后经 `adb shell wm size` 查询，Override 优先）
 * @param binaryReady scrcpy.exe 是否就绪
 * @param binaryPath scrcpy.exe 期望路径（缺失时展示在指引中）
 * @param embedEnabled 嵌入模式勾选态（宿主持有，跨 Tab 切换保持）
 * @param onEmbedEnabledChange 勾选态变更回调
 */
@Composable
fun MirrorPanel(
    deviceIp: String?,
    mirrorState: MirrorState,
    resolutionText: String? = null,
    binaryReady: Boolean,
    binaryPath: String,
    embedEnabled: Boolean,
    onEmbedEnabledChange: (Boolean) -> Unit,
    /** 嵌入区收编成功回调（hwnd>0）/ 脱离·停止清零（0）：宿主用于窗口级键盘接管转发 */
    onEmbeddedHwndChange: (Long) -> Unit = {},
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = mirrorState is MirrorState.Running
    val isConnecting = mirrorState is MirrorState.Connecting
    val isLocked = isRunning || isConnecting

    // 运行时长（H5 .mirror-stats .mstat）：基准存文件级变量，Tab 切走再切回时长连续不重置；
    // Idle→Running 时重置基准，每秒刷一次显示（mm:ss，满一小时进位 h:mm:ss）
    LaunchedEffect(isRunning) {
        if (isRunning) {
            if (mirrorRunStartMs == 0L) mirrorRunStartMs = System.currentTimeMillis()
        } else {
            mirrorRunStartMs = 0L
        }
    }
    var elapsedText by remember { mutableStateOf("00:00") }
    if (isRunning) {
        LaunchedEffect(Unit) {
            while (true) {
                val totalSec = (System.currentTimeMillis() - mirrorRunStartMs) / 1000
                val h = totalSec / 3600
                val m = (totalSec % 3600) / 60
                val sec = totalSec % 60
                elapsedText = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
                delay(1000)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 工具栏（HTML #pane-mirror .mirror-toolbar：去卡片化贴顶行，gap 12 / padding 2×14） ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题单元（.mirror-name：点+文字 gap 8，700 字重 + .5px 字距 + 专用蓝灰，12.5sp）。
            // 全行文本统一 lineHeight=16sp：不同字号的默认行盒高不同，中心对齐后视觉字面会
            // 高低错落；统一行高 + softWrap=false（窄宽度不换行增高）后各文本字面同一水平线
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MirrorStateDot(isRunning = isRunning, isConnecting = isConnecting)
                Text(
                    text = "实时预览",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = easyAdbColors.MirrorName,
                    lineHeight = MirrorToolbarLineHeight,
                    softWrap = false
                )
            }
            // 开关（.switch 工具栏瘦身 36×20）：Material Switch 52×32 均匀 scale 至视觉约
            // 34×21，requiredSize 占位 38×22 防间距放大；开启轨恒品牌蓝（H5 明暗同值）；
            // 未选设备/二进制缺失时禁开；连接中窗口极短，禁用防「点了关却变运行」竞态
            Box(
                modifier = Modifier.requiredSize(38.dp, 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Switch(
                    checked = isRunning || isConnecting,
                    onCheckedChange = { on -> if (on) onStart() else onStop() },
                    enabled = isRunning || (!isConnecting && deviceIp != null && binaryReady),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.scale(0.66f)
                )
            }
            // 状态文案（.mirror-state：12sp/600 字重/min-width 58px，三态色）
            Text(
                text = when {
                    isRunning -> "运行中"
                    isConnecting -> "连接中"
                    else -> "已停止"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = MirrorToolbarLineHeight,
                softWrap = false,
                color = when {
                    isRunning -> easyAdbColors.StatusOnline
                    isConnecting -> easyAdbColors.StatusOffline
                    else -> easyAdbColors.TextDisabled
                },
                modifier = Modifier.widthIn(min = 58.dp)
            )
            // 嵌入/独立分段控件（#mirrorEmbed 特化：--field 底 + --line 描边 + 圆角 8，
            // 选中项品牌蓝渐变+白字；运行/连接中锁定 + 琥珀提示）
            if (Win32Interop.isWindows) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .alpha(if (isLocked) 0.55f else 1f)
                            .border(easyAdbTokens.strokeWidth, easyAdbColors.Divider, easyAdbTokens.shapeLg)
                            .clip(easyAdbTokens.shapeLg)
                            .background(easyAdbColors.Field, easyAdbTokens.shapeLg)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        MirrorModeButton("嵌入应用窗口", embedEnabled, !isLocked) { onEmbedEnabledChange(true) }
                        MirrorModeButton("独立窗口", !embedEnabled, !isLocked) { onEmbedEnabledChange(false) }
                    }
                    if (isLocked) {
                        Text(
                            text = "预览运行中已锁定 · 停止后可切换",
                            fontSize = 11.sp,
                            lineHeight = MirrorToolbarLineHeight,
                            softWrap = false,
                            color = easyAdbColors.StatusOffline
                        )
                    }
                }
            }
            // 操作提示行（.mirror-hint-line：margin-left:auto 即右对齐贴行尾）。宽度过窄时
            // 不换行增高、不推挤后续控件：weight 全占剩余空间并右对齐，文字溢出端直接裁切
            // （「优先缩小文字宽度，实在没位置正常遮挡」——H5 white-space:nowrap 同语义）
            Text(
                text = "鼠标模式(右键=返回)、键盘模式(方向键、Enter键)",
                fontSize = 11.5.sp,
                lineHeight = MirrorToolbarLineHeight,
                color = easyAdbColors.TextDisabled,
                textAlign = TextAlign.End,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            // 运行统计徽标组（.mirror-stats gap 6：时长 + 分辨率）
            if (isRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MirrorStatBadge(text = elapsedText)
                    if (!resolutionText.isNullOrEmpty()) {
                        MirrorStatBadge(text = resolutionText)
                    }
                }
            }
        }

        // ── 镜像区卡片（HTML .mirror-stage / .scr-outer：设备屏幕本体恒深色，随面板铺满） ──
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = easyAdbTokens.shapeLg,
            color = easyAdbColors.PreviewBg,
            border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
            elevation = 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !binaryReady -> MirrorMissingGuide(
                        binaryPath = binaryPath,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    isConnecting -> MirrorConnectingState(modifier = Modifier.align(Alignment.Center))
                    isRunning && embedEnabled -> EmbeddedScrcpyRegion(
                        modifier = Modifier.fillMaxSize(),
                        onEmbeddedHwndChange = onEmbeddedHwndChange
                    )
                    isRunning -> FloatRunningHint(modifier = Modifier.align(Alignment.Center))
                    else -> MirrorEmptyState(
                        canStart = deviceIp != null && binaryReady,
                        binaryReady = binaryReady,
                        onStart = onStart,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

/** 工具栏三态状态点（.mirror-dot：tx-3 灰停止 / amber 1s 闪烁连接中 / 绿色光晕运行）。 */
@Composable
private fun MirrorStateDot(isRunning: Boolean, isConnecting: Boolean) {
    // 连接中闪烁（H5 animation:blink 1s infinite）：alpha 1↔0.3 往返
    val blink by rememberInfiniteTransition(label = "mirrorDotBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "mirrorDotAlpha"
    )
    val color = when {
        isRunning -> easyAdbColors.StatusOnline
        isConnecting -> easyAdbColors.StatusOffline
        else -> easyAdbColors.TextDisabled
    }
    // 运行光晕（H5 box-shadow:0 0 10px green）：18dp 径向渐变发散圈叠 7dp 实心点
    Box(
        modifier = Modifier
            .size(18.dp)
            .then(
                if (isRunning) Modifier.drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                            center = center,
                            radius = size.minDimension / 2f
                        )
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(if (isConnecting) blink else 1f)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** 镜像区连接过渡态（.scr-connecting：spinner + 「正在连接设备画面…」）。 */
@Composable
private fun MirrorConnectingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = easyAdbColors.PreviewTitle,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = "正在连接设备画面…",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.PreviewTitle
        )
    }
}

/** 工具栏数据徽标（.mstat：mono 浅蓝字 + 蓝 10% 底 + 浅蓝 28% 描边胶囊，明暗同值）。 */
@Composable
private fun MirrorStatBadge(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = MirrorToolbarLineHeight,
        softWrap = false,
        color = easyAdbColors.MirrorStatText,
        modifier = Modifier
            .clip(easyAdbTokens.shapeMd)
            .background(Color(0xFF3B82F6).copy(alpha = 0.1f))
            .border(
                easyAdbTokens.strokeWidth,
                Color(0xFF60A5FA).copy(alpha = 0.28f),
                easyAdbTokens.shapeMd
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * 分段控件单项（#mirrorEmbed button.active 特化：品牌蓝渐变 #3f88f8→#2563eb + 白字 +
 * 蓝色投影，圆角 7 / 内边距 14×6；未选透底 + TextSecondary）。与通用 .seg active
 * （SegActiveBg/SegActiveText）不同——H5 参照稿中实时预览的嵌入模式分段是独立样式；
 * 选中切换瞬切（渐变过渡在小面积控件上感知为闪烁，沿常用命令 seg 走查结论 1301750）。
 */
@Composable
private fun MirrorModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (selected) 1.dp else 0.dp,
                shape = shape,
                ambientColor = MirrorSegShadow,
                spotColor = MirrorSegShadow
            )
            .clip(shape)
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(Color(0xFF3F88F8), Color(0xFF2563EB)))
                } else {
                    SolidColor(Color.Transparent)
                }
            )
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = null, indication = null, onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            lineHeight = MirrorToolbarLineHeight,
            softWrap = false,
            maxLines = 1,
            color = if (selected) easyAdbColors.TextOnPrimary else easyAdbColors.TextSecondary
        )
    }
}

/** 分段选中项投影色（H5 box-shadow rgba(37,99,235,.4)）。 */
private val MirrorSegShadow: Color = Color(0xFF2563EB).copy(alpha = 0.4f)

/** 工具栏全行统一行高：不同字号默认行盒高不同，中心对齐后字面高低错落；统一后同一水平线。 */
private val MirrorToolbarLineHeight = 16.sp

/** 镜像区空态（.scr-empty：大图标容器 + 标题 + 说明 + 品牌主按钮）。 */
@Composable
private fun MirrorEmptyState(
    canStart: Boolean,
    binaryReady: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(easyAdbColors.PreviewTileBg)
                .border(easyAdbTokens.strokeWidth, easyAdbColors.PreviewTileBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EasyAdbTileIcons.Monitor,
                contentDescription = null,
                tint = easyAdbColors.PreviewIcon,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = "实时预览未开启",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.PreviewTitle
        )
        Text(
            text = "通过 scrcpy 实时镜像设备画面 · 支持嵌入应用窗口 / 独立窗口两种模式\n鼠标右键 = 返回",
            fontSize = 12.sp,
            color = easyAdbColors.PreviewBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 430.dp)
        )
        GradientButton(
            text = "开启实时预览",
            onClick = onStart,
            enabled = canStart,
            height = 34.dp,
            horizontalPadding = 18.dp
        )
        if (!canStart) {
            Text(
                text = if (binaryReady) "请先在左侧设备列表中选择设备" else "scrcpy 未就绪，请先安装",
                fontSize = 11.sp,
                color = easyAdbColors.PreviewBody
            )
        }
    }
}

/** scrcpy 缺失指引（对齐 Python WARRING 文案；恒深底上用主题错误色）。 */
@Composable
private fun MirrorMissingGuide(binaryPath: String, modifier: Modifier = Modifier) {
    Text(
        text = buildString {
            appendLine("系统找不到 scrcpy。请从 https://github.com/Genymobile/scrcpy/releases 下载编译好的 win64 版本，")
            appendLine("例如 scrcpy-win64-v3.3.4.zip，解压后将文件夹改名为 scrcpy-win64，放入以下任一位置：")
            appendLine("  · $binaryPath（推荐，程序数据目录）")
            appendLine("  · 程序运行目录下的 tool/scrcpy-win64")
            appendLine("后续升级 scrcpy 版本只需替换 scrcpy-win64 目录下的文件，无缝升级。")
        },
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = easyAdbColors.LogError,
        modifier = modifier
    )
}

/** 独立窗口模式运行提示（画面在系统窗口，镜像区仅作状态说明）。 */
@Composable
private fun FloatRunningHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "预览运行中 · 独立窗口模式",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.PreviewTitle
        )
        Text(
            text = "画面显示在独立窗口「" + ScrcpyLauncher.WINDOW_TITLE + "」中，操作提示同嵌入模式",
            fontSize = 12.sp,
            color = easyAdbColors.PreviewBody
        )
    }
}

/** 本轮预览运行起始时刻（文件级，跨 Tab 切换存活）：Idle→Running 重置为当前时刻，停止清零。 */
private var mirrorRunStartMs: Long = 0L

/** 上次成功收编的 scrcpy 窗口句柄（文件级，跨 Tab 切换存活）：同句柄的重嵌入跳过就绪门控。 */
private var lastEmbeddedChildHwnd: Long = 0L

/**
 * scrcpy 嵌入区（P9）：宿主为 SwingPanel 内的重型 [Canvas]（有独立 HWND，子窗口
 * 被裁剪在面板内）。进入组合后轮询按标题找到 scrcpy 窗口 → 子窗口化 + reparent +
 * 贴合尺寸 + 自动聚焦；面板尺寸变化实时 SetWindowPos 跟随；离开组合（取消勾选 /
 * 停止镜像 / 关闭 scrcpy）时脱离并还原独立窗口样式。
 *
 * 几何断言与全链路验证见 [main]（EmbedSpike）。
 */
@Composable
private fun EmbeddedScrcpyRegion(
    modifier: Modifier = Modifier,
    onEmbeddedHwndChange: (Long) -> Unit = {}
) {
    val hostPanel = remember { JPanel() }
    // 重型 Canvas：有独立 HWND 作嵌入宿主；置为不可聚焦，避免 AWT 抢走子窗口键盘焦点
    val hostCanvas = remember { Canvas().apply { isFocusable = false } }
    // 收编后的 scrcpy 窗口句柄与宿主句柄、原始样式；onDispose 取消嵌入时用
    var embedHwnd by remember { mutableStateOf<Long?>(null) }
    var hostHwnd by remember { mutableStateOf<Long?>(null) }
    var originalStyle by remember { mutableStateOf<Int?>(null) }
    // 最近一次应用的宿主尺寸（物理 px），避免每帧重复 SetWindowPos
    val lastSize = remember { intArrayOf(0, 0) }

    Box(
        modifier = modifier
            .focusable()
            // 键盘透传兜底：主路径是上方 LaunchedEffect 的裸 SetFocus（焦点在子窗口，
            // 键盘原生直达 scrcpy）；焦点被 Compose 控件拿走时按键经此转投 scrcpy，
            // 并在按下时同步归还焦点，后续按键恢复原生直达
            .onPreviewKeyEvent { event ->
                val child = embedHwnd ?: return@onPreviewKeyEvent false
                // Compose Desktop 的 keyCode 为 Long（即 AWT VK 码），VK 值域在 Int 内
                val vk = event.key.keyCode.toInt()
                if (vk == 0) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        Win32Interop.setFocusWindow(child)
                        Win32Interop.postKeyDown(child, vk)
                    }
                    KeyEventType.KeyUp -> Win32Interop.postKeyUp(child, vk)
                    else -> false
                }
            }
    ) {
        SwingPanel(
            // scrcpy 画面收编前的垫底色：预览区恒深色（对齐 .scr-outer）
            background = easyAdbColors.PreviewBg,
            factory = {
                hostPanel.apply {
                    layout = BorderLayout()
                    add(hostCanvas, BorderLayout.CENTER)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    // Compose px 即物理像素（Spike 几何断言已验证），可直接喂给 SetWindowPos
                    if (size.width != lastSize[0] || size.height != lastSize[1]) {
                        lastSize[0] = size.width
                        lastSize[1] = size.height
                        val child = embedHwnd
                        val host = hostHwnd
                        if (child != null && host != null && size.width > 0 && size.height > 0) {
                            Win32Interop.moveWindow(child, 0, 0, size.width, size.height)
                        }
                    }
                }
        )
    }

    // 启动后轮询查找 scrcpy 窗口，就绪后收编：
    // 1) 按唯一标题找窗口（200ms×25 次上限）；
    // 2) 就绪检测——连续两次（间隔 250ms）窗口尺寸 ≥50 且句柄有效才 reparent。
    //    过早 reparent（SDL 窗口初始化中途）会触发 0xC000041D 崩溃（致命用户回调异常）；
    //    启动失败/窗口消失则超时放弃，保持独立窗口兜底。
    LaunchedEffect(Unit) {
        var child: Long? = null
        val findDeadline = System.currentTimeMillis() + 5000
        while (child == null && System.currentTimeMillis() < findDeadline) {
            child = Win32Interop.findWindowByTitle(ScrcpyLauncher.WINDOW_TITLE)
            if (child == null) delay(200)
        }
        if (child == null) return@LaunchedEffect // 找不到（启动失败/非 Windows）→ 保持独立窗口兜底

        // 就绪门控仅防"SDL 初始化中途 reparent"（0xC000041D），只对新启动的窗口有意义；
        // Tab 切走再切回的重嵌入（同进程同句柄）窗口早已成熟，门控的 ~500ms 采样延迟
        // 只会表现为画面迟滞（PyQt QTabWidget 保留页面无此问题），同句柄直接跳过
        val isReattach = child == lastEmbeddedChildHwnd
        if (!isReattach) {
            var stable = 0
            val readyDeadline = System.currentTimeMillis() + 4000
            while (stable < 2 && System.currentTimeMillis() < readyDeadline) {
                val rect = Win32Interop.windowRect(child)
                if (rect != null && rect.width >= 50 && rect.height >= 50) {
                    stable++
                } else {
                    stable = 0 // 尺寸不足或窗口已销毁（启动失败），重新等待
                }
                delay(250)
            }
            if (stable < 2) return@LaunchedEffect // 窗口未就绪 → 放弃收编，独立窗口兜底
        }

        val host = Win32Interop.componentHwnd(hostCanvas)
        if (host == null) return@LaunchedEffect

        originalStyle = Win32Interop.applyChildStyle(child)
        if (!Win32Interop.setParent(child, host)) return@LaunchedEffect
        embedHwnd = child
        hostHwnd = host
        lastEmbeddedChildHwnd = child
        onEmbeddedHwndChange(child)

        // 嵌入期常驻挂接输入队列 + 裸 SetFocus（不 detach、不抢回焦点）：KeySpike 与
        // 实测确认焦点是键盘透传的关键——attach→SetFocus→detach 会把焦点状态打散，
        // focusRequester.requestFocus() 会把焦点抢回 Compose，两者都会让键盘失效；
        // 焦点落在子窗口后键盘原生直达 scrcpy
        Win32Interop.attachThreadInputToWindow(child, true)

        Win32Interop.windowRect(host)?.let {
            Win32Interop.moveWindow(child, 0, 0, it.width, it.height)
        }
        // 重嵌入路径：窗口可能因上次 Tab 切走被隐藏——先显示再聚焦（SetFocus 对隐藏窗口无效）
        Win32Interop.showWindow(child)
        // 自动聚焦：收编后立即取得键盘焦点，无需先点击画面
        Win32Interop.setFocusWindow(child)
    }

    // 离开组合（Tab 切走 / 取消嵌入 / 停止镜像）：先隐藏收起画面，再脱离父窗口并还原
    // 独立窗口样式、停靠到屏幕外备用位。隐藏先行保证宿主销毁瞬间窗口不可见——
    // scrcpy 进程仍在运行（Tab 切走场景），绝不以独立窗口形态弹出；
    // 切回 Tab 时收编流程会 find → reparent → showWindow 恢复嵌入。
    DisposableEffect(Unit) {
        onDispose {
            val child = embedHwnd
            if (child != null) {
                onEmbeddedHwndChange(0L)
                Win32Interop.hideWindow(child)
                Win32Interop.attachThreadInputToWindow(child, false)
                Win32Interop.setParent(child, null)
                originalStyle?.let { Win32Interop.restoreTopLevelStyle(child, it, visible = false) }
                Win32Interop.moveWindow(child, 120, 120, 960, 720)
            }
        }
    }
}
