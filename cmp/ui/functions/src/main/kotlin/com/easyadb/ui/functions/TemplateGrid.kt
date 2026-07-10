package com.easyadb.ui.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.config.FunctionTemplate
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 功能模板按钮网格。
 *
 * 对应 Python `src/CenterWindow.py::_setUpUIDynamic`（line 208-318）：
 * 从 [FunctionTemplate] 列表渲染网格按钮，每个模板一个标题区，
 * 按钮按 `every_row_size=5` 均分网格摆放。
 */
@Composable
fun TemplateGrid(
    templates: List<FunctionTemplate>,
    deviceIp: String?,
    appParams: AppParamState,
    onItemClick: (FunctionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val everyRowSize = 5

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        templates.forEach { template ->
            // 每个模板一个带边框的分组容器（对齐 Python QGroupBox）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // 模板标题（对应 Python 的 QGroupBox title）
                    Text(
                        text = template.name,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = EasyAdbColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // 网格布局（每列等宽均分）
                    val items = template.items
                    val rows = (items.size + everyRowSize - 1) / everyRowSize

                    for (rowIndex in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (colIndex in 0 until everyRowSize) {
                                val itemIndex = rowIndex * everyRowSize + colIndex
                                if (itemIndex < items.size) {
                                    FunctionButton(
                                        item = items[itemIndex],
                                        isDisabled = items[itemIndex].state == "disable",
                                        deviceIp = deviceIp,
                                        onClick = { onItemClick(items[itemIndex]) },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个功能按钮，对齐 Python 中的 QToolButton（56x56 icon + text under icon）。
 * 无边框，边框由父容器（模板分组）提供。
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FunctionButton(
    item: FunctionItem,
    isDisabled: Boolean,
    deviceIp: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val painter: Painter = painterResource(iconResourceForItem(item.text, item.cmd, item.action))

    Surface(
        onClick = onClick,
        enabled = !isDisabled,
        modifier = modifier
            .padding(3.dp)
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = item.text,
                modifier = Modifier.size(44.dp),
                tint = Color.Unspecified
            )
            Text(
                text = item.text,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (!isDisabled) EasyAdbColors.TextPrimary else Color(0xFFBDBDBD),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * 根据功能项的名称/命令/行为，返回对应的 PNG 图标资源路径。
 * 对应 Python 中 function_templates.xml 的 icon 属性。
 */
private fun iconResourceForItem(text: String, cmd: String, action: String): String {
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

        // 按键模拟
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