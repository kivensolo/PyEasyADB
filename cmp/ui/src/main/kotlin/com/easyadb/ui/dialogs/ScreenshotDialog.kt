package com.easyadb.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.adb.AdbExecutor
import com.easyadb.ui.designsystem.easyAdbColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/21
 * description：屏幕截图对话框（对齐 HTML 参照稿 .modal-shotload / .modal-sspreview）：
 *              单窗口状态机——打开即进入 Loading 页（spinner + 进度条 + 取消），
 *              adb exec-out screencap 捕获成功后切换预览页（刷新/旋转/复制/缩放/
 *              分辨率信息/保存/取消）。「刷新截图」不关窗，视图区叠加半透明遮罩
 *              重新获取；「保存」接入 AWT FileDialog 落盘，以原始 PNG 为准
 *              （缩放/旋转仅影响查看）；「复制到粘贴板」经 AWT imageFlavor 写
 *              系统剪贴板。获取失败进入错误页，可重试或关闭。
 */

/** 截图对话框内容状态：获取中 → 就绪（携带 PNG 数据）/ 失败（可重试） */
private sealed interface ScreenshotUiState {
    /** Loading 页：正在从设备获取截图 */
    data object Loading : ScreenshotUiState

    /** 预览页：PNG 原始字节 + 解码位图 + 分辨率/体积/位色信息 */
    data class Ready(
        val bytes: ByteArray,
        val image: ImageBitmap,
        val width: Int,
        val height: Int,
        val kb: Float,
        /** 像素位深（24/32 等，AWT ColorModel 解析；0 = 未知不显示） */
        val colorBits: Int
    ) : ScreenshotUiState

    /** 错误页：获取失败提示，可重试 */
    data class Error(val message: String) : ScreenshotUiState
}

/**
 * 屏幕截图对话框。
 *
 * @param deviceIp 目标设备地址（宿主保证非空才打开）
 * @param executor AdbExecutor（captureScreenBytes 捕获）
 * @param onDismiss 关闭对话框（取消/X/保存落盘后）
 * @param onResult 控制台回显（消息 + 级别，对齐宿主 logAppender 语义）
 */
@Composable
fun ScreenshotDialog(
    deviceIp: String,
    executor: AdbExecutor,
    onDismiss: () -> Unit,
    onResult: (message: String, level: Int) -> Unit
) {
    var uiState by remember { mutableStateOf<ScreenshotUiState>(ScreenshotUiState.Loading) }
    // 预览交互状态（对齐 H5 ss 对象：zoom 0.2–4 步进 ×1.25、rot ±90、busy 刷新中）
    var zoom by remember { mutableStateOf(1f) }
    var rot by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    // 视图区像素尺寸：1:1 缩放换算用（ContentScale.Fit 基准比例）
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun startCapture() {
        onResult("> adb -s $deviceIp exec-out screencap -p", 2)
        scope.launch {
            val bytes = executor.captureScreenBytes(deviceIp)
            val decoded = bytes?.let { runCatching { SkiaImage.makeFromEncoded(it) }.getOrNull() }
            if (bytes != null && decoded != null) {
                val bitmap = decoded.toComposeImageBitmap()
                // 位深（24 位色/32 位色）：AWT ColorModel，解析失败置 0 不显示
                val colorBits = runCatching {
                    ImageIO.read(bytes.inputStream())?.colorModel?.pixelSize
                }.getOrNull() ?: 0
                uiState = ScreenshotUiState.Ready(
                    bytes = bytes,
                    image = bitmap,
                    width = bitmap.width,
                    height = bitmap.height,
                    kb = bytes.size / 1024f,
                    colorBits = colorBits
                )
                zoom = 1f
                rot = 0f
                onResult(
                    "[成功] ${bitmap.width}×${bitmap.height} PNG · ${"%.1f".format(Locale.US, bytes.size / 1024f)} kB",
                    3
                )
            } else {
                uiState = ScreenshotUiState.Error("截图获取失败：设备可能离线，或 screencap 不可用/超时（20s）")
                onResult("[错误] 截图获取失败", 4)
            }
            busy = false
        }
    }

    // 打开即开始捕获（对话框关闭时 rememberCoroutineScope 随组合销毁自动取消协程）
    LaunchedEffect(Unit) { startCapture() }

    // 窗口尺寸随内容态切换（对齐 H5：loading 弹窗 560px 小窗 / 预览 min(1150,96vw)×min(780,92vh)），
    // 切换后重新居中。Toolkit.screenSize 为逻辑像素，与 dp 同尺度（见 ApkHelperDialog 注释）。
    val screen = remember { java.awt.Toolkit.getDefaultToolkit().screenSize }
    val dialogState = rememberDialogState(width = 560.dp, height = 148.dp)
    LaunchedEffect(uiState) {
        dialogState.size = when (uiState) {
            is ScreenshotUiState.Loading -> DpSize(560.dp, 148.dp)
            is ScreenshotUiState.Error -> DpSize(460.dp, 190.dp)
            is ScreenshotUiState.Ready -> DpSize(
                minOf(1150f, screen.width * 0.96f).dp,
                minOf(780f, screen.height * 0.92f).dp
            )
        }
        dialogState.position = WindowPosition(Alignment.Center)
    }

    // 窗口左上角图标：Android 机器人（Material Icons.Filled.Android）染品牌绿。
    // DialogWindow 的 icon 收 Painter，但 rememberVectorPainter(image) 无 tint 参数，
    // 故画到 64px ImageBitmap（ColorFilter.tint 上色）后以 BitmapPainter 包装，
    // 不新增资源文件
    val robotPainter = rememberVectorPainter(Icons.Filled.Android)
    val windowIcon = remember(robotPainter) {
        val iconSize = Size(64f, 64f)
        val bitmap = ImageBitmap(64, 64)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(bitmap),
            size = iconSize
        ) {
            with(robotPainter) {
                draw(size = iconSize, colorFilter = ColorFilter.tint(AndroidRobotGreen))
            }
        }
        BitmapPainter(bitmap)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        icon = windowIcon,
        title = "屏幕截图",
        resizable = true
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = easyAdbColors.Surface) {
            when (val state = uiState) {
                is ScreenshotUiState.Loading -> LoadingContent(
                    deviceIp = deviceIp,
                    onCancel = onDismiss
                )

                is ScreenshotUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = {
                        uiState = ScreenshotUiState.Loading
                        startCapture()
                    },
                    onClose = onDismiss
                )

                is ScreenshotUiState.Ready -> PreviewContent(
                    state = state,
                    zoom = zoom,
                    rot = rot,
                    busy = busy,
                    viewSize = viewSize,
                    onZoomChange = { zoom = it },
                    onRotChange = { rot = it },
                    onViewSizeChanged = { viewSize = it },
                    onRefresh = {
                        if (!busy) {
                            busy = true
                            startCapture()
                        }
                    },
                    onCopy = { copyImageToClipboard(state.bytes, onResult) },
                    onSave = {
                        saveToDisk(state.bytes, onResult)
                        onDismiss()
                    },
                    onCancel = onDismiss,
                    onHint = { onResult("[提示] 缩放/旋转仅影响查看，保存以原始 PNG 为准", 2) }
                )
            }
        }
    }
}

// ───────────────────────── Loading 页 ─────────────────────────

/**
 * 获取中内容页（对齐 H5 .modal-shotload）：body 自上而下排布——
 * 文案（flex）+「取消」按钮同行，下方 12px 处 8px 高缓动进度条
 * （4%→86% 驻留，完成即整页切换）。H5 此弹窗无 spinner，故不画。
 */
@Composable
private fun LoadingContent(deviceIp: String, onCancel: () -> Unit) {
    val progress = remember { Animatable(0.04f) }
    LaunchedEffect(Unit) {
        progress.animateTo(0.86f, animationSpec = tween(durationMillis = 1700, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "正在从设备获取截图…（$deviceIp）",
                fontSize = 13.sp,
                color = easyAdbColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            SsPlainButton(text = "取消", onClick = onCancel)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(8.dp)
                .background(easyAdbColors.Field, RoundedCornerShape(5.dp))
                .border(1.dp, easyAdbColors.Divider, RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(
                        Brush.horizontalGradient(listOf(easyAdbColors.PrimaryDark, easyAdbColors.PrimaryLight)),
                        RoundedCornerShape(5.dp)
                    )
            )
        }
    }
}

// ───────────────────────── 错误页 ─────────────────────────

/** 获取失败内容页：错误文案 + 重试/关闭 */
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(message, fontSize = 13.sp, color = easyAdbColors.StatusOffline)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SsPrimaryButton(text = "重试", onClick = onRetry)
                SsPlainButton(text = "关闭", onClick = onClose)
            }
        }
    }
}

// ───────────────────────── 预览页 ─────────────────────────

/** 预览内容页：工具行×2 + 视图区 + 底部操作行。对齐 H5 .modal-sspreview。 */
@Composable
private fun PreviewContent(
    state: ScreenshotUiState.Ready,
    zoom: Float,
    rot: Float,
    busy: Boolean,
    viewSize: IntSize,
    onZoomChange: (Float) -> Unit,
    onRotChange: (Float) -> Unit,
    onViewSizeChanged: (IntSize) -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onHint: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── 工具行 1：重新捕获 / 左旋 / 右旋 / 复制到粘贴板（行下带分割线）──
        // 旋转用文字按钮：旋转图标与刷新图标视觉相近易混淆（走查反馈），不再用图标
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SsToolButton(text = "重新捕获", icon = SsIcons.Refresh, enabled = !busy, onClick = onRefresh)
                SsToolButton(text = "左旋 90°", onClick = { onRotChange(rot - 90f) })
                SsToolButton(text = "右旋 90°", onClick = { onRotChange(rot + 90f) })
                SsToolButton(text = "复制到粘贴板", icon = SsIcons.Copy, onClick = onCopy)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(easyAdbColors.Divider)
            )
        }

        // ── 工具行 2：适应窗口 / 放大 / 缩小 / 1:1 / 百分比 / 信息（全部裸图标 + 气泡提示）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SsBareToolButton(icon = SsIcons.Fit, tooltip = "适应窗口", onClick = { onZoomChange(1f) })
            SsBareToolButton(icon = SsIcons.ZoomIn, tooltip = "放大", onClick = {
                onZoomChange((zoom * 1.25f).coerceAtMost(4f))
            })
            SsBareToolButton(icon = SsIcons.ZoomOut, tooltip = "缩小", onClick = {
                onZoomChange((zoom / 1.25f).coerceAtLeast(0.2f))
            })
            SsBareToolButton(text = "1:1", tooltip = "原始尺寸", onClick = {
                onZoomChange(oneToOneZoom(state.width, state.height, viewSize))
            })
            BasicText(
                text = "${(zoom * 100).roundToInt()}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = easyAdbColors.TextSecondary
                ),
                modifier = Modifier.defaultMinSize(minWidth = 46.dp)
            )
            Spacer(Modifier.weight(1f))
            // 信息行对齐 H5 .ss-info："1,920×1,080 PNG（24 位色）· 45.2 kB"（位深未知则省略括号段）
            val colorPart = if (state.colorBits > 0) "（${state.colorBits} 位色）" else ""
            BasicText(
                text = String.format(
                    Locale.US,
                    "%,d×%,d PNG%s · %.1f kB",
                    state.width, state.height, colorPart, state.kb
                ),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = easyAdbColors.TextSecondary
                )
            )
        }

        // ── 视图区：Fit 居中 + 缩放/旋转 + 溢出滚动（滚动条/手型拖拽）+ 刷新遮罩 ──
        // clipToBounds 必须有：放大的绘制会溢出布局边界，不裁剪则盖住上下工具行
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RectangleShape)
                .background(easyAdbColors.ScreenshotViewBg)
                .onSizeChanged { onViewSizeChanged(it) }
        ) {
            val density = LocalDensity.current
            val vpW = viewSize.width.toFloat()
            val vpH = viewSize.height.toFloat()
            // Fit 基准比例与缩放后显示尺寸（px；首帧视口未量出时取 0 占位）
            val fitScale = if (vpW > 0f && vpH > 0f) minOf(vpW / state.width, vpH / state.height) else 0f
            val dispWpx = state.width * fitScale * zoom
            val dispHpx = state.height * fitScale * zoom
            // ±90° 奇数倍旋转：载荷盒与互换盒宽高互换（绕中心旋转，视觉边界正好互换）
            val quarter = ((rot / 90f).toInt() % 2) != 0
            val contentWpx = if (quarter) dispHpx else dispWpx
            val contentHpx = if (quarter) dispWpx else dispHpx
            val dispW = with(density) { dispWpx.toDp() }
            val dispH = with(density) { dispHpx.toDp() }
            // 互换盒 = max(视口, 内容)：小图居中、大图可滚
            val boxW = with(density) { maxOf(vpW, contentWpx).toDp() }
            val boxH = with(density) { maxOf(vpH, contentHpx).toDp() }
            // 放大超出视口 → 出滚动条、光标转手型可拖拽平移
            val overflowX = dispWpx > vpW + 0.5f
            val overflowY = dispHpx > vpH + 0.5f

            val hScroll = rememberScrollState()
            val vScroll = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
                    .verticalScroll(vScroll)
                    .pointerHoverIcon(
                        if (overflowX || overflowY) PointerIcon.Hand else PointerIcon.Default
                    )
                    .pointerInput(overflowX, overflowY) {
                        if (overflowX || overflowY) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                hScroll.dispatchRawDelta(-dragAmount.x)
                                vScroll.dispatchRawDelta(-dragAmount.y)
                            }
                        }
                    }
            ) {
                // 互换盒：尺寸 = max(视口, 旋转后内容)——小于视口时载荷居中显示，
                // 超出时滚动范围自然成立且起点无负溢出（比 wrapContentSize 在
                // scroll 约束下的行为更确定，后者实测靠左）
                Box(
                    modifier = Modifier.size(boxW, boxH),
                    contentAlignment = Alignment.Center
                ) {
                    // 载荷盒：未旋转方向的显示尺寸，图片 Fit 填满后绕中心旋转
                    Box(modifier = Modifier.size(dispW, dispH)) {
                        Image(
                            bitmap = state.image,
                            contentDescription = "设备截图",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = rot }
                        )
                    }
                }
            }
            // 溢出才显示滚动条（宽度对齐设备列表/文件树 5dp 惯例）
            if (hScroll.maxValue > 0) {
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(hScroll),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
            if (vScroll.maxValue > 0) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(vScroll),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(5.dp)
                )
            }
            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(easyAdbColors.ScreenshotReloadOverlay),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ScreenshotSpinner()
                        Text("正在重新获取截图…", fontSize = 13.sp, color = easyAdbColors.TextSecondary)
                    }
                }
            }
        }

        // ── 底部：帮助 + 保存/取消（行上带分割线）──
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(easyAdbColors.Divider)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SsBareToolButton(text = "?", tooltip = "关于", tipAbove = true, onClick = onHint)
                Spacer(Modifier.weight(1f))
                SsPrimaryButton(text = "保存", onClick = onSave)
                SsPlainButton(text = "取消", onClick = onCancel)
            }
        }
    }
}

/**
 * 1:1 缩放：图片像素与屏幕像素一一对应。
 * ContentScale.Fit 的基准比例 fit = min(viewW/imgW, viewH/imgH)，
 * 需 zoom = 1/fit 才回到原始尺寸（clamp 0.2–8，对齐 H5 ssOneToOne）。
 */
private fun oneToOneZoom(imgWidth: Int, imgHeight: Int, viewSize: IntSize): Float {
    if (imgWidth <= 0 || imgHeight <= 0 || viewSize.width <= 0 || viewSize.height <= 0) return 1f
    val fit = minOf(
        viewSize.width.toFloat() / imgWidth,
        viewSize.height.toFloat() / imgHeight
    )
    if (fit <= 0f) return 1f
    return (1f / fit).coerceIn(0.2f, 8f)
}

// ───────────────────────── 复用小组件 ─────────────────────────

/**
 * 截图工具按钮（带文字功能型，如「刷新截图」「复制到粘贴板」）：
 * 28dp 高、7dp 圆角、MiniBtnBg 底 + Border 描边、hover 蓝调。对齐 H5 .ss-iconbtn。
 * disabled 时 50% 透明。纯图标的裸样式变体见 [SsBareToolButton]。
 */
@Composable
private fun SsToolButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .height(28.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .hoverable(interaction)
            .background(
                if (hovered && enabled) easyAdbColors.MiniBtnBgHover else easyAdbColors.MiniBtnBg,
                RoundedCornerShape(7.dp)
            )
            // H5 .ss-iconbtn:hover 边框转蓝（暗 #3b82f6 / 浅 #9dbdfa）
            .border(
                1.dp,
                if (hovered && enabled) easyAdbColors.PrimaryLight else easyAdbColors.Border,
                RoundedCornerShape(7.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            // padding 必须在 background 之后：链序在前的 padding 会让背景/描边不含留白，
            // 按钮宽度塌成纯内容宽（H5 .ss-iconbtn 的 padding:0 10px 计入按钮盒）
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (hovered && enabled) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
                )
            }
            if (text != null) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hovered && enabled) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
                )
            }
        }
    }
}

/**
 * 裸图标工具按钮：无背景/无描边，hover 仅内容变色，悬停 ≥300ms 在按钮下方
 * 弹气泡提示（[tipAbove] = true 时向上弹，用于贴窗口底部的按钮）。
 * 用于旋转/缩放/帮助等纯图标操作；气泡自建（CMP 1.7.3 无 TooltipArea，
 * 参照 LogcatPanel.TooltipIconButton 的 delay(300) + Popup 模式）。
 */
@Composable
private fun SsBareToolButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    tipAbove: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var showTip by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(300)
            showTip = true
        } else {
            showTip = false
        }
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .then(if (icon != null) Modifier.width(28.dp) else Modifier.padding(horizontal = 4.dp))
                .hoverable(interaction)
                // 裸样式：无 indication（ripple），点击只回调
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltip,
                    modifier = Modifier.size(14.dp),
                    tint = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
                )
            }
            if (text != null) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (text == "1:1") FontFamily.Monospace else null,
                    color = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
                )
            }
        }
        if (showTip) {
            // 气泡落在按钮下方（或上方）32px：按钮高 28 + 4 间隙
            val offsetY = with(LocalDensity.current) { 32.dp.roundToPx() } * if (tipAbove) -1 else 1
            Popup(
                alignment = if (tipAbove) Alignment.TopCenter else Alignment.BottomCenter,
                offset = IntOffset(0, offsetY)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    elevation = 4.dp,
                    color = easyAdbColors.TooltipBackground
                ) {
                    Text(
                        tooltip,
                        color = easyAdbColors.TooltipText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 主操作按钮（保存/重试），对齐 H5 .btn + .btn-primary：
 * 14px/600 白字、9/20 padding（总高约 38dp）、10px 圆角、
 * #3f88f8→#2563eb 垂直渐变 + 蓝色投影（明暗同值，H5 无浅色覆盖）。
 */
@Composable
private fun SsPrimaryButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .shadow(elevation = 4.dp, shape = shape, clip = false, ambientColor = PrimaryShadow, spotColor = PrimaryShadow)
            .background(Brush.verticalGradient(listOf(PrimaryGradTop, PrimaryGradBottom)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.5.sp)
    }
}

/** 次级按钮（取消/关闭），对齐 H5 .btn-plain：tx-2 字 + line 描边 + MiniBtnBg 底，hover 字色转亮 */
@Composable
private fun SsPlainButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .hoverable(interaction)
            .background(easyAdbColors.MiniBtnBg, RoundedCornerShape(10.dp))
            .border(1.dp, easyAdbColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
        )
    }
}

/** .btn-primary 渐变与投影固定色（H5 明暗同值硬编码） */
private val PrimaryGradTop = Color(0xFF3F88F8)
private val PrimaryGradBottom = Color(0xFF2563EB)
private val PrimaryShadow = Color(0x612563EB)

/** 窗口图标用 Android 品牌绿（#3DDC84），明暗标题栏下均可辨识 */
private val AndroidRobotGreen = Color(0xFF3DDC84)

/**
 * 旋转圆环 spinner：32dp、3px 轨道（PreviewTileBorder）+ 90° 顶弧（PrimaryLight），
 * 0.8s 线性旋转。对齐 H5 .scr-spin。
 */
@Composable
private fun ScreenshotSpinner() {
    val transition = rememberInfiniteTransition(label = "ss-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "ss-spin-angle"
    )
    // DrawScope 内不可读 @Composable 访问器，先取色再进 Canvas
    val trackColor = easyAdbColors.PreviewTileBorder
    val arcColor = easyAdbColors.PrimaryLight
    Canvas(modifier = Modifier.size(32.dp)) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = arcColor,
            startAngle = angle - 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

// ───────────────────────── AWT 集成：剪贴板 / 保存 ─────────────────────────

/**
 * 复制截图到系统剪贴板（AWT imageFlavor）。
 * Compose LocalClipboardManager 仅支持文本，位图走 Toolkit.systemClipboard。
 */
private fun copyImageToClipboard(bytes: ByteArray, onResult: (String, Int) -> Unit) {
    try {
        val image = ImageIO.read(bytes.inputStream()) ?: error("PNG 解码失败")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
        onResult("[成功] 截图已复制到剪贴板", 3)
    } catch (e: Exception) {
        onResult("[错误] 复制到剪贴板失败：${e.message}", 4)
    }
}

/** BufferedImage 的 Transferable 包装（DataFlavor.imageFlavor） */
private class ImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    @Throws(UnsupportedFlavorException::class)
    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}

/**
 * 保存落盘：AWT FileDialog(SAVE) 选路径后直接写内存中的 PNG 原始字节
 * （无二次 adb 传输；缩放/旋转不影响内容）。对齐既有录屏拉取/文件树另存的 FileDialog 用法。
 */
private fun saveToDisk(bytes: ByteArray, onResult: (String, Int) -> Unit) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "保存截图", java.awt.FileDialog.SAVE)
    dialog.file = "screenshot_$timestamp.png"
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    if (dir == null || file == null) {
        onResult("[取消] 用户取消了截图保存", 2)
        return
    }
    val savePath = File(dir, file).absolutePath
    try {
        File(savePath).writeBytes(bytes)
        onResult("[成功] 截图已保存: $savePath", 3)
    } catch (e: Exception) {
        onResult("[错误] 截图保存失败: ${e.message}", 4)
    }
}

// ───────────────────────── 工具行线稿图标（H5 内联 SVG） ─────────────────────────

/** 截图预览工具行图标集：24×24 视口 2.1 单位圆头描边，path 抄自 H5 参照稿 */
private object SsIcons {
    /** 重新捕获（H5 #ssRecapture；旋转不用图标——与刷新视觉相近易混淆） */
    val Refresh: ImageVector by lazy {
        lineIcon("SsRefresh", listOf("M21 12a9 9 0 1 1-2.64-6.36", "M21 3v6h-6"))
    }

    /** 复制到粘贴板（H5 #ssCopy：rect 9,9 12×12 rx2 + 左上角折页） */
    val Copy: ImageVector by lazy {
        lineIcon(
            "SsCopy",
            listOf(
                "M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
                "M5 15V5a2 2 0 0 1 2-2h10"
            )
        )
    }

    /** 适应窗口（H5 #ssFit 四角框） */
    val Fit: ImageVector by lazy {
        lineIcon(
            "SsFit",
            listOf("M8 3H5a2 2 0 0 0-2 2v3", "M21 8V5a2 2 0 0 0-2-2h-3", "M3 16v3a2 2 0 0 0 2 2h3", "M16 21h3a2 2 0 0 0 2-2v-3")
        )
    }

    /** 放大（H5 #ssZoomIn：圆 + 柄 + 十字） */
    val ZoomIn: ImageVector by lazy {
        lineIcon("SsZoomIn", listOf("M11 4a7 7 0 1 0 0 14 7 7 0 1 0 0-14z", "m21 21-4.3-4.3", "M8 11h6", "M11 8v6"))
    }

    /** 缩小（H5 #ssZoomOut：圆 + 柄 + 横线） */
    val ZoomOut: ImageVector by lazy {
        lineIcon("SsZoomOut", listOf("M11 4a7 7 0 1 0 0 14 7 7 0 1 0 0-14z", "m21 21-4.3-4.3", "M8 11h6"))
    }

    private fun lineIcon(name: String, paths: List<String>): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }
}
