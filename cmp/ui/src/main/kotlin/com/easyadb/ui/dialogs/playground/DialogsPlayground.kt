package com.easyadb.ui.dialogs.playground

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.easyadb.core.apk.ApkInfo
import com.easyadb.core.apk.ApkParseResult
import com.easyadb.ui.designsystem.EasyAdbTheme
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.hoverBackground
import com.easyadb.ui.dialogs.AboutDialog
import com.easyadb.ui.dialogs.AddPackageDialog
import com.easyadb.ui.dialogs.ApkHelperDialog
import com.easyadb.ui.dialogs.InstallApkDialog
import com.easyadb.ui.dialogs.NewConnectDialog
import com.easyadb.ui.dialogs.ScreenRecordDialog
import com.easyadb.ui.dialogs.TextInputDialog
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 弹窗组件画廊（Playground）。
 *
 * 汇总展示 `ui/dialogs` 下全部弹窗组件，用于独立调 UI、走查明暗主题效果：
 * - 左侧为组件入口列表，点击打开对应弹窗（DialogWindow 为独立 OS 窗口，
 *   支持同时打开多个，便于对比 ApkHelperDialog 的未解析/已解析等状态；
 *   多个窗口初始叠放，拖开即可）。再次点击入口关闭该弹窗。
 * - 顶部提供明暗主题切换（EasyAdbTheme 整组换色，弹窗随 CompositionLocal 联动）。
 * - 弹窗内的按钮 / 输入回调不接真实 adb，统一打印到底部「事件日志」，
 *   可验证回调时序与参数；ScreenRecordDialog 附带假数据模拟（开始录制实时倒计时）。
 *
 * 运行方式：IDE 中点击本文件 `main()` 左侧绿色箭头，或命令行
 * `gradlew :ui:dialogs:run`。
 */
fun main() {
    application {
        var darkTheme by remember { mutableStateOf(false) }
        val openIds = remember { mutableStateListOf<String>() }
        val logs = remember { mutableStateListOf<String>() }
        val log: (String) -> Unit = { message ->
            logs.add("${TIME_FORMATTER.format(LocalTime.now())}  $message")
        }

        Window(
            onCloseRequest = { exitApplication() },
            title = "弹窗画廊 · ui/dialogs Playground",
            state = rememberWindowState(width = 1000.dp, height = 680.dp)
        ) {
            EasyAdbTheme(darkTheme = darkTheme) {
                PlaygroundScreen(
                    darkTheme = darkTheme,
                    onDarkThemeChange = { darkTheme = it },
                    openIds = openIds,
                    onToggleEntry = { id ->
                        if (id in openIds) openIds.remove(id) else openIds.add(id)
                    },
                    logs = logs,
                    onLog = log,
                    onClearLog = { logs.clear() }
                )
            }
        }
    }
}

/** 画廊主界面：左侧入口边栏 + 右侧空态提示与事件日志。 */
@Composable
private fun PlaygroundScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    openIds: List<String>,
    onToggleEntry: (String) -> Unit,
    logs: List<String>,
    onLog: (String) -> Unit,
    onClearLog: () -> Unit
) {
    // 满窗 Box：画廊内容 + 弹窗层（NewConnectDialog 已改主窗口内浮层，需盖全窗）
    Box(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        // ── 左侧：入口边栏 ──
        Column(
            modifier = Modifier.fillMaxHeight().width(280.dp).background(easyAdbColors.PanelHeader)
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp)) {
                Text(
                    text = "弹窗画廊",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = easyAdbColors.TextPrimary
                )
                Text(
                    text = "${entries.map { it.component }.distinct().size} 个组件 · ${entries.size} 个状态",
                    fontSize = 11.sp,
                    color = easyAdbColors.TextSecondary
                )
            }

            ThemeToggle(darkTheme = darkTheme, onChange = onDarkThemeChange)

            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).background(easyAdbColors.Divider)
            )

            // 各分组独立记录收起状态；收起只隐藏入口行，不影响已打开的弹窗窗口
            val collapsedGroups = remember { mutableStateListOf<String>() }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                entries.groupBy { it.group }.forEach { (group, groupEntries) ->
                    item(key = "group-$group") {
                        GroupCard(
                            group = group,
                            entries = groupEntries,
                            openIds = openIds,
                            collapsed = group in collapsedGroups,
                            onToggleCollapse = {
                                if (group in collapsedGroups) collapsedGroups.remove(group)
                                else collapsedGroups.add(group)
                            },
                            onToggleEntry = onToggleEntry
                        )
                    }
                }
            }
        }

        // ── 右侧：空态提示 + 事件日志 ──
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "弹窗组件 Playground",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = easyAdbColors.TextPrimary
                    )
                    Text(
                        text = "点击左侧列表打开弹窗，可同时打开多个对比状态；\n弹窗内的按钮 / 输入回调会实时打印到下方事件日志",
                        fontSize = 12.sp,
                        color = easyAdbColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            LogPanel(
                logs = logs,
                onClear = onClearLog,
                modifier = Modifier.fillMaxWidth().height(190.dp)
            )
        }
    }

    // ── 已打开的弹窗：DialogWindow 为独立 OS 窗口，随 openIds 增删 ──
    openIds.forEach { id ->
        val entry = entries.firstOrNull { it.id == id } ?: return@forEach
        key(entry.id) {
            // content 为函数类型属性，只能按位置传参（不支持命名参数）
            // darkTheme 显式传入：DialogWindow 是独立组合根，CompositionLocal 不跨窗口传播
            entry.content(darkTheme, { onToggleEntry(id) }, onLog)
        }
    }
    } // 满窗 Box（画廊内容 + 弹窗浮层）
}

/** 事件日志面板：弹窗回调按时间顺序打印，自动滚动到底部。 */
@Composable
private fun LogPanel(
    logs: List<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(easyAdbColors.Divider))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "事件日志",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空", fontSize = 12.sp) }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
            if (logs.isEmpty()) {
                Text(
                    text = "（暂无事件——在弹窗里点按钮、输内容试试）",
                    fontSize = 12.sp,
                    color = easyAdbColors.TextSecondary
                )
            } else {
                LazyColumn(state = listState) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = easyAdbColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分组卡片：一组一卡（圆角 + 描边 + 独立底色），卡与卡之间留白分区。
 * 组头为品牌色竖条 + 组名 + 条目数 + 展开箭头，点击整行收起/展开
 * （箭头随状态旋转，卡高 [animateContentSize] 平滑过渡）；组内为入口行。
 */
@Composable
private fun GroupCard(
    group: String,
    entries: List<DialogEntry>,
    openIds: List<String>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleEntry: (String) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .animateContentSize()
            .clip(shape)
            .background(MaterialTheme.colors.surface)
            .border(1.dp, easyAdbColors.Divider, shape)
    ) {
        // ── 组头：点击收起/展开 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverBackground()
                .clickable(interactionSource = null, indication = null) { onToggleCollapse() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(easyAdbColors.Primary)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = group,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${entries.size} 个",
                fontSize = 11.sp,
                color = easyAdbColors.TextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            val chevronRotation by animateFloatAsState(targetValue = if (collapsed) -90f else 0f)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (collapsed) "展开分组" else "收起分组",
                tint = easyAdbColors.TextSecondary,
                modifier = Modifier.size(16.dp).rotate(chevronRotation)
            )
        }

        // ── 组内入口行（收起时整体隐藏，卡高随 animateContentSize 收拢） ──
        if (!collapsed) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(easyAdbColors.Divider))
            entries.forEach { entry ->
                val isOpen = entry.id in openIds
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isOpen) easyAdbColors.Selected else Color.Transparent)
                        .hoverBackground()
                        .clickable(interactionSource = null, indication = null) {
                            onToggleEntry(entry.id)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = entry.component,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOpen) easyAdbColors.Primary else easyAdbColors.TextPrimary
                    )
                    Text(
                        text = entry.state,
                        fontSize = 11.sp,
                        color = easyAdbColors.TextSecondary
                    )
                }
            }
        }
    }
}

/** 明暗主题二段切换：切换后 MaterialTheme 槽位与扩展调色板整组生效。 */
@Composable
private fun ThemeToggle(darkTheme: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(easyAdbColors.Hover)
    ) {
        ToggleOption(label = "浅色", active = !darkTheme) { onChange(false) }
        ToggleOption(label = "深色", active = darkTheme) { onChange(true) }
    }
}

@Composable
private fun ToggleOption(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) easyAdbColors.Primary else Color.Transparent)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (active) easyAdbColors.TextOnPrimary else easyAdbColors.TextPrimary
        )
    }
}

/** 时间戳格式（事件日志行首）。 */
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

/** 画廊入口：一组假数据 + 状态说明 + 打开该弹窗的可组合内容。 */
private class DialogEntry(
    val id: String,
    val group: String,
    val component: String,
    val state: String,
    val content: @Composable (darkTheme: Boolean, onDismiss: () -> Unit, log: (String) -> Unit) -> Unit
)

/** ApkHelperDialog「解析成功」状态用的假数据（iconFilePath 留空 → 图标区显示占位）。 */
private val demoApkInfo = ApkInfo(
    packageName = "com.example.demoapp",
    label = "Demo 应用",
    versionName = "2.1.0",
    versionCode = "21000",
    minSdk = "21",
    launchableActivity = "com.example.demoapp.MainActivity",
    signatureMd5 = "A1B2C3D4E5F60718293A4B5C6D7E8F90",
    signatureVersions = listOf("v1", "v2", "v3"),
    permissions = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.WAKE_LOCK",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "com.example.demoapp.PERMISSION_CUSTOM" // 演示非 android.permission 前缀被过滤
    ),
    isLauncherApp = false,
    isShowLaunchIcon = true,
    isSystemApp = false,
    iconSizeInfo = "192x192",
    fileName = "demoapp-release-v2.1.0.apk",
    fileBytes = 38_828_800L,
    fileMd5 = "0F1E2D3C4B5A69788796A5B4C3D2E1F0",
    lastModified = "2026-09-01 10:23:45"
)

/** 画廊全部入口。同一个组件的多个状态（如 ApkHelperDialog）各自一条，便于并排对比。 */
private val entries = listOf(
    DialogEntry(
        id = "token-showcase",
        group = "设计 Token",
        component = "TokenShowcaseDialog",
        state = "圆角/间距/elevation/描边/色板 · 明暗并排"
    ) { darkTheme, onDismiss, _ ->
        TokenShowcaseDialog(darkTheme = darkTheme, onDismiss = onDismiss)
    },
    DialogEntry(
        id = "text-input",
        group = "基础输入",
        component = "TextInputDialog",
        state = "多行输入 + 清空 / 输入"
    ) { _, onDismiss, log ->
        TextInputDialog(
            onDismiss = onDismiss,
            onInput = { text ->
                log("TextInputDialog.onInput(\"${text.take(50)}\")")
                onDismiss()
            }
        )
    },
    DialogEntry(
        id = "add-package",
        group = "基础输入",
        component = "AddPackageDialog",
        state = "包名输入 + Add（未接入主流程）"
    ) { _, onDismiss, log ->
        AddPackageDialog(
            onDismiss = onDismiss,
            onAdd = { pkg ->
                log("AddPackageDialog.onAdd(\"$pkg\")")
                onDismiss()
            }
        )
    },
    DialogEntry(
        id = "new-connect",
        group = "基础输入",
        component = "NewConnectDialog",
        state = "名称(可选)/IP/端口 三字段 + 端口校验"
    ) { _, onDismiss, log ->
        NewConnectDialog(
            onDismiss = onDismiss,
            onConnect = { name, ip ->
                log("NewConnectDialog.onConnect(\"$name\", \"$ip\") → adb connect $ip")
                onDismiss()
            }
        )
    },
    DialogEntry(
        id = "install-apk",
        group = "应用操作",
        component = "InstallApkDialog",
        state = "路径输入 / 浏览 / 拖拽 + 三个安装选项"
    ) { _, onDismiss, log ->
        InstallApkDialog(
            onDismiss = onDismiss,
            onInstall = { apkPath, options ->
                log(
                    "InstallApkDialog.onInstall(\"$apkPath\", " +
                        "replace=${options.replace}, test=${options.testApp}, downgrade=${options.downgrade})"
                )
                onDismiss()
            }
        )
    },
    DialogEntry(
        id = "apk-helper-empty",
        group = "应用操作",
        component = "ApkHelperDialog",
        state = "未解析（拖入真实 APK 只打印回调）"
    ) { _, onDismiss, log ->
        ApkHelperDialog(
            parseResult = null,
            isParsing = false,
            onDismiss = onDismiss,
            onParse = { path -> log("ApkHelperDialog.onParse(\"$path\") —— 画廊不接真实解析器，请打开「解析成功」状态查看") }
        )
    },
    DialogEntry(
        id = "apk-helper-success",
        group = "应用操作",
        component = "ApkHelperDialog",
        state = "解析成功（假数据）"
    ) { _, onDismiss, log ->
        ApkHelperDialog(
            parseResult = ApkParseResult.Success(demoApkInfo),
            isParsing = false,
            onDismiss = onDismiss,
            onParse = { path -> log("ApkHelperDialog.onParse(\"$path\") —— 画廊不接真实解析器") }
        )
    },
    DialogEntry(
        id = "apk-helper-failure",
        group = "应用操作",
        component = "ApkHelperDialog",
        state = "解析失败（红字错误提示）"
    ) { _, onDismiss, log ->
        ApkHelperDialog(
            parseResult = ApkParseResult.Failure("ERROR: AssetManager.java 语义错误，apk 包体损坏或非标准 zip 格式（示例错误）"),
            isParsing = false,
            onDismiss = onDismiss,
            onParse = { path -> log("ApkHelperDialog.onParse(\"$path\") —— 画廊不接真实解析器") }
        )
    },
    DialogEntry(
        id = "apk-helper-parsing",
        group = "应用操作",
        component = "ApkHelperDialog",
        state = "解析中（底部「解析中....」）"
    ) { _, onDismiss, log ->
        ApkHelperDialog(
            parseResult = null,
            isParsing = true,
            onDismiss = onDismiss,
            onParse = { path -> log("ApkHelperDialog.onParse(\"$path\") —— 画廊不接真实解析器") }
        )
    },
    DialogEntry(
        id = "screen-record",
        group = "其他",
        component = "ScreenRecordDialog",
        state = "可交互：开始后实时倒计时，归零自动结束"
    ) { _, onDismiss, log ->
        var recording by remember { mutableStateOf(false) }
        var remaining by remember { mutableStateOf(0) }
        LaunchedEffect(recording) {
            if (!recording) return@LaunchedEffect
            while (remaining > 0) {
                delay(1000)
                remaining--
            }
            recording = false
            log("ScreenRecordDialog：录制完成（模拟）")
        }
        ScreenRecordDialog(
            isRecording = recording,
            remainingSeconds = remaining,
            onDismiss = onDismiss,
            onRecordStart = { options ->
                log(
                    "ScreenRecordDialog.onRecordStart(timeLimit=${options.timeLimit}s, " +
                        "res=${options.customResolution.ifBlank { "默认" }}, " +
                        "bitRate=${options.bitRate ?: "默认"}, rotate=${options.rotate})"
                )
                remaining = options.timeLimit
                recording = true
            },
            onRecordStop = {
                log("ScreenRecordDialog.onRecordStop()")
                recording = false
            },
            onPull = { log("ScreenRecordDialog.onPull()") }
        )
    },
    DialogEntry(
        id = "about",
        group = "其他",
        component = "AboutDialog",
        state = "版本信息 + GitHub 链接（点击真开浏览器）"
    ) { _, onDismiss, _ ->
        AboutDialog(onDismiss = onDismiss)
    }
)
