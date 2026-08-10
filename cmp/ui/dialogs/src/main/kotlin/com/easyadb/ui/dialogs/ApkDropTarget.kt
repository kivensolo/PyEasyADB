package com.easyadb.ui.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * APK 文件拖拽接收修饰符。
 *
 * 对齐 Python 原版 `DraggableLineEdit` / `DragDialog` 的拖拽语义：
 * 仅接受 `.apk` 文件（大小写不敏感，比原版严格 `.apk` 更适配 Windows），取第一个合法 APK，
 * 回调出本地绝对路径；非法文件忽略。
 *
 * 使用 Compose Multiplatform 的 [dragAndDropTarget] 修饰符，通过
 * [DragAndDropEvent.awtTransferable] + [DataFlavor.javaFileListFlavor] 读取外部文件。
 *
 * @param onApkDropped 拖入合法 APK 时回调，参数为本地文件绝对路径。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.apkDropTarget(
    onApkDropped: (String) -> Unit
): Modifier = composed {
    val target = remember(onApkDropped) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false
                }
                val data = try {
                    transferable.getTransferData(DataFlavor.javaFileListFlavor)
                } catch (_: Exception) {
                    // getTransferData 偶发抛 IOException / UnsupportedFlavorException，拖拽数据无效直接忽略
                    return false
                }
                val apk = (data as? List<*>)
                    ?.filterIsInstance<File>()
                    ?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return false
                onApkDropped(apk.absolutePath)
                return true
            }
        }
    }
    dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target = target
    )
}
