package com.easyadb.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.apk.ApkInfo
import com.easyadb.ui.designsystem.EasyAdbColors
import java.awt.FileDialog
import java.awt.Frame

/**
 * APK Helper 对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::APKHelperDialog`：
 * - 标题："APK Helper"
 * - 浏览按钮选择 APK 文件
 * - 解析状态（加载中 / 完成）
 * - 解析结果展示（包名、名称、版本号、签名、权限等）
 *
 * @param apkInfo 解析结果（null 表示未解析或解析中）
 * @param isParsing 是否正在解析
 * @param onParse 选择文件后回调，宿主负责调用 ApkParser 解析
 *
 * 注意：拖拽 APK 功能待后续用 Swing interop（DropTarget）实现。
 */
@Composable
fun ApkHelperDialog(
    apkInfo: ApkInfo?,
    isParsing: Boolean,
    onDismiss: () -> Unit,
    onParse: (apkPath: String) -> Unit
) {
    val dialogState = rememberDialogState(width = 500.dp, height = 600.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "APK Helper",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── 文件选择按钮 ──
                Button(onClick = {
                    val dialog = FileDialog(null as Frame?, "选择 APK 文件", FileDialog.LOAD).apply {
                        setFile("*.apk")
                    }
                    dialog.isVisible = true
                    val selectedDir = dialog.directory
                    val selectedFile = dialog.file
                    if (selectedDir != null && selectedFile != null) {
                        val path = java.io.File(selectedDir, selectedFile).absolutePath
                        onParse(path)
                    }
                }) {
                    Text("浏览 APK 文件")
                }

                // ── 状态提示 ──
                Text(
                    text = when {
                        isParsing -> "正在解析..."
                        apkInfo != null -> "解析完成"
                        else -> "点击上方按钮选择 APK 文件"
                    },
                    fontSize = 11.sp,
                    color = EasyAdbColors.TextSecondary
                )

                // ── 解析结果 ──
                if (apkInfo != null) {
                    ApkInfoPanel(apkInfo)
                } else if (isParsing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("解析中...", color = EasyAdbColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * APK 信息展示面板。
 */
@Composable
private fun ApkInfoPanel(info: ApkInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("APK 信息", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EasyAdbColors.TextPrimary)

            InfoRow("包名", info.packageName)
            InfoRow("名称", info.label)
            InfoRow("App版本号", info.versionName)
            InfoRow("代码版本号", info.versionCode)
            InfoRow("Min.SDK", info.minSdk)
            InfoRow("Target.SDK", info.targetSdk)
            InfoRow("证书SHA-256", info.signatureDigest)
            InfoRow("文件路径", info.filePath)

            // 权限列表
            if (info.permissions.isNotEmpty()) {
                Text(
                    text = "权限要求（${info.permissions.size}）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EasyAdbColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    color = EasyAdbColors.PanelHeader
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    ) {
                        items(info.permissions) { perm ->
                            Text(
                                text = perm,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = EasyAdbColors.TextPrimary,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 标签 + 值的行。
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = EasyAdbColors.TextSecondary,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value.ifBlank { "-" },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = EasyAdbColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
