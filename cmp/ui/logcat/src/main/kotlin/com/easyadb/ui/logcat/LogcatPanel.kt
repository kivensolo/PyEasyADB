package com.easyadb.ui.logcat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.LogcatEntry
import com.easyadb.core.adb.LogcatStream
import com.easyadb.core.log.LogLevel
import com.easyadb.ui.designsystem.LogColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 实时日志面板 —— 显示设备 logcat 输出，支持过滤和控制。
 *
 * 对应 Python `BottomWindow.py` 中的 logcat 标签 + `LiveLogAdbThread`。
 *
 * ## 设计要点
 * - **数据源**：直接使用 [LogcatStream.logcatFlow] 获取设备实时 logcat 流，
 *   通过 `collectLatest` 收集 [LogcatEntry] 并缓存到本地 `logEntries` 列表。
 * - **过滤**：通过 [LogcatStream.getFilter] 返回的 [LogcatFilter] 控制过滤条件：
 *   - 日志级别过滤（`changeFilterLevelByName`）
 *   - 关键字搜索（`onFilterContentChanged`）
 *   过滤发生在 `logcatFlow` 内部（`LogcatFilter.filter`），已过滤的条目不会发射到 UI。
 * - **着色**：使用 [LogColor.byLevel] 对 [LogcatEntry.level] 着色，字体为 `Monospace`。
 * - **控制**：启动/停止按钮控制 `logcatJob` 的协程生命周期；停止时调用 `logcatStream.stop()`
 *   销毁底层 ADB 进程。
 * - **限制**：最多缓存 3000 条（[maxEntries]），超出后丢弃最旧的条目。
 * - **依赖**：需要 `:core:adb` 模块的 [LogcatStream] 和 [LogcatEntry] 支持。
 */
@Composable
fun LogcatPanel(
    deviceIp: String?,
    logcatStream: LogcatStream,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var logcatJob by remember { mutableStateOf<Job?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf<List<LogcatEntry>>(emptyList()) }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    // 过滤状态
    var selectedLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    var levelDropdownExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val levelOptions = listOf(
        LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR
    )

    val maxEntries = 3000

    // 自动滚动
    LaunchedEffect(logEntries.size) {
        if (autoScroll && logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    // 启动/停止 logcat
    fun startLogcat() {
        if (deviceIp == null) return
        logcatJob = scope.launch {
            isRunning = true
            logcatStream.logcatFlow(deviceIp).collectLatest { entry ->
                if (logEntries.size >= maxEntries) {
                    logEntries = logEntries.drop(1) + entry
                } else {
                    logEntries = logEntries + entry
                }
            }
        }
    }

    fun stopLogcat() {
        logcatJob?.cancel()
        logcatStream.stop()
        logcatJob = null
        isRunning = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── 控制栏 ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp,
            color = Color(0xFFF5F5F5)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 设备信息
                    Text(
                        text = deviceIp ?: "未选中设备",
                        fontSize = 10.sp,
                        color = Color(0xFF757575)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 日志级别下拉
                    Box {
                        Button(
                            onClick = { levelDropdownExpanded = true },
                            modifier = Modifier.height(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFE0E0E0),
                                contentColor = Color(0xFF212121)
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(selectedLevel.shortName, fontSize = 10.sp)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.width(14.dp).height(14.dp))
                        }
                        DropdownMenu(
                            expanded = levelDropdownExpanded,
                            onDismissRequest = { levelDropdownExpanded = false }
                        ) {
                            levelOptions.forEach { level ->
                                DropdownMenuItem(onClick = {
                                    selectedLevel = level
                                    logcatStream.getFilter().changeFilterLevelByName(level.shortName)
                                    levelDropdownExpanded = false
                                }) {
                                    Text(
                                        "${level.name} (${level.shortName})",
                                        fontSize = 12.sp,
                                        color = LogColor.byLevel(level.value)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 搜索框
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            logcatStream.getFilter().onFilterContentChanged(it)
                        },
                        placeholder = { Text("搜索", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 24.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchText = ""
                                        logcatStream.getFilter().onFilterContentChanged("")
                                    },
                                    modifier = Modifier.width(20.dp).height(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, "清除", modifier = Modifier.width(14.dp).height(14.dp))
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 自动滚动按钮
                    Button(
                        onClick = { autoScroll = !autoScroll },
                        modifier = Modifier.height(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (autoScroll) Color(0xFF1565C0) else Color(0xFFE0E0E0),
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(if (autoScroll) "自动" else "手动", fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 清空按钮
                    IconButton(
                        onClick = {
                            logEntries = emptyList()
                            logcatStream.clearFilter()
                        },
                        modifier = Modifier.width(24.dp).height(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, "清空", modifier = Modifier.width(16.dp).height(16.dp))
                    }

                    // 启动/停止按钮
                    if (isRunning) {
                        IconButton(
                            onClick = { stopLogcat() },
                            modifier = Modifier.width(24.dp).height(24.dp)
                        ) {
                            Icon(Icons.Default.Stop, "停止", modifier = Modifier.width(16.dp).height(16.dp), tint = Color(0xFFD32F2F))
                        }
                    } else {
                        IconButton(
                            onClick = { startLogcat() },
                            enabled = deviceIp != null,
                            modifier = Modifier.width(24.dp).height(24.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, "启动", modifier = Modifier.width(16.dp).height(16.dp), tint = Color(0xFF2E7D32))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${logEntries.size} 条",
                        fontSize = 9.sp,
                        color = Color(0xFF9E9E9E)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "上限 $maxEntries 条",
                        fontSize = 9.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
        }

        // ── 日志列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
        ) {
            items(logEntries, key = { "${it.pid}-${it.raw.hashCode()}" }) { entry ->
                Text(
                    text = entry.raw,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LogColor.byLevel(entry.level.value),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // 未选中设备时显示提示
        if (deviceIp == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请先在左侧设备列表中选择一个设备",
                    fontSize = 12.sp,
                    color = Color(0xFFBDBDBD)
                )
            }
        }
    }
}