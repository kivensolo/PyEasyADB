package com.easyadb.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * 应用图片资源（PNG）引用。
 *
 * 与 [AppIcons] 区分：本对象承载从 Python res/img 迁移过来的位图资源，
 * 这些资源没有合适的 Material 矢量等价物，必须保留原视觉。
 *
 * 资源目录：`ui/designsystem/src/main/resources/img/`
 */
object AppImages {

    /** 设备已正常连接（state == device）。对应 Python state_connect_normal.png */
    @Composable
    fun deviceConnected(): Painter = painterResource("img/state_connect_normal.png")

    /** 设备离线（state == offline）。对应 Python state_connect_offline.png */
    @Composable
    fun deviceOffline(): Painter = painterResource("img/state_connect_offline.png")

    /** 设备未授权 / 未连接。对应 Python state_disconnect.png */
    @Composable
    fun deviceDisconnected(): Painter = painterResource("img/state_disconnect.png")
}
