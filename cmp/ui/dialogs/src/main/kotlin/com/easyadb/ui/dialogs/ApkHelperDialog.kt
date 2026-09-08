package com.easyadb.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.apk.ApkInfo
import com.easyadb.core.apk.ApkParseResult
import com.easyadb.ui.designsystem.AppImages
import com.easyadb.ui.designsystem.LogColor
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Toolkit
import java.io.File

/**
 * APK Helper 对话框（Material 风格）。
 *
 * 信息结构对齐 Python `src/widget/Dialogs.py::APKHelperDialog`：
 * - 「APK信息」区：包名 / 名称 / 应用特征信息（Launcher应用、Icon展示、系统应用，
 *   文字 + 状态图标，与原版一致）/ 启动入口 / 证书MD5 / 签名版本 / App版本号 /
 *   代码版本号 / Min.SDK / 权限要求，应用图标（64dp）+ 分辨率信息位于
 *   「App版本号 ~ Min.SDK」三行右侧
 * - 「文件信息」区：文件名 / MD5 / 大小 / 日期
 * - 底部提示「拖文件到窗口即可检查apk信息」，解析失败时红字显示原因
 * - 整窗支持拖入 APK 文件立即解析（[apkDropTarget]）
 *
 * 视觉：不复刻 PyQt 原版样式，采用 Compose Material 卡片 + 只读信息框；
 * value 为不可编辑的"编辑框效果"，支持选择复制，鼠标悬停时边框变主题色
 * （对齐原版 HoverQLineEdit 的 hover 语义）。整页不滚动，仅权限要求的多行
 * 值框内部可垂直滚动；窗口尺寸随屏幕 DPI 缩放自适应（宽 28% × 高 65%）。
 *
 * @param parseResult 解析结果（null 表示未解析）
 * @param isParsing 是否正在解析
 * @param onDismiss 关闭对话框回调
 * @param onParse 拖入文件后回调，宿主负责调用 ApkParser 解析
 */
@Composable
fun ApkHelperDialog(
    parseResult: ApkParseResult?,
    isParsing: Boolean,
    onDismiss: () -> Unit,
    onParse: (apkPath: String) -> Unit
) {
    // 窗口尺寸对齐原版：屏幕宽 28% × 高 65%。
    // 注意：Windows 下 JVM 的 Toolkit.screenSize 返回的是已按系统缩放折算的
    // 逻辑像素（如 200% 缩放的高分屏返回 1440×900），其数值与 Compose dp 一致，
    // 因此直接取比例即可，不能再除以任何 DPI 缩放系数（否则窗口被二次缩小）。
    // 实测（双显示器：200% 高分屏 + 100% 低分屏）验证：
    // screenSize=1440×900、screenResolution=192、Compose density=2.0。
    // 高度下限 700dp 需容纳整页内容（两卡片 + 固定高度权限框 + 底部提示），
    // 上限为屏幕 92% 防超出；内容高度变化时需同步校对该值。
    val dialogSize = remember {
        val screen = Toolkit.getDefaultToolkit().screenSize
        val width = maxOf(screen.width * 0.28f, 460f).dp
        val height = minOf(maxOf(screen.height * 0.65f, 700f), screen.height * 0.92f).dp
        width to height
    }
    val dialogState = rememberDialogState(width = dialogSize.first, height = dialogSize.second)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "APK Helper",
        resizable = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().apkDropTarget { onParse(it) },
            color = MaterialTheme.colors.background
        ) {
            val info = (parseResult as? ApkParseResult.Success)?.info

            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── APK 信息 ──
                InfoSection(title = "APK信息") {
                    InfoRow("包名", info?.packageName)
                    InfoRow("名称", info?.label)
                    FeatureRow(info)
                    InfoRow("启动入口", info?.launchableActivity)
                    InfoRow("证书MD5", info?.signatureMd5)
                    InfoRow("签名版本", info?.signatureVersions?.joinToString(","))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            InfoRow("App版本号", info?.versionName)
                            InfoRow("代码版本号", info?.versionCode)
                            InfoRow("Min.SDK", info?.minSdk)
                        }
                        // 应用图标 + 分辨率：对齐原版位于「App版本号 ~ Min.SDK」三行右侧
                        // 注意：此处不可用 weight 撑高对齐——wrap-content 的 Column 中
                        // weight(fill=true) 会占满父级最大高度，把整行乃至卡片撑满窗口
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(start = 10.dp).width(76.dp)
                        ) {
                            ApkLogo(info)
                        }
                    }
                    PermissionRow(info)
                }

                // ── 文件信息 ──
                InfoSection(title = "文件信息") {
                    InfoRow("文件名", info?.fileName)
                    InfoRow("MD5", info?.fileMd5)
                    InfoRow("大小", info?.let { formatFileSize(it.fileBytes) })
                    InfoRow("日期", info?.lastModified)
                }

                // ── 底部提示：紧跟文件信息卡片，剩余空白留在窗口底部 ──
                Text(
                    text = when {
                        isParsing -> "解析中...."
                        parseResult is ApkParseResult.Failure -> parseResult.reason
                        else -> "拖文件到窗口即可检查apk信息"
                    },
                    fontSize = 13.sp,
                    color = if (parseResult is ApkParseResult.Failure) {
                        LogColor.Error
                    } else {
                        MaterialTheme.colors.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 信息分区卡片：圆角 Surface + 主题色标题。
 */
@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.surface,
        elevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.primary
            )
            content()
        }
    }
}

/**
 * 标签（文本控件）+ 值（只读编辑框）的行。
 * @param value null 表示尚未解析（编辑框显示为空）
 */
@Composable
private fun InfoRow(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.width(70.dp)
        )
        ReadOnlyField(value = value.orEmpty(), modifier = Modifier.weight(1f))
    }
}

/**
 * 应用特征信息行：Launcher应用 / Icon展示 / 系统应用，布局对齐 Python 原版——
 * 每项为「文字: + 20dp 状态图标」；未解析时显示 help 问号占位，
 * 解析后替换为绿点（是）/ 红点（否）。
 */
@Composable
private fun FeatureRow(info: ApkInfo?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FeatureItem("Launcher应用", info?.isLauncherApp)
        FeatureItem("Icon展示", info?.isShowLaunchIcon)
        FeatureItem("系统应用", info?.isSystemApp)
    }
}

@Composable
private fun FeatureItem(name: String, on: Boolean?) {
    val stateIcon = when (on) {
        null -> AppImages.help()
        true -> AppImages.deviceConnected()
        false -> AppImages.deviceDisconnected()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "$name:",
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface
        )
        Image(
            painter = stateIcon,
            contentDescription = name,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 权限要求行：只保留 android.permission.* 并取末段，"-" 前缀逐行展示。
 * 对齐原版 __startApkParse 的权限格式化。
 */
@Composable
private fun PermissionRow(info: ApkInfo?) {
    val text = remember(info) {
        info?.permissions
            ?.filter { it.startsWith("android.permission") }
            ?.joinToString("\n") { "- ${it.substringAfterLast('.')}" }
            .orEmpty()
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "权限要求",
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.width(70.dp)
        )
        ReadOnlyField(
            value = text,
            // 固定高度（160dp 的 2/3）：解析前后尺寸不变，避免内容增多时
            // 撑高卡片把文件信息挤出可视区；窗口 700dp 高度下限按此校准
            modifier = Modifier.weight(1f).height(106.dp),
            scrollable = true
        )
    }
}

/**
 * 应用图标（64dp）+ 分辨率信息。
 * 图标缺失或解码失败（如 adaptive-icon xml）时显示占位。
 */
@Composable
private fun ApkLogo(info: ApkInfo?) {
    val bitmap = remember(info?.iconFilePath) {
        val path = info?.iconFilePath
        if (path.isNullOrBlank()) null
        else try {
            SkiaImage.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = "app icon",
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Spacer(modifier = Modifier.size(64.dp))
    }
    if (!info?.iconSizeInfo.isNullOrBlank()) {
        Text(
            text = info?.iconSizeInfo.orEmpty(),
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface
        )
    }
}

/**
 * 只读信息框：不可编辑的"编辑框效果"，文本可选择复制（SelectionContainer），
 * 鼠标悬停时边框变主题色并浮起（对齐原版 HoverQLineEdit 的 hover 语义）。
 * 值为空时显示为空，不展示占位符。
 *
 * @param scrollable true 时内容超高可垂直滚动（多行场景，如权限列表）
 */
@Composable
private fun ReadOnlyField(
    value: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false
) {
    var isHovered by remember { mutableStateOf(false) }
    val borderColor = if (isHovered) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
    }
    val shape = RoundedCornerShape(6.dp)
    val contentModifier = if (scrollable) {
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }
    Box(
        modifier = modifier
            .shadow(if (isHovered) 4.dp else 0.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colors.surface)
            .border(1.dp, borderColor, shape)
            .onHover { isHovered = it }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        SelectionContainer {
            Text(
                text = value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onSurface,
                maxLines = if (scrollable) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = contentModifier
            )
        }
    }
}

/**
 * 悬停状态检测修饰符。
 */
private fun Modifier.onHover(onHoverChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            when (awaitPointerEvent().type) {
                PointerEventType.Enter -> onHoverChange(true)
                PointerEventType.Exit -> onHoverChange(false)
            }
        }
    }
}

/**
 * 文件大小格式化，对齐原版 "{:,} 字节({:.2f} MB)"。
 */
private fun formatFileSize(bytes: Long): String {
    return "%,d 字节(%.2f MB)".format(bytes, bytes / 1024.0 / 1024.0)
}
