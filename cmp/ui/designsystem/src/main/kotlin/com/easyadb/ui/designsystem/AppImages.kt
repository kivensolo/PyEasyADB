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

    // ── 设备右键菜单图标 ──

    /** 备注编辑。对应 Python edit.png */
    @Composable
    fun edit(): Painter = painterResource("img/edit.png")

    /** 警告 / 删除设备。对应 Python warning.png */
    @Composable
    fun warning(): Painter = painterResource("img/warning.png")

    // ── 功能模板图标（对应 function_templates.xml 中的 icon 属性） ──

    /** 卸载应用 */
    @Composable
    fun appUninstall(): Painter = painterResource("img/app_uninstall_256x256.png")

    /** 安装应用 */
    @Composable
    fun appInstall(): Painter = painterResource("img/app_install_256x256.png")

    /** 停止应用 */
    @Composable
    fun appStop(): Painter = painterResource("img/app_stop_256x256.png")

    /** 启动应用 */
    @Composable
    fun appStart(): Painter = painterResource("img/app_start_256x256.png")

    /** 重启应用 */
    @Composable
    fun appRestart(): Painter = painterResource("img/app_restart_256x256.png")

    /** 清除数据 */
    @Composable
    fun appClearData(): Painter = painterResource("img/app_clear_data_256x256.png")

    /** 文本输入 */
    @Composable
    fun textInput(): Painter = painterResource("img/text_input_256x256.png")

    /** 发送广播 */
    @Composable
    fun broadcast(): Painter = painterResource("img/broadcast_256x256.png")

    /** 查询 ContentProvider */
    @Composable
    fun queryContentProvider(): Painter = painterResource("img/query_content_provider_256x256.png")

    /** 屏幕截图 */
    @Composable
    fun screenCapture(): Painter = painterResource("img/screen_capture_256x256.png")

    /** 屏幕录制 */
    @Composable
    fun screenRecord(): Painter = painterResource("img/screen_record_256x256.png")

    /** APK 提取 */
    @Composable
    fun appPull(): Painter = painterResource("img/app_pull_256x256.png")

    /** 返回键 */
    @Composable
    fun back(): Painter = painterResource("img/back_256x256.png")

    /** 上键 */
    @Composable
    fun arrowUp(): Painter = painterResource("img/arrow_up.png")

    /** 确认键 */
    @Composable
    fun ok(): Painter = painterResource("img/ok_256x256.png")

    /** 音量加 */
    @Composable
    fun volumeUp(): Painter = painterResource("img/volume_up_256x256.png")

    /** 菜单键 */
    @Composable
    fun menu(): Painter = painterResource("img/menu_256x256.png")

    /** 左键 */
    @Composable
    fun arrowLeft(): Painter = painterResource("img/arrow_left.png")

    /** 下键 */
    @Composable
    fun arrowDown(): Painter = painterResource("img/arrow_down.png")

    /** 右键 */
    @Composable
    fun arrowRight(): Painter = painterResource("img/arrow_right.png")

    /** 音量减 */
    @Composable
    fun volumeDown(): Painter = painterResource("img/volume_down_256x256.png")

    /** 电源键 */
    @Composable
    fun power(): Painter = painterResource("img/power_256x256.png")
}