package com.easyadb.ui.designsystem

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
 * 通用文件拖拽接收修饰符（SP-5001，[apkDropTarget] 的泛化版）。
 *
 * 与 apkDropTarget 的差异：不限扩展名（[extensions] 传 null 接收任意文件），
 * 回调携带全部合法文件列表（投递场景取首个，后续可扩展批量）。
 *
 * @param extensions 白名单扩展名（小写不含点）；null = 任意文件
 * @param onDragHover 拖拽悬停高亮回调（进入 true / 离开或松手 false），可空
 * @param onFilesDropped 拖入合法文件时回调（本地绝对路径列表，至少一项）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.fileDropTarget(
    extensions: Set<String>? = null,
    onDragHover: ((Boolean) -> Unit)? = null,
    onFilesDropped: (List<String>) -> Unit
): Modifier = composed {
    val target = remember(onFilesDropped, onDragHover, extensions) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                onDragHover?.invoke(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                onDragHover?.invoke(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragHover?.invoke(false)
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
                val files = (data as? List<*>)
                    ?.filterIsInstance<File>()
                    ?.filter { file ->
                        extensions == null || extensions.any { file.name.endsWith(".$it", ignoreCase = true) }
                    }
                    .orEmpty()
                if (files.isEmpty()) return false
                onFilesDropped(files.map { it.absolutePath })
                return true
            }
        }
    }
    dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target = target
    )
}
