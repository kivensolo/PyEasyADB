package com.easyadb.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 可提取的应用信息。
 *
 * @param packageName 包名
 * @param apkPath 设备上的 APK 路径
 * @param isSystem 是否系统应用（true=系统，false=第三方）
 */
data class PullableApp(
    val packageName: String,
    val apkPath: String,
    val isSystem: Boolean
)

private enum class AppFilter(val label: String) {
    ALL("全部应用"),
    THIRD_PARTY("第三方应用"),
    SYSTEM("系统应用")
}

/**
 * 提取应用 APK 对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::PullApkDialog`：
 * - 标题："提取应用"
 * - 搜索框 + 类型筛选下拉 + 刷新按钮
 * - 应用列表（包名 / APK路径 / 类型）
 * - 提取选中应用 + 关闭按钮
 *
 * @param apps 宿主加载好的应用列表
 * @param isLoading 是否正在加载
 * @param onRefresh 点击刷新按钮时回调
 * @param onPull 点击提取时回调，参数为选中的应用
 */
@Composable
fun PullApkDialog(
    apps: List<PullableApp>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPull: (app: PullableApp) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var selectedIndex by remember { mutableStateOf(-1) }
    var filterExpanded by remember { mutableStateOf(false) }
    val dialogState = rememberDialogState(width = 700.dp, height = 500.dp)

    // 应用搜索 + 类型筛选
    val filteredApps = remember(apps, searchText, filter) {
        apps.filter { app ->
            val textMatch = searchText.isBlank() ||
                app.packageName.contains(searchText, ignoreCase = true)
            val typeMatch = when (filter) {
                AppFilter.ALL -> true
                AppFilter.THIRD_PARTY -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
            }
            textMatch && typeMatch
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "提取应用",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── 搜索栏 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("输入包名进行筛选...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.padding(horizontal = 4.dp))
                    // 类型筛选下拉
                    Box {
                        Surface(
                            modifier = Modifier.clickable { filterExpanded = true },
                            color = EasyAdbColors.PanelHeader,
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(filter.label, fontSize = 12.sp)
                            }
                        }
                        DropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false }
                        ) {
                            AppFilter.entries.forEach { f ->
                                DropdownMenuItem(onClick = {
                                    filter = f
                                    filterExpanded = false
                                    selectedIndex = -1
                                }) {
                                    Text(f.label, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.padding(horizontal = 4.dp))
                    Button(onClick = onRefresh) {
                        Text("刷新")
                    }
                }

                // ── 表头 ──
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(EasyAdbColors.PanelHeader)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("包名", modifier = Modifier.weight(1f), fontSize = 11.sp, color = EasyAdbColors.TextSecondary)
                    Text("APK路径", modifier = Modifier.weight(1.5f), fontSize = 11.sp, color = EasyAdbColors.TextSecondary)
                    Text("类型", modifier = Modifier.weight(0.4f), fontSize = 11.sp, color = EasyAdbColors.TextSecondary)
                }

                // ── 应用列表 ──
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("正在加载应用列表...", color = EasyAdbColors.TextSecondary)
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (apps.isEmpty()) "暂无数据，请点击刷新" else "无匹配结果",
                                color = EasyAdbColors.TextSecondary
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredApps.withIndex().toList()) { (index, app) ->
                                    val isSelected = selectedIndex == index
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .background(
                                                if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.18f)
                                                else Color.Transparent
                                            )
                                            .clickable { selectedIndex = index }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            app.packageName,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = EasyAdbColors.TextPrimary
                                        )
                                        Text(
                                            app.apkPath,
                                            modifier = Modifier.weight(1.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = EasyAdbColors.TextPrimary
                                        )
                                        Text(
                                            if (app.isSystem) "系统" else "第三方",
                                            modifier = Modifier.weight(0.4f),
                                            fontSize = 11.sp,
                                            color = EasyAdbColors.TextPrimary
                                        )
                                    }
                                }
                            }
                            // 滚动条：与 LazyColumn 共享 listState，用于跟踪滚动位置
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(listState),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(5.dp)
                            )
                        }
                    }
                }

                // ── 底部：左侧"共x条"，右侧按钮 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "共${filteredApps.size}条",
                        fontSize = 12.sp,
                        color = EasyAdbColors.TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = selectedIndex >= 0,
                            onClick = {
                                val app = filteredApps.getOrNull(selectedIndex)
                                if (app != null) onPull(app)
                            }
                        ) {
                            Text("提取选中应用")
                        }
                        Box(modifier = Modifier.padding(horizontal = 4.dp))
                        TextButton(onClick = onDismiss) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}
