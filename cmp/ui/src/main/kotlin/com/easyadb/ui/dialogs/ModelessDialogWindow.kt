package com.easyadb.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.first

/**
 * 非模态独立窗口弹窗（SP-2003 调整4）：功能进行中（如日志抓取）允许用户继续操作
 * 主窗口其他功能区。
 *
 * **为什么不用 DialogWindow**：Compose 把它的模态硬编码为
 * `ModalityType.DOCUMENT_MODAL`（Dialog.desktop.kt 内 `ComposeDialog(owner,
 * DOCUMENT_MODAL)`），无参数可关。低级重载自建 MODELESS ComposeDialog 的方案
 * 已实测不可行（pack/setSize 与 Compose 渲染层的初始化顺序竞态，出现 0 高度
 * 白窗贴屏幕顶、无法拖动）。
 *
 * **方案**：直接用非 owner 的 [Window]（它本来就是 MODELESS 语义的独立窗口），
 * 完整继承官方的尺寸/位置/标题栏/拖动管理。代价（可接受）：
 * - 无 owner 绑定 → 不强制保持在主窗口之上（用户可切换层叠，反而合理）；
 * - [size] 变化时官方会 resize。
 *
 * **居中口径（与截图/视频录制弹窗同机制）**：`WindowPosition(Alignment.Center)`
 * 走 compose 官方 `align()`——全程 AWT 像素坐标、尺寸先于位置应用，居中拿到的
 * 是已落地的新尺寸（勿手动算坐标包 .dp，Windows DPI 缩放下 dp/px 混算必漂移）。
 * 唯一差别：高度 Unspecified（自动尺寸）时首帧尺寸未定，需等官方把解析后的
 * 实际尺寸回写 `state.size`（componentResized 路径）再设位置；定高态立即设。
 * 仅 [recenterKey]（三态切换）触发重新居中，内容高度微调保持原位不跳动。
 *
 * Esc（KeyUp）触发 [onCloseRequest]，与 H5 弹窗 Esc 退出语义一致。
 */
@Composable
internal fun ModelessDialogWindow(
    onCloseRequest: () -> Unit,
    title: String,
    icon: androidx.compose.ui.graphics.painter.Painter?,
    size: DpSize,
    recenterKey: Any? = null,
    resizable: Boolean = false,
    content: @Composable androidx.compose.ui.window.WindowScope.() -> Unit
) {
    val currentOnClose by rememberUpdatedState(onCloseRequest)
    val currentTitle by rememberUpdatedState(title)
    val currentIcon by rememberUpdatedState(icon)
    val currentSize by rememberUpdatedState(size)
    val currentRecenterKey by rememberUpdatedState(recenterKey)

    // 初始 position 就给 Alignment.Center：AwtWindow 时序为 create → update(先
    // setSizeSafely[含 pack 解析自动高度] 后 setPositionSafely) → 下一 AWT tick 才
    // isVisible=true。首次 update 时尺寸已落地、align() 用真实尺寸居中，且发生在
    // 可见之前——不会先落平台默认层叠位置（主窗口左上角）再修正产生闪动
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = size.orUnspecified()
    )

    // 三态切换：重设尺寸 →（自动高度时）等实际尺寸落地 → 官方 Alignment 居中。
    // snapshotFlow 挂起等待，不会用未定的尺寸定位
    LaunchedEffect(currentRecenterKey) {
        state.size = currentSize.orUnspecified()
        if (currentSize.height == Dp.Unspecified) {
            snapshotFlow { state.size }.first { it.height != Dp.Unspecified }
        }
        state.position = WindowPosition(Alignment.Center)
    }

    Window(
        onCloseRequest = currentOnClose,
        state = state,
        title = currentTitle,
        icon = currentIcon,
        resizable = resizable,
        onPreviewKeyEvent = { false },
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                currentOnClose()
                true
            } else {
                false
            }
        }
    ) {
        content()
    }
}

/** 只保留指定值，未指定维度维持 Unspecified（官方自动尺寸语义） */
private fun DpSize.orUnspecified(): DpSize = DpSize(
    if (width.isSpecified()) width else Dp.Unspecified,
    if (height.isSpecified()) height else Dp.Unspecified
)

private fun Dp.isSpecified(): Boolean = this != Dp.Unspecified
