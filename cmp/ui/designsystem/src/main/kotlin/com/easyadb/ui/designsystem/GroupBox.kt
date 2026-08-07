package com.easyadb.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自定义的带边框和标题的分组容器，模拟 Python `QGroupBox`。
 *
 * 标题文字浮在顶部边框线上（legend 效果）：用 surface 背景色遮挡边框线，
 * 对齐 PyQt QGroupBox 的视觉——文字与顶部边框线在同一水平线展示。
 *
 * 用法：
 * ```
 * GroupBox("分辨率", Modifier.weight(1f)) {
 *     // 内容
 * }
 * ```
 *
 * @param title 标题文字（浮在顶部边框线上）
 * @param modifier 外部 modifier（如 weight / fillMaxWidth）
 * @param content 分组内容
 */
@Composable
fun GroupBox(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        // 边框内容区（顶部下移 8dp，让边框线穿过标题中部）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(1.dp, EasyAdbColors.Divider, RoundedCornerShape(4.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
        // 标题：用 surface 背景色遮挡顶部边框线
        Text(
            text = title,
            fontSize = 12.sp,
            color = EasyAdbColors.TextPrimary,
            modifier = Modifier
                .padding(start = 8.dp)
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 4.dp)
        )
    }
}
