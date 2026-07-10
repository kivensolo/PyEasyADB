package com.easyadb.ui.functions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.ui.designsystem.EasyAdbColors
import java.util.prefs.Preferences

/**
 * 应用参数配置状态。
 */
data class AppParamState(
    val selectedPackage: String = "",
    val activityClassPath: String = "",
    val action: String = "",
    val extendParams: String = ""
)

/**
 * 紧凑型输入框，使用 BasicTextField + 自定义边框，
 * 内边距比 Material 默认的 OutlinedTextField 小得多。
 */
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val borderColor = Color(0xFFBDBDBD)
    val focusedBorderColor = Color(0xFF1565C0) // EasyAdbColors.Primary

    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = TextStyle(
                        fontSize = 11.sp,
                        color = EasyAdbColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(focusedBorderColor),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth()
                )
                // 占位文字（无值时显示）
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
            // 尾部图标
            trailingIcon?.invoke()
        }
    }
}

/**
 * 自定义参数行：固定高度 28dp、垂直居中对齐的 Row。
 * 三个单行输入框（包名 / Activity / Action）共用此布局。
 */
@Composable
private fun ParamRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
//      content: @Composable RowScope.() -> Unit //如果需要不同的变体，还可以这样加参数
    )
}

/**
 * 应用参数配置面板。
 */
@Composable
fun AppParamPanel(
    dbPackages: List<String>,
    onPackageAdd: (String) -> Unit,
    onPackageDelete: (String) -> Unit,
    onStateChanged: (AppParamState) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = remember { Preferences.userNodeForPackage(AppParamState::class.java) }

    var activityClassPath by remember { mutableStateOf(prefs.get("activity_classpath", "")) }
    var action by remember { mutableStateOf(prefs.get("app_action", "")) }
    var extendParams by remember { mutableStateOf(prefs.get("extend_params", "")) }
    var selectedPackage by remember { mutableStateOf("") }
    var packageDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(activityClassPath, action, extendParams, selectedPackage) {
        onStateChanged(AppParamState(selectedPackage, activityClassPath, action, extendParams))
        prefs.put("activity_classpath", activityClassPath)
        prefs.put("app_action", action)
        prefs.put("extend_params", extendParams)
        prefs.flush()
    }

    // 标签后的间距，控制标签文本与输入框之间的距离
    val labelEndPadding = 4.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp,
        color = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // ── 标题 ──
            Text(
                text = "应用参数配置",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = EasyAdbColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // ── 包名行 ──
            ParamRow {
                Text(
                    text = "包名:",
                    fontSize = 11.sp,
                    color = EasyAdbColors.TextSecondary,
                    modifier = Modifier.padding(end = labelEndPadding)
                )
                Box(modifier = Modifier.weight(1f)) {
                    CompactTextField(
                        value = selectedPackage,
                        onValueChange = { selectedPackage = it },
                        placeholder = "输入或选择包名，按回车添加",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (selectedPackage.isNotBlank()) {
                                    onPackageAdd(selectedPackage.trim())
                                }
                            }
                        ),
                        trailingIcon = {
                            Row {
                                if (selectedPackage.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            onPackageDelete(selectedPackage)
                                            selectedPackage = ""
                                        },
                                        modifier = Modifier.width(20.dp).height(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "删除", modifier = Modifier.width(14.dp).height(14.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { packageDropdownExpanded = true },
                                    modifier = Modifier.width(20.dp).height(20.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, "展开", modifier = Modifier.width(16.dp).height(16.dp))
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = packageDropdownExpanded,
                        onDismissRequest = { packageDropdownExpanded = false }
                    ) {
                        if (dbPackages.isEmpty()) {
                            DropdownMenuItem(enabled = false, onClick = {}) {
                                Text("暂无已保存的包名", fontSize = 12.sp)
                            }
                        } else {
                            dbPackages.forEach { pkg ->
                                DropdownMenuItem(
                                    onClick = {
                                        selectedPackage = pkg
                                        packageDropdownExpanded = false
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pkg,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                onPackageDelete(pkg)
                                                if (selectedPackage == pkg) selectedPackage = ""
                                            },
                                            modifier = Modifier.width(24.dp).height(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "删除", modifier = Modifier.width(14.dp).height(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Activity 类路径行 ──
            ParamRow {
                Text(
                    text = "Activity:",
                    fontSize = 11.sp,
                    color = EasyAdbColors.TextSecondary,
                    modifier = Modifier.padding(end = labelEndPadding)
                )
                CompactTextField(
                    value = activityClassPath,
                    onValueChange = { activityClassPath = it },
                    placeholder = "目标activity的完整路径，如:com.example.myapp.MainActivity",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Action 行 ──
            ParamRow {
                Text(
                    text = "Action:",
                    fontSize = 11.sp,
                    color = EasyAdbColors.TextSecondary,
                    modifier = Modifier.padding(end = labelEndPadding)
                )
                CompactTextField(
                    value = action,
                    onValueChange = { action = it },
                    placeholder = "用于启动activity或广播发送",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── 扩展参数行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Extend:",
                    fontSize = 11.sp,
                    color = EasyAdbColors.TextSecondary,
                    modifier = Modifier.padding(end = labelEndPadding, top = 5.dp)
                )
                CompactTextField(
                    value = extendParams,
                    onValueChange = { extendParams = it },
                    placeholder = "扩展参数，如:--es \"key1\" \"value1\"",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}