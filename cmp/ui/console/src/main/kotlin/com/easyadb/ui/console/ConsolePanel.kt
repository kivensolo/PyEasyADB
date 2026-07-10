package com.easyadb.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.ui.designsystem.EasyAdbColors
import com.easyadb.ui.designsystem.LogColor
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 控制台日志条目。
 *
 * 通过 [createLogFlow] 创建日志桥接，Main.kt 中调用返回的 appender 函数
 * 即可将日志推送到控制台面板。[timestamp] 为 HH:mm:ss 格式的时间戳，
 * [level] 对应 [LogLevel.value]（0=TRACE, 1=DEBUG, 2=INFO, 3=WARN, 4=ERROR），
 * 用于 [LogColor.byLevel] 着色。
 */
data class LogEntry(
    val timestamp: String,
    val message: String,
    val level: Int = 2
)

/**
 * 控制台面板 —— 显示应用自身日志，支持着色和清空。
 *
 * 对应 Python `BottomWindow.py` 中的控制台输出区域。
 *
 * ## 设计要点
 * - **布局**：左侧垂直工具栏（自动滚动图标 + 清空图标）+ 右侧日志列表，对齐 Python 风格。
 * - **数据源**：通过 [logFlow] 接收 [LogEntry] 列表，由 `collectAsState` 驱动 UI 重组。
 *   通过 [onClear] 回调清空日志。
 * - **渲染**：使用 `LazyColumn` 高效渲染，仅显示最近 1000 条。
 * - **着色**：日志级别颜色使用 [LogColor.byLevel] 映射，字体为 `Monospace`。
 * - **自动滚动**：默认开启（图标高亮），新日志到达时自动滚动到底部；点击切换为手动滚动。
 */
@Composable
fun ConsolePanel(
    logFlow: MutableStateFlow<List<LogEntry>>,
    modifier: Modifier = Modifier
) {
    val logs by logFlow.collectAsState()
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val logLimit = 1000

    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // ── 左侧垂直工具栏（对齐 Python 风格） ──
        Column(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(EasyAdbColors.PanelHeader),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // 自动滚动图标（Python 中对应滚动到底部按钮）
            IconButton(
                onClick = { autoScroll = !autoScroll },
                modifier = Modifier.width(32.dp).height(32.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = if (autoScroll) "自动滚动" else "手动滚动",
                    tint = if (autoScroll) EasyAdbColors.Primary else Color(0xFF757575),
                    modifier = Modifier.width(18.dp).height(18.dp)
                )
            }
            // 清空图标
            IconButton(
                onClick = { logFlow.value = emptyList() },
                modifier = Modifier.width(32.dp).height(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "清空",
                    tint = Color(0xFF757575),
                    modifier = Modifier.width(18.dp).height(18.dp)
                )
            }
        }

        // ── 右侧日志列表 ──
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // 日志计数栏
            Text(
                text = "共 ${logs.size} 条",
                fontSize = 9.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
            ) {
                itemsIndexed(logs.takeLast(logLimit), key = { index, entry -> "${entry.timestamp}-${entry.message.hashCode()}-$index" }) { _, entry ->
                    Text(
                        text = "[${entry.timestamp}] ${entry.message}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LogColor.byLevel(entry.level),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * 创建日志桥接的辅助函数 —— 在 Main.kt 中使用。
 *
 * ## 设计要点
 * 返回 `Pair(flow, appender)`：
 * - `flow`：`MutableStateFlow<List<LogEntry>>`，直接传给 `ConsolePanel.logFlow`，
 *   通过 `collectAsState` 驱动 UI 重组。ConsolePanel 可直接修改此 flow 实现清空。
 * - `appender`：`(message: String, level: Int) -> Unit`，任何地方调用此函数即可
 *   将日志追加到 flow 中。例如在 CustomActionHandler 的 onResult 回调中调用
 *   `logAppender("[P5] 操作完成", 2)` 即可在控制台看到日志。
 *
 * 内部缓存上限 2000 条，超出后保留最近 1500 条，防止内存溢出。
 */
fun createLogFlow(): Pair<MutableStateFlow<List<LogEntry>>, (String, Int) -> Unit> {
    val flow = MutableStateFlow<List<LogEntry>>(emptyList())
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    val appender: (String, Int) -> Unit = { message, level ->
        val timestamp = LocalTime.now().format(timeFormatter)
        val entry = LogEntry(timestamp = timestamp, message = message, level = level)
        flow.value = flow.value + entry
        // 限制条数防止内存溢出
        if (flow.value.size > 2000) {
            flow.value = flow.value.takeLast(1500)
        }
    }

    return Pair(flow, appender)
}