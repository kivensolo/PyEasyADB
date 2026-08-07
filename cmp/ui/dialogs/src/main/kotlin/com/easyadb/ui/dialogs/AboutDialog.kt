package com.easyadb.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.EasyAdbColors
import java.awt.Desktop
import java.net.URI

/**
 * 关于对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::AboutDialog`：
 * - 应用标题（黑底白字，居中，粗体大字号）
 * - 版本号
 * - 编译时间
 * - GitHub 链接（可点击跳转浏览器）
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val dialogState = rememberDialogState(width = 400.dp, height = 260.dp)
    val githubUrl = "https://github.com/kivensolo/PyEasyADB"

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "关于",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 应用标题（黑底白字） ──
                Surface(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    color = Color.Black
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "EasyADB",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── 版本号 ──
                Text(
                    text = "版本号：v2.0.0",
                    fontSize = 12.sp,
                    color = EasyAdbColors.TextPrimary
                )

                // ── 编译时间 ──
                Text(
                    text = "Compose Multiplatform 版本",
                    fontSize = 10.sp,
                    color = EasyAdbColors.TextSecondary
                )

                // ── GitHub 链接（可点击） ──
                val linkText = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF1565C0),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("GitHub/EasyPyADB")
                    }
                }
                ClickableText(
                    text = linkText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    onClick = {
                        openUrl(githubUrl)
                    }
                )
            }
        }
    }
}

/**
 * 用系统默认浏览器打开 URL。
 */
private fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {
        // 忽略打开失败的情况
    }
}
