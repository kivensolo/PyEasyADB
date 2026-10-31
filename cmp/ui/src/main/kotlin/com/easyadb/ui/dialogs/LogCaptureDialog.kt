package com.easyadb.ui.dialogs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAwtImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.platform.LocalDensity
import com.easyadb.core.adb.AdbExecutor
import com.easyadb.core.adb.CaptureState
import com.easyadb.core.adb.LogcatCapture
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.FieldShakeState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbMonospace
import com.easyadb.ui.designsystem.observeFrame
import com.easyadb.platform.NativeFilePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/30
 * description：日志抓取对话框（对齐 HTML 参照稿 .modal-logcap 三态，SP-2003）：
 *              配置态（保存目录 + 文件名 seg + logcat 格式单选 + 抓取前清缓冲勾选 +
 *              命令预览）→ 抓取中态（双环旋转动画 + File.length() 每秒轮询真实落盘
 *              大小 + 唯一「停止抓取」按钮，×/Esc 一律路由到停止保存）→ 完成态
 *              （绿勾徽标 + 大小/时长 chip + 打开所在目录 / 重新抓取）。
 *              抓取进程由 [LogcatCapture] 承载（ProcessBuilder 裸 adb + Redirect.to
 *              覆盖写）；「抓取前清空设备日志缓存」先执行 `adb logcat -c` 再起进程。
 */

/** 弹窗内容状态机：配置 → 抓取中 → 完成 */
private sealed interface LogCapUiState {
    /** 配置页：目录/文件名/格式/清缓冲/命令预览 */
    data object Config : LogCapUiState

    /** 抓取中页：双环动画 + 实时字节数 + 停止 */
    data class Capturing(val file: File, val startedAtMs: Long) : LogCapUiState

    /** 完成页：绿勾 + 文件信息 + 打开目录 */
    data class Done(val file: File, val sizeBytes: Long, val durationMs: Long) : LogCapUiState
}

/** logcat 输出格式单选项（对齐 H5 #lcOpts，第一项默认） */
private data class LogFormatOption(val label: String, val args: String, val desc: String, val custom: Boolean = false)

private val LOG_FORMATS = listOf(
    LogFormatOption("-v threadtime", "-v threadtime", "时间 + pid + tid + 优先级 + Tag，信息最全，研发分析首选"),
    LogFormatOption("-v time", "-v time", "无pid/tid信息，不关心线程时可优先选择"),
    LogFormatOption("-v long", "-v long", "每条日志换行展示，可读性强，但文件体积会暴涨"),
    LogFormatOption("自定义参数", "", "手动输入 logcat 参数，如 -v color -s TagName", custom = true)
)

/** 文件名时间戳（H5 lcToday 口径：yyyyMMdd_HHmmss） */
private val STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

/** 配置输入项（目录/前缀/自定义文件名/自定义参数在弹窗生命周期内保留，重新抓取回填） */
private class LogCapConfig {
    var dir by mutableStateOf("")
    var prefix by mutableStateOf("")
    var customName by mutableStateOf("")
    var customArgs by mutableStateOf("")
    var nameModeCustom by mutableStateOf(false)
    var clearBuffer by mutableStateOf(false)
    var formatIndex by mutableStateOf(0)
}

/**
 * 日志抓取对话框。
 *
 * @param deviceIp 目标设备地址（宿主保证非空才打开，不带 :port 口径）
 * @param executor AdbExecutor（logcat -c 清缓冲）
 * @param capture LogcatCapture 实例（宿主持有，状态回写驱动 UI）
 * @param onDismiss 关闭对话框（完成态「关闭/打开所在目录」或任何状态的自然关闭）
 * @param onResult 控制台回显（消息 + 级别）
 * @param lastDir/lastPrefix 上次会话的保存目录/前缀（AppConfigManager 持久化）
 * @param onSettingsPersist 会话结束时回传目录/前缀供宿主写回配置
 */
@Composable
fun LogCaptureDialog(
    deviceIp: String,
    executor: AdbExecutor,
    capture: LogcatCapture,
    onDismiss: () -> Unit,
    onResult: (message: String, level: Int) -> Unit,
    lastDir: String,
    lastPrefix: String,
    onSettingsPersist: (dir: String, prefix: String) -> Unit
) {
    var uiState by remember { mutableStateOf<LogCapUiState>(LogCapUiState.Config) }
    val config = remember { LogCapConfig() }
    config.dir = if (config.dir.isEmpty()) lastDir else config.dir
    config.prefix = if (config.prefix.isEmpty() && lastPrefix.isNotEmpty()) lastPrefix else config.prefix

    val scope = rememberCoroutineScope()
    val dirShake = remember { FieldShakeState() }

    // 目标文件名（H5 lcBuildFname 口径）：前缀_时间戳.log / 自定义名补 .log
    fun buildFileName(): String {
        return if (config.nameModeCustom) {
            val name = config.customName.trim().ifEmpty { "logcat" }
            if (Regex("\\.[a-zA-Z0-9]+$").containsMatchIn(name)) name else "$name.log"
        } else {
            val prefix = config.prefix.trim()
            (if (prefix.isEmpty()) "" else "${prefix}_") +
                LocalDateTime.now().format(STAMP_FORMATTER) + ".log"
        }
    }

    /** 组装 logcat 参数列表（H5 lcLvl 口径：选中自定义时空输入回退 threadtime） */
    fun logcatArgs(): List<String> {
        val opt = LOG_FORMATS[config.formatIndex]
        return if (opt.custom) {
            config.customArgs.trim().ifEmpty { "-v threadtime" }.split(Regex("\\s+"))
        } else {
            opt.args.split(Regex("\\s+"))
        }
    }

    // ── 抓取状态订阅：进程退出（任何原因）→ 按文件非空判定成败 ──
    LaunchedEffect(Unit) {
        capture.state.collect { st ->
            val cur = uiState
            if (cur is LogCapUiState.Capturing && st is CaptureState.Stopped) {
                val file = cur.file
                val duration = System.currentTimeMillis() - cur.startedAtMs
                if (file.exists() && file.length() > 0) {
                    uiState = LogCapUiState.Done(file, file.length(), duration)
                    onResult("[成功] 日志抓取完成：${file.absolutePath}", 2)
                } else {
                    onResult(
                        "[错误] 日志抓取未产生数据（exitCode=${st.exitCode}）\n" +
                            st.stderrTail.take(300).ifBlank { "设备可能离线，或 logcat 参数不合法" },
                        4
                    )
                    uiState = LogCapUiState.Config
                }
                capture.reset()
            } else if (cur is LogCapUiState.Capturing && st is CaptureState.Failed) {
                onResult("[错误] 抓取进程启动失败：${st.message}", 4)
                uiState = LogCapUiState.Config
                capture.reset()
            }
        }
    }

    fun startCapture() {
        val dir = config.dir.trim()
        if (dir.isEmpty()) {
            dirShake.shake()
            onResult("[提示] 请先填写保存目录", 3)
            return
        }
        val fileName = buildFileName()
        val target = File(dir, fileName)
        val args = logcatArgs()
        onSettingsPersist(dir, config.prefix.trim())
        onResult("> adb -s $deviceIp logcat ${args.joinToString(" ")} > ${target.absolutePath}", 2)
        scope.launch {
            // 勾选清理：先 adb logcat -c（短命令，20s 超时足够），成功后再起抓取进程
            if (config.clearBuffer) {
                onResult("> adb -s $deviceIp logcat -c", 2)
                val clear = executor.execAdbCmd("adb -s $deviceIp logcat -c")
                if (!clear.success) {
                    onResult("[错误] 清理设备日志缓存失败：${clear.output.take(200)}", 4)
                    return@launch
                }
                onResult("设备日志缓存已清空", 3)
            }
            val startedAt = System.currentTimeMillis()
            capture.start(deviceIp, args, target)
            // start 置 Running 失败（进程秒退/启动异常）时 state 收集器会回置 Config；
            // 只有真正 Running 才切页
            delay(50)
            val st = capture.state.value
            if (st is CaptureState.Running) {
                uiState = LogCapUiState.Capturing(target, startedAt)
            }
        }
    }

    /** 停止抓取：进程由 onExit 回调驱动 state 收集器切完成页（唯一出口=保存文件） */
    fun stopCapture() {
        val cur = uiState
        if (cur is LogCapUiState.Capturing) {
            onResult("[提示] 已请求停止抓取，等待写入完成…", 2)
            capture.stop()
        }
    }

    // 窗口尺寸：配置页高度 Unspecified → 官方自动尺寸（内容自然高度），抓取中/
    // 完成页定高；非模态窗口（ModelessDialogWindow 基于 Window）抓取中可操作主窗口
    // 窗口左上角图标：LogCapture 线稿染品牌色（BitmapPainter，Window.icon 收 Painter）
    val windowIcon = rememberWindowIconPainter(EasyAdbTileIcons.LogCapture)

    ModelessDialogWindow(
        onCloseRequest = {
            // 抓取中唯一出口 = 停止并保存（H5 语义：不提供丢弃路径）；
            // 进程仍在跑时关闭窗口，让进程由 onExit 收尾后状态自然落地
            if (uiState is LogCapUiState.Capturing) {
                stopCapture()
            } else {
                onDismiss()
            }
        },
        title = "日志抓取",
        icon = windowIcon,
        recenterKey = uiState,
        size = when (uiState) {
            is LogCapUiState.Config -> DpSize(640.dp, Dp.Unspecified)
            is LogCapUiState.Capturing -> DpSize(420.dp, 300.dp)
            is LogCapUiState.Done -> DpSize(520.dp, 320.dp)
        }
    ) {
        // Config 态窗口高度 Unspecified（自动尺寸）：Surface 只能 fillMaxWidth——
        // 若 fillMaxSize 会把可用高度（整屏）全部吃掉，导致窗口被撑到屏幕高度；
        // 定高态（Capturing/Done）维持 fillMaxSize 铺满窗口
        val surfaceModifier = if (uiState is LogCapUiState.Config) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        }
        Surface(modifier = surfaceModifier, color = easyAdbColors.Surface) {
            when (val state = uiState) {
                is LogCapUiState.Config -> ConfigContent(
                    config = config,
                    dirShake = dirShake,
                    fileNamePreview = buildFileName(),
                    onBrowse = {
                        NativeFilePicker.pickFolder("选择保存目录", config.dir.ifBlank { null })?.let { config.dir = it }
                    },
                    onStart = ::startCapture,
                    onCancel = onDismiss
                )

                is LogCapUiState.Capturing -> CapturingContent(
                    file = state.file,
                    startedAtMs = state.startedAtMs,
                    onStop = ::stopCapture
                )

                is LogCapUiState.Done -> DoneContent(
                    file = state.file,
                    sizeBytes = state.sizeBytes,
                    durationMs = state.durationMs,
                    onRecapture = { uiState = LogCapUiState.Config },
                    onOpenDir = {
                        openInExplorer(state.file.parentFile)
                        onDismiss()
                    },
                    onClose = onDismiss
                )
            }
        }
    }
}

// ───────────────────────── 配置页 ─────────────────────────

@Composable
private fun ConfigContent(
    config: LogCapConfig,
    dirShake: FieldShakeState,
    fileNamePreview: String,
    onBrowse: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    // 高度自适应（SP-2003 调整1）：窗口高度 Unspecified → 官方按内容自然高度定框；
    // 此处去掉旧「无界测量上报」回路（与官方自动尺寸叠加曾致空白/震荡）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        ConfigContentInner(config, dirShake, fileNamePreview, onBrowse, onStart, onCancel)
    }
}

@Composable
private fun ConfigContentInner(
    config: LogCapConfig,
    dirShake: FieldShakeState,
    fileNamePreview: String,
    onBrowse: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
        // ── 保存目录 + 浏览（H5 .lc-name-row + #lcDirBrowse）──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("保存目录")
                ShakeableTextField(
                    value = config.dir,
                    onValueChange = { config.dir = it },
                    placeholder = "可填写本机任意目录",
                    shakeState = dirShake
                )
            }
            Column(modifier = Modifier.padding(top = 20.dp)) {
                PlainToolButton(text = "浏览", onClick = onBrowse)
            }
        }

        // ── 文件名 seg + 输入行（H5 #lcNameSeg / .lc-name-row）──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NameModeSeg(
                    custom = config.nameModeCustom,
                    onSelect = { config.nameModeCustom = it }
                )
                Text(
                    text = if (config.nameModeCustom) "完全自定义文件名（.log 自动补全）"
                    else "文件名 = 前缀_日期.log（.log 自动补全）",
                    fontSize = 11.5.sp,
                    color = easyAdbColors.TextDisabled
                )
            }
            if (config.nameModeCustom) {
                Column {
                    FieldLabel("文件名")
                    SimpleTextField(
                        value = config.customName,
                        onValueChange = { config.customName = it },
                        placeholder = "例如：复现闪退日志"
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1.35f)) {
                        FieldLabel("文件名前缀")
                        SimpleTextField(
                            value = config.prefix,
                            onValueChange = { config.prefix = it },
                            placeholder = "可留空"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel("日期")
                        ReadOnlyStampField()
                    }
                }
            }
        }

        // ── logcat 格式单选卡列表（H5 .lc-radio-list）──
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            LOG_FORMATS.forEachIndexed { index, opt ->
                FormatRadioCard(
                    option = opt,
                    selected = config.formatIndex == index,
                    onClick = { config.formatIndex = index }
                )
                if (opt.custom && config.formatIndex == index) {
                    SimpleTextField(
                        value = config.customArgs,
                        onValueChange = { config.customArgs = it },
                        placeholder = "例如：-v color -s ActivityManager",
                        mono = true
                    )
                }
            }
        }

        // ── 抓取前清理设备日志缓存（H5 .lc-check）──
        ClearBufferCheck(
            checked = config.clearBuffer,
            onCheckedChange = { config.clearBuffer = it }
        )

        // ── 命令预览（H5 .cmd-preview）──
        CommandPreview(
            captureCmd = "adb logcat ${LOG_FORMATS[config.formatIndex].let {
                if (it.custom) (config.customArgs.trim().ifEmpty { "-v threadtime" }) else it.args
            }} > …\\${fileNamePreview}"
        )

        // ── 底部：取消 + 开始抓取（底边距由外层 Column 统一提供）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlainButton(text = "取消", onClick = onCancel)
            Spacer(Modifier.width(10.dp))
            PrimaryButton(text = "开始抓取", onClick = onStart)
        }
}

/** 文件名模式 seg（H5 #lcNameSeg；CommandsPanel 同款 SegTrack 风格） */
@Composable
private fun NameModeSeg(custom: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(easyAdbColors.SegTrack)
            .border(1.dp, easyAdbColors.Border, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("前缀 + 日期" to false, "自定义文件名" to true).forEach { (label, mode) ->
            val active = custom == mode
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    active -> easyAdbColors.SegActiveText
                    hovered -> easyAdbColors.TextPrimary
                    else -> easyAdbColors.TextSecondary
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) easyAdbColors.SegActiveBg else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(mode) }
                    .hoverable(interaction)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/** logcat 格式单选卡（H5 .lc-radio：卡片 + 圆点 + 主文案 + 副文案） */
@Composable
private fun FormatRadioCard(option: LogFormatOption, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> easyAdbColors.Primary.copy(alpha = 0.08f)
                    hovered -> easyAdbColors.Field
                    else -> easyAdbColors.Field.copy(alpha = 0.4f)
                }
            )
            .border(
                1.dp,
                when {
                    selected -> easyAdbColors.Primary.copy(alpha = 0.55f)
                    hovered -> easyAdbColors.Border
                    else -> easyAdbColors.Border.copy(alpha = 0.6f)
                },
                shape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .hoverable(interaction)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // 圆点（选中实心蓝）
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(50))
                .border(2.dp, if (selected) easyAdbColors.Primary else easyAdbColors.TextDisabled, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(easyAdbColors.Primary))
            }
        }
        Column {
            Text(
                text = option.label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (option.label.startsWith("-v")) easyAdbMonospace else null,
                color = if (selected) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
            )
            Text(
                text = option.desc,
                fontSize = 11.sp,
                color = easyAdbColors.TextDisabled,
                lineHeight = 15.sp
            )
        }
    }
}

/** 清缓冲勾选项（SP-2003 调整6：去掉包裹卡片与副文案，勾选框 + 单行文字） */
@Composable
private fun ClearBufferCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) }
            .hoverable(interaction)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // 勾选框：17dp 圆角方，选中蓝底 + 白勾
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) easyAdbColors.Primary else Color.Transparent)
                .border(
                    1.5.dp,
                    if (checked) easyAdbColors.Primary else easyAdbColors.TextDisabled,
                    RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = CheckMark,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = Color.White
                )
            }
        }
        Text(
            text = "抓取前清空设备日志缓存",
            fontSize = 12.5.sp,
            color = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
        )
    }
}

/** 命令预览行（H5 .cmd-preview：tag + mono 命令；清空缓存为前置步骤不展示，仅抓取主命令） */
@Composable
private fun CommandPreview(captureCmd: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.Transparent,
                shape = RoundedCornerShape(0.dp)
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        PreviewRow(tag = "抓取", tagColor = easyAdbColors.Primary, cmd = captureCmd)
    }
}

@Composable
private fun PreviewRow(tag: String, tagColor: Color, cmd: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = tag,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = tagColor,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(tagColor.copy(alpha = 0.12f))
                .border(1.dp, tagColor.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 2.5.dp)
        )
        Text(
            text = cmd,
            fontFamily = easyAdbMonospace,
            fontSize = 11.5.sp,
            color = easyAdbColors.CmdPreviewText,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ───────────────────────── 抓取中页 ─────────────────────────

/** 抓取中页（H5 .modal-logcap 抓取中态）：双环动画 + 字节数 + 时长 + 唯一「停止抓取」 */
@Composable
private fun CapturingContent(file: File, startedAtMs: Long, onStop: () -> Unit) {
    // 真实落盘大小：每秒轮询 File.length()（SP-2003 决策：展示的永远是已写入的真实体积）
    var sizeBytes by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(file.absolutePath) {
        while (isActive) {
            sizeBytes = runCatching { file.length() }.getOrDefault(0L)
            elapsedSec = (System.currentTimeMillis() - startedAtMs) / 1000
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 双环旋转 + 中心文档图标（H5 .lc-orb：外环 1.1s 顺 / 内环 0.8s 逆）
        Box(modifier = Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            OrbitRing(diameter = 74.dp, stroke = 2.5.dp, sweep = 100f, periodMs = 1100, reverse = false)
            OrbitRing(diameter = 56.dp, stroke = 2.dp, sweep = 90f, periodMs = 800, reverse = true)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(easyAdbColors.Primary.copy(alpha = 0.14f))
                    .border(1.dp, easyAdbColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EasyAdbTileIcons.LogCapture,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = easyAdbColors.PrimaryLight
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // 字节数（tabular-nums 防跳动；KB/MB 自适应）
        Text(
            text = formatBytes(sizeBytes),
            fontFamily = easyAdbMonospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = easyAdbColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "已抓取 %02d:%02d · 写入中…".format(Locale.US, elapsedSec / 60, elapsedSec % 60),
            fontSize = 12.sp,
            color = easyAdbColors.TextDisabled
        )
        Spacer(Modifier.height(22.dp))
        DangerButton(text = "停止抓取", onClick = onStop)
    }
}

/**
 * 旋转弧环（H5 .lc-orb .ring/.ring2）：periodMs 周期线性 360° 旋转的 [sweep] 弧，
 * [reverse] = true 时逆时针。外环双色（Primary+PrimaryLight 两段），内环单色 AccentSky。
 */
@Composable
private fun OrbitRing(diameter: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp, sweep: Float, periodMs: Int, reverse: Boolean) {
    val transition = rememberInfiniteTransition(label = "lc-orbit")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reverse) -360f else 360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "lc-orbit-angle"
    )
    val outerStart = easyAdbColors.Primary
    val outerMid = easyAdbColors.PrimaryLight
    val innerColor = easyAdbColors.AccentSky
    Canvas(modifier = Modifier.size(diameter)) {
        val s = stroke.toPx()
        val inset = s / 2
        val arcSize = Size(size.width - s, size.height - s)
        drawArc(
            color = if (reverse) innerColor else outerMid,
            startAngle = angle - 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = s, cap = StrokeCap.Round)
        )
        // 外环双色：再画一小段 Primary 增强双色感（H5 border-top/right 双色）
        if (!reverse) {
            drawArc(
                color = outerStart,
                startAngle = angle - 90f - sweep / 2,
                sweepAngle = sweep / 2,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = s, cap = StrokeCap.Round)
            )
        }
    }
}

// ───────────────────────── 完成页 ─────────────────────────

/** 完成页（H5 .lc-done）：绿勾徽标 + 文件路径 + 大小/时长 chip + 三按钮 */
@Composable
private fun DoneContent(
    file: File,
    sizeBytes: Long,
    durationMs: Long,
    onRecapture: () -> Unit,
    onOpenDir: () -> Unit,
    onClose: () -> Unit
) {
    // 绿勾弹出动画（H5 @keyframes lcPop：0.35s 回弹缩放）
    val badgeScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "lc-badge"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                }
                .clip(RoundedCornerShape(50))
                .background(easyAdbColors.StatusOnline.copy(alpha = 0.12f))
                .border(1.5.dp, easyAdbColors.StatusOnline.copy(alpha = 0.35f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CheckMark,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = easyAdbColors.StatusOnline
            )
        }
        Spacer(Modifier.height(13.dp))
        Text("抓取完成", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = easyAdbColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "日志已保存为 ${file.name}",
            fontSize = 12.5.sp,
            color = easyAdbColors.TextDisabled,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = file.absolutePath,
            fontFamily = easyAdbMonospace,
            fontSize = 11.sp,
            color = easyAdbColors.CmdPreviewText,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetaChip(label = "文件大小", value = formatBytes(sizeBytes))
            MetaChip(label = "抓取时长", value = "%02d:%02d".format(Locale.US, durationMs / 60000, durationMs / 1000 % 60))
        }
        Spacer(Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlainButton(text = "重新抓取", onClick = onRecapture)
            Spacer(Modifier.weight(1f))
            PrimaryButton(text = "打开所在目录", onClick = onOpenDir)
            PlainButton(text = "关闭", onClick = onClose)
        }
    }
}

@Composable
private fun MetaChip(label: String, value: String) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .background(easyAdbColors.Field.copy(alpha = 0.5f), shape)
            .border(1.dp, easyAdbColors.Border.copy(alpha = 0.6f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextDisabled)
        Text(value, fontFamily = easyAdbMonospace, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextSecondary)
    }
}

// ───────────────────────── 复用小组件 ─────────────────────────

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        // SP-2003 调整3：标题文字提对比（亮色近黑 TextPrimary，暗色 token 自适应亮白）
        color = easyAdbColors.TextPrimary,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
    )
}

/** 标准单行输入（H5 .mfield input：Field 底 + Border 1dp、10dp 圆角、focus 蓝描边） */
@Composable
private fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(easyAdbColors.Field, shape)
            .border(1.dp, if (isFocused) easyAdbColors.Primary else easyAdbColors.Border, shape)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = if (mono) easyAdbMonospace else null,
                fontSize = 12.5.sp,
                color = easyAdbColors.TextPrimary
            ),
            cursorBrush = SolidColor(easyAdbColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 12.5.sp, color = easyAdbColors.TextHint)
        }
    }
}

/** 带抖动的输入框（目录）：SimpleTextField + FieldShakeState 红边抖动 + 聚焦 */
@Composable
private fun ShakeableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    shakeState: FieldShakeState
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shakeFrame = shakeState.observeFrame(focusRequester)
    val shakeRed = easyAdbColors.AccentRed
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .graphicsLayer { translationX = shakeFrame.translationXPx }
            .clip(shape)
            .background(easyAdbColors.Field, shape)
            .drawBehind {
                val overlay = shakeFrame.redOverlayAlpha
                if (overlay > 0f) drawRect(color = shakeRed.copy(alpha = overlay))
            }
            .border(
                1.dp,
                when {
                    shakeFrame.redEdge -> shakeRed
                    isFocused -> easyAdbColors.Primary
                    else -> easyAdbColors.Border
                },
                shape
            )
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.5.sp, color = easyAdbColors.TextPrimary),
            cursorBrush = SolidColor(easyAdbColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
        )
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 12.5.sp, color = easyAdbColors.TextHint)
        }
    }
}

/** 只读时间戳字段（H5 #lcDate readonly）：Field 底 + 次级色等宽文本 */
@Composable
private fun ReadOnlyStampField() {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(easyAdbColors.Field, shape)
            .border(1.dp, easyAdbColors.Border, shape)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = LocalDateTime.now().format(STAMP_FORMATTER),
            fontFamily = easyAdbMonospace,
            fontSize = 12.sp,
            color = easyAdbColors.TextSecondary
        )
    }
}

/** 浏览按钮（H5 .ss-iconbtn 带文字变体） */
@Composable
private fun PlainToolButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(34.dp)
            .hoverable(interaction)
            .background(
                if (hovered) easyAdbColors.MiniBtnBgHover else easyAdbColors.MiniBtnBg,
                RoundedCornerShape(7.dp)
            )
            .border(
                1.dp,
                if (hovered) easyAdbColors.PrimaryLight else easyAdbColors.Border,
                RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
        )
    }
}

/** 主操作按钮（开始抓取/打开所在目录），对齐 H5 .btn-primary（ScreenshotDialog 同规格） */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                clip = false,
                ambientColor = PrimaryGradShadow,
                spotColor = PrimaryGradShadow
            )
            .background(Brush.verticalGradient(listOf(PrimaryGradTop, PrimaryGradBottom)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.5.sp)
    }
}

/** 次级按钮（取消/关闭/重新抓取），对齐 H5 .btn-plain */
@Composable
private fun PlainButton(text: String, onClick: () -> Unit) {
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

/** 危险按钮（停止抓取），对齐 H5 .btn-danger（UninstallConfirmDialog 同规格） */
@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .hoverable(interaction)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                clip = false,
                ambientColor = DangerGradBottom.copy(alpha = 0.35f),
                spotColor = DangerGradBottom.copy(alpha = 0.35f)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (hovered) DangerHoverTop else DangerGradTop,
                        if (hovered) DangerHoverTop else DangerGradBottom
                    )
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.5.sp)
    }
}

// ───────────────────────── 工具函数 / 常量 ─────────────────────────

/** .btn-primary/.btn-danger 渐变固定色（H5 明暗同值硬编码） */
private val PrimaryGradTop = Color(0xFF3F88F8)
private val PrimaryGradBottom = Color(0xFF2563EB)
private val PrimaryGradShadow = Color(0x612563EB)
private val DangerGradTop = Color(0xFFEF4444)
private val DangerGradBottom = Color(0xFFDC2626)
private val DangerHoverTop = Color(0xFFF05252)

/** 白勾/完成对勾（H5 lc-check 与 .ok-badge 的对勾 path） */
private val CheckMark: ImageVector by lazy {
    val builder = ImageVector.Builder(
        name = "LcCheckMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    builder.addPath(
        pathData = addPathNodes("M20 6 9 17l-5-5"),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    )
    builder.build()
}

/** KB/MB 自适应（抓取中字节数与完成态 chip 共用） */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.2f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** 打开目录所在资源管理器（FileTransferPanel.openInExplorer 同款） */
private fun openInExplorer(dir: File?) {
    if (dir == null || !dir.isDirectory) return
    runCatching {
        ProcessBuilder("explorer.exe", dir.absolutePath).start()
    }
}

/**
 * 弹窗窗口图标：矢量线稿画到 64px 位图染品牌蓝（Window.icon 收 Painter 而
 * rememberVectorPainter 无 tint）。
 */
@Composable
private fun rememberWindowIconPainter(icon: ImageVector): androidx.compose.ui.graphics.painter.Painter {
    val painter = rememberVectorPainter(icon)
    return remember(painter) {
        val iconSize = Size(64f, 64f)
        val bitmap = androidx.compose.ui.graphics.ImageBitmap(64, 64)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(bitmap),
            size = iconSize
        ) {
            with(painter) {
                draw(size = iconSize, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF3F88F8)))
            }
        }
        androidx.compose.ui.graphics.painter.BitmapPainter(bitmap)
    }
}
