package com.easyadb.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWang
 * date：2026/10/9
 * description：统一空态组件（P12 视觉现代化）——替代散落各面板的「一行灰字」空态。
 *              居中淡色图标 + 主文案 + 可选辅助说明，色彩随明暗主题（TextDisabled /
 *              TextSecondary / TextHint），间距走设计 token。适用于列表为空、未选择
 *              设备、数据加载完成但无内容等场景。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(easyAdbTokens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = easyAdbColors.TextDisabled,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(easyAdbTokens.spaceSm))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = easyAdbColors.TextSecondary
        )
        if (subtitle != null) {
            Spacer(Modifier.height(easyAdbTokens.spaceXs))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = easyAdbColors.TextHint
            )
        }
    }
}
