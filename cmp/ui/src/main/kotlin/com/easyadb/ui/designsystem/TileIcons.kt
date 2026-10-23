package com.easyadb.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：功能卡线稿图标集（对齐 HTML 参照稿快捷操作区 .a-icon 内联 SVG 风格）：
 *              24×24 视口、2 单位圆头描边（Lucide/Feather 风格），播放/闪电等
 *              实心形以 fill 呈现。所有路径不携带颜色（占位黑色），由使用方
 *              Icon(tint = ...) 染为 accent 语义色或主题灰，随明暗主题切换。
 *              Vector 为惰性构建（by lazy），首次访问时解析一次 SVG path。
 */
object EasyAdbTileIcons {

    /** 启动应用：实心播放三角（HTML actLaunch） */
    val LaunchApp: ImageVector by lazy {
        iconOf(
            name = "EasyAdbLaunchApp",
            fill = listOf("M8 5.14v13.72c0 .8.87 1.3 1.56.88l10.54-6.86a1.05 1.05 0 0 0 0-1.76L9.56 4.26A1.04 1.04 0 0 0 8 5.14z")
        )
    }

    /** 停止应用：圆角方块（Lucide square，与播放三角成对） */
    val StopApp: ImageVector by lazy {
        iconOf(
            name = "EasyAdbStopApp",
            stroke = listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z")
        )
    }

    /** 重启应用：循环箭头（HTML actReboot 旋转箭头） */
    val RestartApp: ImageVector by lazy {
        iconOf(
            name = "EasyAdbRestartApp",
            stroke = listOf("M21 12a9 9 0 1 1-2.64-6.36", "M21 3v6h-6")
        )
    }

    /** 安装应用：下载托盘（HTML actInstall 安装APK） */
    val InstallApp: ImageVector by lazy {
        iconOf(
            name = "EasyAdbInstallApp",
            stroke = listOf("M12 3v12", "m8 11 4 4 4-4", "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2")
        )
    }

    /** 卸载应用：垃圾桶（HTML actUninstall） */
    val UninstallApp: ImageVector by lazy {
        iconOf(
            name = "EasyAdbUninstallApp",
            stroke = listOf(
                "M3 6h18",
                "M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2",
                "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                "M10 11v6",
                "M14 11v6"
            )
        )
    }

    /** 发送广播：中心点 + 双层电波（HTML actBroadcast） */
    val SendBroadcast: ImageVector by lazy {
        iconOf(
            name = "EasyAdbSendBroadcast",
            stroke = listOf(
                "M16.24 7.76a6 6 0 0 1 0 8.49",
                "M7.76 16.24a6 6 0 0 1 0-8.49",
                "M19.07 4.93a10 10 0 0 1 0 14.14",
                "M4.93 19.07a10 10 0 0 1 0-14.14"
            ),
            fill = listOf("M12 10a2 2 0 1 0 0 4 2 2 0 1 0 0-4z")
        )
    }

    /** 查询ContentProvider：数据库圆柱（Lucide database，对应 provider 存储语义） */
    val QueryProvider: ImageVector by lazy {
        iconOf(
            name = "EasyAdbQueryProvider",
            stroke = listOf(
                "M3 5a9 3 0 1 0 18 0a9 3 0 1 0-18 0z",
                "M3 5v14a9 3 0 0 0 18 0V5",
                "M3 12a9 3 0 0 0 18 0"
            )
        )
    }

    /** 文本输入：大写 T 字型（Lucide type） */
    val TextInput: ImageVector by lazy {
        iconOf(
            name = "EasyAdbTextInput",
            stroke = listOf("M4 7V4h16v3", "M9 20h6", "M12 4v16")
        )
    }

    /** 屏幕截图：相机（HTML actShot） */
    val Screenshot: ImageVector by lazy {
        iconOf(
            name = "EasyAdbScreenshot",
            stroke = listOf(
                "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z",
                "M12 9a4 4 0 1 0 0 8 4 4 0 1 0 0-8z"
            )
        )
    }

    /** 视频录制：摄像机（HTML actRecord） */
    val ScreenRecord: ImageVector by lazy {
        iconOf(
            name = "EasyAdbScreenRecord",
            stroke = listOf(
                "M4.5 6h8A2.5 2.5 0 0 1 15 8.5v7a2.5 2.5 0 0 1-2.5 2.5h-8A2.5 2.5 0 0 1 2 15.5v-7A2.5 2.5 0 0 1 4.5 6z",
                "m15 10.5 7-4v11l-7-4z"
            )
        )
    }

    /** 推送文件：上箭头+顶部横线（Lucide upload 形——导出到设备的方向语义） */
    val PushFile: ImageVector by lazy {
        iconOf(
            name = "EasyAdbPushFile",
            stroke = listOf("M12 21V9", "m8 13 4-4 4 4", "M4 3h16")
        )
    }

    /** 拉取文件：文件夹（HTML actPull） */
    val PullFile: ImageVector by lazy {
        iconOf(
            name = "EasyAdbPullFile",
            stroke = listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
        )
    }

    /** 清除数据：闪电（HTML actClearLog 同形） */
    val ClearData: ImageVector by lazy {
        iconOf(
            name = "EasyAdbClearData",
            fill = listOf("M13 2 3 14h8l-1 8 11-14h-9l1-6z")
        )
    }

    /** APK提取：档案盒（Lucide archive，文件传输 Tab 大卡用） */
    val ExtractApk: ImageVector by lazy {
        iconOf(
            name = "EasyAdbExtractApk",
            stroke = listOf(
                "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
                "M5 8v11a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8",
                "M10 12h4"
            )
        )
    }

    /** 电源：圆弧 + 竖线（HTML rk-pill 电源图标，按键模拟 Tab） */
    val Power: ImageVector by lazy {
        iconOf(
            name = "EasyAdbPower",
            stroke = listOf("M18.36 6.64a9 9 0 1 1-12.73 0", "M12 2v10")
        )
    }

    /** 设置齿轮：齿轮外形 + 中心圆（HTML titlebar btnSettings，顶栏菜单入口） */
    val SettingsGear: ImageVector by lazy {
        iconOf(
            name = "EasyAdbSettingsGear",
            stroke = listOf(
                "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
                "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z"
            )
        )
    }

    /** 太阳：中心圆 + 八向射线（HTML themeToggle，暗色时显示、点击切浅色） */
    val Sun: ImageVector by lazy {
        iconOf(
            name = "EasyAdbSun",
            stroke = listOf(
                "M12 8a4 4 0 1 0 0 8 4 4 0 1 0 0-8z",
                "M12 2v2",
                "M12 20v2",
                "M4.93 4.93l1.41 1.41",
                "M17.66 17.66l1.41 1.41",
                "M2 12h2",
                "M20 12h2",
                "M6.34 17.66l-1.41 1.41",
                "M19.07 6.34l-1.41 1.41"
            )
        )
    }

    /** 月亮：新月形（HTML themeToggle，浅色时显示、点击切暗色） */
    val Moon: ImageVector by lazy {
        iconOf(
            name = "EasyAdbMoon",
            stroke = listOf("M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z")
        )
    }

    /** 复制：双层圆角矩形（HTML cmd-row 复制按钮，常用命令 Tab） */
    val Copy: ImageVector by lazy {
        iconOf(
            name = "EasyAdbCopy",
            stroke = listOf(
                "M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
                "M5 15V5a2 2 0 0 1 2-2h10"
            )
        )
    }

    /** 显示器：屏幕圆角矩形 + 底座线（HTML .cur-dev 图标，Tab 行当前设备 chip） */
    val Monitor: ImageVector by lazy {
        iconOf(
            name = "EasyAdbMonitor",
            stroke = listOf(
                "M4.5 4.5H19.5A2 2 0 0 1 21.5 6.5V15.5A2 2 0 0 1 19.5 17.5H4.5A2 2 0 0 1 2.5 15.5V6.5A2 2 0 0 1 4.5 4.5Z",
                "M8 21h8"
            )
        )
    }

    /** 终端：左尖括号 + 下横线（HTML .logo 内图标，标题栏品牌标识） */
    val Terminal: ImageVector by lazy {
        iconOf(
            name = "EasyAdbTerminal",
            stroke = listOf("M8 6 3 12l5 6", "M13 18h7")
        )
    }

    // ── 文件传输（SP-5001，H5 pane-files 终稿线稿直译） ──

    /** 文件夹（HTML .f-ico.dir，目录行图标） */
    val Folder: ImageVector by lazy {
        iconOf(
            name = "EasyAdbFolder",
            stroke = listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
        )
    }

    /** 文件（HTML .f-ico，文件行图标：折角文档） */
    val FileDoc: ImageVector by lazy {
        iconOf(
            name = "EasyAdbFileDoc",
            stroke = listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6")
        )
    }

    /** 新建文件夹：文件夹 + 中央加号（HTML dxMkdir） */
    val FolderPlus: ImageVector by lazy {
        iconOf(
            name = "EasyAdbFolderPlus",
            stroke = listOf(
                "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
                "M12 10v6",
                "M9 13h6"
            )
        )
    }

    /** 新建文件：折角文档 + 中央加号（与 FolderPlus 同构，新建→文件二级菜单项） */
    val FilePlus: ImageVector by lazy {
        iconOf(
            name = "EasyAdbFilePlus",
            stroke = listOf(
                "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
                "M14 2v6h6",
                "M12 12v6",
                "M9 15h6"
            )
        )
    }

    /** 复制路径：折角文档 + 右上复制角（路径文本复制语义） */
    val CopyPath: ImageVector by lazy {
        iconOf(
            name = "EasyAdbCopyPath",
            stroke = listOf(
                "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
                "M14 2v6h6",
                "M9 14h6",
                "M9 18h6"
            )
        )
    }

    /** 另存文件到电脑：下箭头+底部横线（Lucide download 形——导出到本机的方向语义） */
    val SaveAsFile: ImageVector by lazy {
        iconOf(
            name = "EasyAdbSaveAsFile",
            stroke = listOf("M12 3v12", "m8 11 4 4 4-4", "M4 21h16")
        )
    }

    /** 投递到右侧：两栏边界竖线 + 右向跨栏箭头（HTML pcDeliver 终稿） */
    val DeliverRight: ImageVector by lazy {
        iconOf(
            name = "EasyAdbDeliverRight",
            stroke = listOf("M3 5v14", "M8 12h10", "m14.5 8.5 3.5 3.5-3.5 3.5", "M21 5v14")
        )
    }

    /** 投递到左侧：两栏边界竖线 + 左向跨栏箭头（HTML devDeliver 终稿镜像） */
    val DeliverLeft: ImageVector by lazy {
        iconOf(
            name = "EasyAdbDeliverLeft",
            stroke = listOf("M3 5v14", "M16 12H6", "m9.5 8.5-3.5 3.5 3.5 3.5", "M21 5v14")
        )
    }

    /** 最小化：中置横线（HTML titlebar winMin） */
    val WinMinimize: ImageVector by lazy {
        iconOf(
            name = "EasyAdbWinMinimize",
            stroke = listOf("M5 12h14")
        )
    }

    /** 最大化：圆角方框（HTML titlebar winMax） */
    val WinMaximize: ImageVector by lazy {
        iconOf(
            name = "EasyAdbWinMaximize",
            stroke = listOf("M6.5 4.5h11a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z")
        )
    }

    /** 关闭：交叉双斜线（HTML titlebar winClose） */
    val WinClose: ImageVector by lazy {
        iconOf(
            name = "EasyAdbWinClose",
            stroke = listOf("M6 6l12 12", "M18 6 6 18")
        )
    }

    /** 备注设置：书写笔（Lucide pen-line——编辑备注的语义） */
    val AliasEdit: ImageVector by lazy {
        iconOf(
            name = "EasyAdbAliasEdit",
            stroke = listOf(
                "M12 20h9",
                "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"
            )
        )
    }

    /** 断开连接：分离的双链环 + 四角短线（Lucide unlink——连接解除语义） */
    val Disconnect: ImageVector by lazy {
        iconOf(
            name = "EasyAdbDisconnect",
            stroke = listOf(
                "m18.84 12.25 1.72-1.71h-.02a5.004 5.004 0 0 0-7.09-7.08l-1.71 1.71",
                "m5.17 11.75-1.71 1.71a5.004 5.004 0 0 0 7.09 7.08l1.71-1.71",
                "M8 2v3",
                "M2 8h3",
                "M16 19v3",
                "M19 16h3"
            )
        )
    }

    /** 删除设备：垃圾桶 + 两道竖线（Lucide trash-2——移除记录语义） */
    val RemoveDevice: ImageVector by lazy {
        iconOf(
            name = "EasyAdbRemoveDevice",
            stroke = listOf(
                "M3 6h18",
                "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
                "M10 11v6",
                "M14 11v6"
            )
        )
    }

    /**
     * 按线稿规范构建图标：描边路径 2 单位圆头/圆角、不填充；实心路径仅填充。
     * 颜色占位为黑色，实际显示色由 Icon(tint) 染色决定。
     */
    private fun iconOf(
        name: String,
        stroke: List<String> = emptyList(),
        fill: List<String> = emptyList()
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        stroke.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        fill.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = SolidColor(Color.Black)
            )
        }
        return builder.build()
    }
}
