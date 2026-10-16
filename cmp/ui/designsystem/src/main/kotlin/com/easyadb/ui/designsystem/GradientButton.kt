@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：品牌渐变主按钮（对齐 HTML 参照稿 .run-btn 执行/发送按钮）：
 *              白字 + Primary→PrimaryDark 垂直蓝渐变底、8dp 圆角。
 *              按键模拟 Tab「发送」与常用命令 Tab「执行」等主操作入口共用，
 *              默认高 38dp、左右留白 22dp，可按场景收窄。
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 38.dp,
    horizontalPadding: Dp = 22.dp
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        elevation = 0.dp,
        modifier = modifier.height(height).alpha(if (enabled) 1f else 0.45f)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(easyAdbColors.Primary, easyAdbColors.PrimaryDark))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextOnPrimary,
                modifier = Modifier.padding(horizontal = horizontalPadding)
            )
        }
    }
}
