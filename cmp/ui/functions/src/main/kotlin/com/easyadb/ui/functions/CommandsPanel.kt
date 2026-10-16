@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.functions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.GradientButton
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：常用命令 Tab（对齐 HTML 参照稿 pane-cmds）：顶部工具条卡为
 *              seg 分组按钮（包信息/显示/系统/网络/关于）+ 右侧操作提示；
 *              下方为当前分组的命令行卡片列表，每张卡含「标题/描述（200dp）
 *              + mono 命令条（hover 描边提亮）+ 复制/执行操作区」。
 *              命令模板 {pkg} 占位符自动取快捷操作参数卡的包名（空则
 *              com.example.app）；复制写入系统剪贴板并回调宿主记录日志；
 *              执行回调宿主按当前设备组装 adb 命令、回显输出至内嵌控制台。
 */

/** 常用命令模板：title/desc 为展示文案，cmd 为 adb 命令模板（{pkg} 占位符） */
private data class CmdTemplate(val title: String, val desc: String, val cmd: String)

/** 常用命令分组：seg 分组按钮名 + 组内命令列表 */
private data class CmdCategory(val name: String, val items: List<CmdTemplate>)

/**
 * 常用命令目录（对齐 HTML 参照稿 CMD_CATS，全中文文案）：五组 24 条覆盖
 * 包信息 / 显示 / 系统 / 网络 / 关于，命令与 Python 版 cmdConfig.xml 的
 * 原有指令操作对应（原英文文案全部转中文）。
 */
private val CMD_CATEGORIES = listOf(
    CmdCategory(
        "包信息", listOf(
            CmdTemplate(
                "选中应用版本信息", "使用参数区「包名」查询版本",
                "adb shell dumpsys package {pkg} | grep -E \"versionName|versionCode\""
            ),
            CmdTemplate("所有应用包信息", "列出全部应用及其 APK 路径", "adb shell pm list packages -f"),
            CmdTemplate("第三方应用列表", "仅列出用户安装的第三方应用", "adb shell pm list packages -3"),
            CmdTemplate("系统应用列表", "仅列出系统内置应用", "adb shell pm list packages -s"),
            CmdTemplate("应用安装路径", "使用参数区「包名」查询 APK 路径", "adb shell pm path {pkg}")
        )
    ),
    CmdCategory(
        "显示", listOf(
            CmdTemplate("屏幕分辨率", "查看物理分辨率", "adb shell wm size"),
            CmdTemplate("屏幕密度", "查看 DPI 密度", "adb shell wm density"),
            CmdTemplate(
                "屏幕刷新率", "查询当前刷新率",
                "adb shell dumpsys display | grep -i refreshrate"
            ),
            CmdTemplate(
                "当前前台应用", "查看正在前台运行的 Activity",
                "adb shell dumpsys activity activities | grep mResumedActivity"
            ),
            CmdTemplate("屏幕亮度", "读取当前亮度值", "adb shell settings get system screen_brightness")
        )
    ),
    CmdCategory(
        "系统", listOf(
            CmdTemplate("内存信息", "查看总内存 / 空闲内存", "adb shell cat /proc/meminfo"),
            CmdTemplate("CPU 信息", "查看处理器与核心信息", "adb shell cat /proc/cpuinfo"),
            CmdTemplate("电池信息", "电量、充电状态与健康度", "adb shell dumpsys battery"),
            CmdTemplate("Android 版本", "系统版本号", "adb shell getprop ro.build.version.release"),
            CmdTemplate("内核版本", "Linux 内核信息", "adb shell uname -a")
        )
    ),
    CmdCategory(
        "网络", listOf(
            CmdTemplate("设备 IP 地址", "查询 WLAN 当前 IP", "adb shell ifconfig wlan0"),
            CmdTemplate("网络连通测试", "ping 公网 DNS 检测连通性", "adb shell ping -c 4 223.5.5.5"),
            CmdTemplate("DNS 配置", "查询 DNS 服务器", "adb shell getprop | grep dns"),
            CmdTemplate("TCP 监听端口", "查看设备监听中的端口", "adb shell netstat -tuln"),
            CmdTemplate("开启无线调试", "重启 adbd 至 TCP 模式", "adb tcpip 5555")
        )
    ),
    CmdCategory(
        "关于", listOf(
            CmdTemplate("ADB 版本", "本机 adb 工具版本", "adb version"),
            CmdTemplate("ADB Help", "查看 adb 全部帮助", "adb help"),
            CmdTemplate("设备型号", "当前选中设备的型号", "adb shell getprop ro.product.model"),
            CmdTemplate("安全补丁级别", "系统安全补丁日期", "adb shell getprop ro.build.version.security_patch")
        )
    )
)

/** {pkg} 占位符缺省包名（对齐 H5 fillTpl：参数区包名为空时回退） */
private const val DEFAULT_PKG = "com.example.app"

/** H5 .seg button{transition:.15s}：仅非选中项 hover 提亮时渐变，选中切换瞬切 */
private const val SEG_TRANSITION_MS = 150

/**
 * 常用命令面板。
 *
 * @param packageName 参数区包名（{pkg} 占位符替换来源，空则用缺省包名）
 * @param onRunCommand 执行命令（已填充 {pkg} 的完整命令，宿主定向到当前设备）
 * @param onCopyCommand 命令已复制到剪贴板（宿主记录控制台日志）
 */
@Composable
fun CommandsPanel(
    packageName: String,
    onRunCommand: (String) -> Unit,
    onCopyCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var curCat by remember { mutableStateOf(0) }
    val filledPkg = packageName.trim().ifEmpty { DEFAULT_PKG }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(easyAdbColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 工具条卡：seg 分组 + 操作提示（对齐 H5 apps-toolbar）──
        Surface(
            shape = easyAdbTokens.shapeLg,
            color = easyAdbColors.Surface,
            border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(easyAdbColors.SegTrack)
                        .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CMD_CATEGORIES.forEachIndexed { index, cat ->
                        val active = index == curCat
                        val segInteraction = remember { MutableInteractionSource() }
                        val segHovered by segInteraction.collectIsHoveredAsState()
                        // 选中切换完全瞬切（渐显/渐隐的中间态在小面积控件上感知为
                        // 闪烁）；仅非选中项的 hover 提亮保留 H5 .15s 渐变
                        val segBg = if (active) easyAdbColors.SegActiveBg else Color.Transparent
                        val segText by animateColorAsState(
                            targetValue = when {
                                active -> easyAdbColors.SegActiveText
                                segHovered -> easyAdbColors.TextPrimary // H5 hover 变主文字色
                                else -> easyAdbColors.TextSecondary
                            },
                            animationSpec = if (segHovered && !active) tween(SEG_TRANSITION_MS) else snap(),
                            label = "segText"
                        )
                        Text(
                            text = cat.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = segText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(segBg)
                                .clickable(
                                    interactionSource = segInteraction,
                                    indication = null
                                ) { curCat = index }
                                .hoverable(segInteraction)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "点击「执行」运行命令，输出打印到下方控制台 · {pkg} 自动取参数区包名",
                    fontSize = 12.sp,
                    color = easyAdbColors.TextSecondary
                )
            }
        }

        // ── 命令行卡片列表（对齐 H5 cmd-list / cmd-row）──
        CMD_CATEGORIES[curCat].items.forEach { item ->
            val cmd = item.cmd.replace("{pkg}", filledPkg)
            CmdRow(
                title = item.title,
                desc = item.desc,
                cmd = cmd,
                onCopy = {
                    clipboard.setText(AnnotatedString(cmd))
                    onCopyCommand(cmd)
                },
                onRun = { onRunCommand(cmd) }
            )
        }
    }
}

/**
 * 命令行卡片（对齐 H5 .cmd-row）：标题/描述固定 200dp + mono 命令条
 * （weight 占满、单行省略）+ 复制/执行操作区；hover 时描边提亮为主色。
 */
@Composable
private fun CmdRow(
    title: String,
    desc: String,
    cmd: String,
    onCopy: () -> Unit,
    onRun: () -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    val hovered by rowInteraction.collectIsHoveredAsState()

    Surface(
        shape = easyAdbTokens.shapeLg,
        color = easyAdbColors.Surface,
        border = BorderStroke(
            easyAdbTokens.strokeWidth,
            if (hovered) easyAdbColors.PrimaryLight else easyAdbColors.Divider
        ),
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(rowInteraction)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题 / 描述（对齐 H5 .cmd-info 宽 200px）
            Column(modifier = Modifier.width(200.dp)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = easyAdbColors.TextPrimary
                )
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = easyAdbColors.TextSecondary
                )
            }

            // mono 命令条（对齐 H5 .cmd-line：深底描边、单行省略）
            Text(
                text = cmd,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = easyAdbColors.PrimaryLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(easyAdbColors.PanelHeader)
                    .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )

            // 操作区：复制（icon-sq）+ 执行（run-btn）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val copyInteraction = remember { MutableInteractionSource() }
                val copyHovered by copyInteraction.collectIsHoveredAsState()
                Surface(
                    onClick = onCopy,
                    interactionSource = copyInteraction,
                    shape = RoundedCornerShape(8.dp),
                    color = if (copyHovered) easyAdbColors.Hover else easyAdbColors.PanelHeader,
                    border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Border),
                    elevation = 0.dp,
                    modifier = Modifier.hoverable(copyInteraction)
                ) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = EasyAdbTileIcons.Copy,
                            contentDescription = "复制命令",
                            modifier = Modifier.size(14.dp),
                            tint = easyAdbColors.TextSecondary
                        )
                    }
                }
                GradientButton(text = "执行", onClick = onRun, height = 30.dp, horizontalPadding = 16.dp)
            }
        }
    }
}
