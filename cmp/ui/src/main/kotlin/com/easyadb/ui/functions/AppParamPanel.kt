package com.easyadb.ui.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.ui.designsystem.TextInputFocusTracker
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
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
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：常用命令参数卡（快捷操作 Tab 对齐 HTML 参照稿 params-card）。
 *              shapeLg 描边卡片 + 标题「常用命令参数」+ 2×2 输入网格
 *              （label 置于输入框上方：包名 / Activity / Action / 扩展参数），
 *              包名格带已存包名下拉与快捷删除，回车保存包名。
 *              仅负责参数收集与持久化（Preferences），不包含执行按钮——
 *              启动应用 / 发送广播等执行入口为功能卡本身（点击走
 *              onItemClick(item, appParamState) 既有链路）。
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = easyAdbTokens.shapeLg,
        color = easyAdbColors.Surface,
        border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "常用命令参数",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextSecondary
            )

            // ── 网格行 1：包名 | Activity ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParamField("包名", modifier = Modifier.weight(1f)) {
                    Box {
                        CompactTextField(
                            value = selectedPackage,
                            onValueChange = { selectedPackage = it },
                            placeholder = "输入或选择包名，回车添加",
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
                ParamField("Activity", modifier = Modifier.weight(1f)) {
                    CompactTextField(
                        value = activityClassPath,
                        onValueChange = { activityClassPath = it },
                        placeholder = "com.example/.MainActivity",
                        singleLine = true
                    )
                }
            }

            // ── 网格行 2：Action | 扩展参数 ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParamField("Action", modifier = Modifier.weight(1f)) {
                    CompactTextField(
                        value = action,
                        onValueChange = { action = it },
                        placeholder = "android.intent.action.VIEW",
                        singleLine = true
                    )
                }
                ParamField("扩展参数", modifier = Modifier.weight(1f)) {
                    CompactTextField(
                        value = extendParams,
                        onValueChange = { extendParams = it },
                        placeholder = "--es key value",
                        singleLine = true
                    )
                }
            }
        }
    }
}

/** 参数格：label（输入框上方）+ 内容。 */
@Composable
private fun ParamField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/**
 * 紧凑型输入框，使用 BasicTextField + 自定义边框（Field 底 = H5 --field，
 * focus 时描边变主色），内边距比 Material 默认的 OutlinedTextField 小得多。
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
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(34.dp)
            .border(
                width = easyAdbTokens.strokeWidth,
                color = if (isFocused) easyAdbColors.Primary else easyAdbColors.Border,
                shape = easyAdbTokens.shapeSm
            )
            .background(easyAdbColors.Field, easyAdbTokens.shapeSm)
            .padding(horizontal = 8.dp)
            .onFocusChanged { isFocused = it.hasFocus; TextInputFocusTracker.onFocusChanged(it.hasFocus) },
        contentAlignment = Alignment.CenterStart
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
                        fontSize = 12.sp,
                        color = easyAdbColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(easyAdbColors.Primary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth()
                )
                // 占位文字（无值时显示）
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 12.sp,
                        color = easyAdbColors.TextHint,
                        maxLines = 1
                    )
                }
            }
            // 尾部图标
            trailingIcon?.invoke()
        }
    }
}
