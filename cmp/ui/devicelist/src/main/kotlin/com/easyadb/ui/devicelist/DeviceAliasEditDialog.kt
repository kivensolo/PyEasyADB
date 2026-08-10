package com.easyadb.ui.devicelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.database.DeviceRecord
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 设备备注（别名）编辑对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::device_alis_edit_dialog`：
 * - 标题："备注修改"
 * - 提示："请输入设备别名，便于识别:"
 * - 输入框：初始填入当前 alias
 * - 按钮："更新备注名称"
 * - 确认后回调 [onConfirm]，宿主负责写库并刷新树。
 */
@Composable
fun DeviceAliasEditDialog(
    device: DeviceRecord,
    onDismiss: () -> Unit,
    onConfirm: (newAlias: String) -> Unit
) {
    // 用 device.alias 作为 key：当数据库中的 alias 变化（例如刚刚保存了新别名）
    // 时强制重新初始化 aliasText，避免再次打开对话框时仍显示旧的别名。
    var aliasText by remember(device.alias) { mutableStateOf(device.alias) }
    val dialogState = rememberDialogState(width = 380.dp, height = 200.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "备注修改",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "请输入设备别名，便于识别（${device.ip}）：",
                    fontSize = 12.sp,
                    color = EasyAdbColors.TextPrimary
                )
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Box(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = aliasText.isNotBlank(),
                        onClick = { onConfirm(aliasText.trim()) }
                    ) {
                        Text("更新备注名称")
                    }
                }
            }
        }
    }
}
