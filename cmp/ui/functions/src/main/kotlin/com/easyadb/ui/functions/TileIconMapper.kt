package com.easyadb.ui.functions

import androidx.compose.ui.graphics.vector.ImageVector
import com.easyadb.core.config.FunctionItem
import com.easyadb.ui.designsystem.EasyAdbTileIcons

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：功能卡图标映射（快捷操作/文件传输 Tab，对齐 HTML 参照稿线稿风格）。
 *              优先按 act 精确匹配，其次按 cmd 片段、最后按文字关键词兜底；
 *              返回 [EasyAdbTileIcons] 中的 SVG 线稿图标，由渲染方 Icon(tint)
 *              染为 accent 语义色。按键模拟 Tab（遥控器）仍走
 *              [iconResourceForItem] 的 PNG 位图映射，不在本函数职责内。
 */
internal fun tileIconForItem(item: FunctionItem): ImageVector = when {
    item.action == "m_show_install_app_dialog" || item.text.contains("安装") -> EasyAdbTileIcons.InstallApp
    item.action == "m_push_file" -> EasyAdbTileIcons.PushFile
    item.action == "m_pull_file" -> EasyAdbTileIcons.PullFile
    item.action == "m_pull_apk" || item.text.contains("提取") -> EasyAdbTileIcons.ExtractApk
    item.action == "m_start_app" || item.text.contains("启动") -> EasyAdbTileIcons.LaunchApp
    item.action == "m_stop_app" || item.cmd.contains("force-stop") || item.text.contains("停止") ->
        EasyAdbTileIcons.StopApp
    item.action == "m_restart_app" || item.text.contains("重启") -> EasyAdbTileIcons.RestartApp
    item.action == "m_send_broadcast" || item.text.contains("广播") -> EasyAdbTileIcons.SendBroadcast
    item.action == "m_query_contentprovider" || item.text.contains("ContentProvider") ->
        EasyAdbTileIcons.QueryProvider
    item.action == "m_input_text" || item.text.contains("文本") -> EasyAdbTileIcons.TextInput
    item.action == "m_screenshot" || item.text.contains("截图") -> EasyAdbTileIcons.Screenshot
    item.action == "m_screen_record" || item.text.contains("录制") -> EasyAdbTileIcons.ScreenRecord
    item.text.contains("清除") || item.cmd.contains("clear") -> EasyAdbTileIcons.ClearData
    item.text.contains("卸载") || item.cmd.contains("uninstall") -> EasyAdbTileIcons.UninstallApp
    else -> EasyAdbTileIcons.UninstallApp
}
