@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.FunctionItem
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：文件传输大卡（size=large 的功能项渲染，快捷操作 Tab 推送/拉取
 *              文件与文件传输 Tab APK提取共用）。shapeLg 描边 + 内部图左文右
 *              （accent 语义色图标块 + 标题/副文案），内容垂直居中，高度
 *              heightIn(min 130dp) 包裹内容；推送/拉取文件卡底部含设备路径
 *              输入行（devicePath 非空时显示，状态由调用方持有共享）。
 */
@Composable
internal fun FileTransferCard(
    item: FunctionItem,
    devicePath: String?,
    onDevicePathChange: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = accentColor(item.accent, easyAdbColors)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = 130.dp),
        shape = easyAdbTokens.shapeLg,
        color = if (isHovered) easyAdbColors.Hover else easyAdbColors.Surface,
        border = BorderStroke(
            easyAdbTokens.strokeWidth,
            if (isHovered) easyAdbColors.Primary.copy(alpha = 0.5f) else easyAdbColors.Divider
        ),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentIconBox(
                    icon = tileIconForItem(item),
                    contentDescription = item.text,
                    accent = accent
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = easyAdbColors.TextPrimary
                    )
                    if (item.desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.desc,
                            fontSize = 11.sp,
                            color = easyAdbColors.TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
            // 推送/拉取文件：设备路径输入行（devicePath 非空才显示）
            if (devicePath != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "设备路径",
                        fontSize = 11.sp,
                        color = easyAdbColors.TextSecondary,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    CompactFilePathInput(
                        value = devicePath,
                        onValueChange = onDevicePathChange,
                        modifier = Modifier.weight(1f).height(28.dp)
                    )
                }
            }
        }
    }
}

/** 卡内设备路径输入框（BasicTextField 轻量边框样式）。 */
@Composable
private fun CompactFilePathInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = easyAdbTokens.strokeWidth,
                color = easyAdbColors.Border,
                shape = easyAdbTokens.shapeSm
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 11.sp,
                color = easyAdbColors.TextPrimary
            ),
            cursorBrush = SolidColor(easyAdbColors.Primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 根据功能项的名称/命令/行为，返回对应的 PNG 图标资源路径。
 * 对应 Python 中 function_templates.xml 的 icon 属性。
 */
internal fun iconResourceForItem(text: String, cmd: String, action: String): String {
    return when {
        // 通用应用操作
        text.contains("卸载") || cmd.contains("uninstall") -> "img/app_uninstall_256x256.png"
        text.contains("安装") -> "img/app_install_256x256.png"
        text.contains("停止") || cmd.contains("force-stop") -> "img/app_stop_256x256.png"
        text.contains("启动") || action == "m_start_app" -> "img/app_start_256x256.png"
        text.contains("重启") || action == "m_restart_app" -> "img/app_restart_256x256.png"
        text.contains("清除") || cmd.contains("clear") -> "img/app_clear_data_256x256.png"
        text.contains("文本") || action == "m_input_text" -> "img/text_input_256x256.png"
        text.contains("广播") || action == "m_send_broadcast" -> "img/broadcast_256x256.png"
        text.contains("ContentProvider") || action == "m_query_contentprovider" -> "img/query_content_provider_256x256.png"

        // 数据获取
        text.contains("截图") || action == "m_screenshot" -> "img/screen_capture_256x256.png"
        text.contains("录制") || action == "m_screen_record" -> "img/screen_record_256x256.png"
        text.contains("提取") || action == "m_pull_apk" -> "img/app_pull_256x256.png"

        // 按键模拟（遥控器 UI 亦复用此映射）
        text.contains("返回") || cmd.contains("keyevent 4") -> "img/back_256x256.png"
        text.contains("上") && text.contains("键") || cmd.contains("keyevent 19") -> "img/arrow_up.png"
        text.contains("确认") || cmd.contains("keyevent 23") -> "img/ok_256x256.png"
        text.contains("音量加") || cmd.contains("keyevent 24") -> "img/volume_up_256x256.png"
        text.contains("菜单") || cmd.contains("keyevent 82") -> "img/menu_256x256.png"
        text.contains("左") && text.contains("键") || cmd.contains("keyevent 21") -> "img/arrow_left.png"
        text.contains("下") && text.contains("键") || cmd.contains("keyevent 20") -> "img/arrow_down.png"
        text.contains("右") && text.contains("键") || cmd.contains("keyevent 22") -> "img/arrow_right.png"
        text.contains("音量减") || cmd.contains("keyevent 25") -> "img/volume_down_256x256.png"
        text.contains("电源") || cmd.contains("keyevent 26") -> "img/power_256x256.png"

        else -> "img/app_uninstall_256x256.png"
    }
}

/** accent 配置名 → 主题语义色（对齐 HTML .a-icon 语义色系，明暗两套均从调色板 AccentXxx 字段取；未知值返回 null 不染色）。 */
internal fun accentColor(name: String, colors: com.easyadb.ui.designsystem.EasyAdbColors): Color? = when (name) {
    "green" -> colors.AccentGreen
    "red" -> colors.AccentRed
    "blue" -> colors.AccentBlue
    "orange" -> colors.AccentOrange
    "grey", "gray" -> colors.AccentGrey
    "sky" -> colors.AccentSky
    "rose" -> colors.AccentRose
    else -> null
}
