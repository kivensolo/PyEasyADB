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
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：APK 文件拖拽接收修饰符（designsystem 底层组件，供功能卡/
 *              安装弹窗等上层模块共用）。仅接受 `.apk` 文件（大小写不敏感），
 *              取第一个合法 APK，回调出本地绝对路径；非法文件忽略。
 *              通过 [dragAndDropTarget] + [DragAndDropEvent.awtTransferable] +
 *              [DataFlavor.javaFileListFlavor] 读取外部拖入的文件。
 *              onDragHover 用于拖拽悬停高亮：进入 true / 离开或松手 false。
 *
 * @param onDragHover 拖拽悬停状态变化回调，可空（不需要高亮时省略）
 * @param onApkDropped 拖入合法 APK 时回调，参数为本地文件绝对路径
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.apkDropTarget(
    onDragHover: ((Boolean) -> Unit)? = null,
    onApkDropped: (String) -> Unit
): Modifier = composed {
    val target = remember(onApkDropped, onDragHover) {
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
