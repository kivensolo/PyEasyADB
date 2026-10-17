package com.easyadb.ui.functions

import com.easyadb.ui.designsystem.EasyAdbColors
import androidx.compose.ui.graphics.Color

/**
 * 功能卡语义色解析（原 FileTransferCard.kt 内私有实现，SP-5001 清理旧文件时迁出）。
 * XML accent 属性（green/red/blue/orange/grey|gray/sky/rose）→ 调色板 Accent 族；
 * 未匹配返回 null（调用方回退 AccentGrey 素灰，对齐 HTML a-icon.gray）。
 */
internal fun accentColor(name: String?, colors: EasyAdbColors): Color? = when (name?.lowercase()) {
    "green" -> colors.AccentGreen
    "red" -> colors.AccentRed
    "blue" -> colors.AccentBlue
    "orange" -> colors.AccentOrange
    "grey", "gray" -> colors.AccentGrey
    "sky" -> colors.AccentSky
    "rose" -> colors.AccentRose
    else -> null
}
