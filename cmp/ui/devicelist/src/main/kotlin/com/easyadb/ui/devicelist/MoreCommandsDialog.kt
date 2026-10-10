package com.easyadb.ui.devicelist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.config.CmdGroup
import com.easyadb.core.config.CmdItem
import com.easyadb.ui.designsystem.easyAdbColors

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWang
 * date：2026/10/9
 * description：完整命令集弹窗（P12 布局大改）——收纳 cmdConfig.xml 全量命令。
 *              内容直接复用 [CommandPanel]（master-detail：左大类单选 + 右细项
 *              单击执行），由快捷操作 Tab 的「更多命令」chip 唤起。
 */
@Composable
fun MoreCommandsDialog(
    cmdGroups: List<CmdGroup>,
    onDismiss: () -> Unit,
    onCommandClick: (CmdItem) -> Unit
) {
    val dialogState = rememberDialogState(width = 640.dp, height = 420.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "更多命令"
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = easyAdbColors.Surface
        ) {
            CommandPanel(
                cmdGroups = cmdGroups,
                onCommandClick = onCommandClick,
                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)
            )
        }
    }
}
