package com.easyadb.ui.mirror

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.MirrorState
import com.easyadb.core.adb.ScrcpyLauncher
import com.easyadb.platform.Win32Interop
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import kotlinx.coroutines.delay
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel

/**
 * 设备实时预览面板（P8 独立窗口 + P9 嵌入模式）。
 *
 * 对齐 Python `win32Wrapper.py::ScrcpyEmbedWidget`：
 * - 「开启实时预览」/「关闭实时预览」按钮（状态互斥启用）
 * - 「嵌入模式」checkbox：运行中也可实时切换——勾选即把已运行的 scrcpy 窗口 reparent
 *   进面板（Win32Interop），取消即脱离还原为独立窗口（无需重启镜像，优于 Python 版
 *   的“运行中锁定”）；非 Windows 平台置灰。
 * - 嵌入后键盘焦点自动处理（收编时自动 SetFocus + 点击画面即原生聚焦），
 *   替代 Python 版“键盘无法控制请点红字”的手工 workaround。
 * - scrcpy 二进制缺失时显示红色下载指引（对齐 Python WARRING 文案）。
 *
 * 进程生命周期由宿主持有的 [ScrcpyLauncher] 管理，本面板只按 [mirrorState] 渲染
 * 并回调 [onStart]/[onStop]；嵌入仅是呈现方式切换，reparent 失败自动保持独立窗口。
 *
 * @param deviceIp 当前选中设备 IP（null 时「开启」禁用）
 * @param mirrorState 镜像状态（Idle / Running）
 * @param binaryReady scrcpy.exe 是否就绪
 * @param binaryPath scrcpy.exe 期望路径（缺失时展示在指引中）
 */
@Composable
fun MirrorPanel(
    deviceIp: String?,
    mirrorState: MirrorState,
    binaryReady: Boolean,
    binaryPath: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = mirrorState is MirrorState.Running
    var embedEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(easyAdbTokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceMd)
    ) {
        // ── 控制按钮行（P12 翻新：品牌色主按钮 + 描边次级按钮） ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceSm)
        ) {
            Button(
                enabled = deviceIp != null && !isRunning,
                onClick = onStart,
                shape = easyAdbTokens.shapeMd,
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = easyAdbColors.Primary,
                    contentColor = Color.White
                )
            ) {
                Text("开启实时预览")
            }
            Button(
                enabled = isRunning,
                onClick = onStop,
                shape = easyAdbTokens.shapeMd,
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = easyAdbColors.Surface,
                    contentColor = easyAdbColors.TextPrimary
                ),
                border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider)
            ) {
                Text("关闭实时预览")
            }
            // 嵌入模式：checkbox 压缩触区（28dp）让文字贴近，标签加大加深
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Checkbox(
                    checked = embedEnabled && Win32Interop.isWindows,
                    onCheckedChange = { embedEnabled = it },
                    // 对齐 Python 版：实时预览运行中锁定，停止后才能切换（运行中取消嵌入会导致显示异常）
                    enabled = Win32Interop.isWindows && !isRunning,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (Win32Interop.isWindows) "嵌入模式" else "嵌入模式（仅 Windows 支持）",
                    fontSize = 12.sp,
                    color = easyAdbColors.TextPrimary
                )
                if (Win32Interop.isWindows) {
                    Text(
                        text = "· scrcpy 的操作是右键点击返回",
                        fontSize = 10.sp,
                        color = easyAdbColors.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        // ── 状态行（仅独立窗口运行中 / 未选设备时提示；嵌入运行时嵌入区自明，不加文字） ──
        val statusText = when {
            isRunning && !embedEnabled ->
                "独立窗口运行中：${(mirrorState as MirrorState.Running).deviceIp}（窗口「${ScrcpyLauncher.WINDOW_TITLE}」）"
            deviceIp == null -> "未选中设备——请先在左侧设备列表中选择设备"
            else -> null
        }
        if (statusText != null) {
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = easyAdbColors.TextPrimary
            )
        }

        // ── scrcpy 缺失指引（对齐 Python WARRING 文案，手动安装路径） ──
        if (!binaryReady) {
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
                color = easyAdbColors.LogError
            )
        }

        // ── 嵌入区：运行中且勾选嵌入时收编 scrcpy 窗口 ──
        if (isRunning && embedEnabled) {
            EmbeddedScrcpyRegion(
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

/**
 * scrcpy 嵌入区（P9）：宿主为 SwingPanel 内的重型 [Canvas]（有独立 HWND，子窗口
 * 被裁剪在面板内）。进入组合后轮询按标题找到 scrcpy 窗口 → 子窗口化 + reparent +
 * 贴合尺寸 + 自动聚焦；面板尺寸变化实时 SetWindowPos 跟随；离开组合（取消勾选 /
 * 停止镜像 / 关闭 scrcpy）时脱离并还原独立窗口样式。
 *
 * 几何断言与全链路验证见 [main]（EmbedSpike）。
 */
@Composable
private fun EmbeddedScrcpyRegion(modifier: Modifier = Modifier) {
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
            // scrcpy 画面收编前的垫底色随主题（原 Color.White 在暗色下会白块闪烁）
            background = easyAdbColors.Surface,
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

        val host = Win32Interop.componentHwnd(hostCanvas)
        if (host == null) return@LaunchedEffect

        originalStyle = Win32Interop.applyChildStyle(child)
        if (!Win32Interop.setParent(child, host)) return@LaunchedEffect
        embedHwnd = child
        hostHwnd = host

        // 嵌入期常驻挂接输入队列 + 裸 SetFocus（不 detach、不抢回焦点）：KeySpike 与
        // 实测确认焦点是键盘透传的关键——attach→SetFocus→detach 会把焦点状态打散，
        // focusRequester.requestFocus() 会把焦点抢回 Compose，两者都会让键盘失效；
        // 焦点落在子窗口后键盘原生直达 scrcpy
        Win32Interop.attachThreadInputToWindow(child, true)

        Win32Interop.windowRect(host)?.let {
            Win32Interop.moveWindow(child, 0, 0, it.width, it.height)
        }
        // 自动聚焦：收编后立即取得键盘焦点，无需先点击画面
        Win32Interop.setFocusWindow(child)
    }

    // 离开组合：脱离父窗口 + 还原顶层样式 + 给个默认位置（避免叠在 (0,0)）
    DisposableEffect(Unit) {
        onDispose {
            val child = embedHwnd
            if (child != null) {
                Win32Interop.attachThreadInputToWindow(child, false)
                Win32Interop.setParent(child, null)
                originalStyle?.let { Win32Interop.restoreTopLevelStyle(child, it) }
                Win32Interop.moveWindow(child, 120, 120, 960, 720)
            }
        }
    }
}
