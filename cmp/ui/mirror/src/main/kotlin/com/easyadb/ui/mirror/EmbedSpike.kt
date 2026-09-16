package com.easyadb.ui.mirror

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.easyadb.platform.Win32Interop
import com.easyadb.ui.designsystem.EasyAdbTheme
import com.easyadb.ui.designsystem.easyAdbColors
import kotlinx.coroutines.delay
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Frame
import javax.swing.JPanel

/**
 * P9 嵌入链路 Spike（开发工具，验证 Win32 reparent 机制，非产品代码）。
 *
 * 用一个独立 AWT Frame 模拟 scrcpy 窗口，自动化跑完整嵌入链路并把每步断言
 * 打印到 stdout（同时显示在窗口里）：
 * 1. FindWindowByTitle 与 componentHwnd 一致性
 * 2. SwingPanel 内重型 Canvas 是否有独立 HWND（决定嵌入宿主）
 * 3. SetParent 后 GetAncestor(GA_PARENT) == 宿主
 * 4. moveWindow 后子窗口屏幕矩形与宿主对齐（几何正确性）
 * 5. 跨线程焦点 / 可见性
 * 6. 脱离父窗口 + 还原样式（取消嵌入路径）
 *
 * 运行：`gradlew :ui:mirror:run`，看输出 `SPIKE RESULT: PASS/FAIL`。
 */
fun main() {
    val child = Frame("EASYADB_SPIKE_CHILD").apply {
        setBounds(120, 120, 320, 240)
        isVisible = true
    }

    application {
        val hostPanel = remember { JPanel() }
        val hostCanvas = remember { Canvas() }
        val report = remember { mutableStateListOf("spike waiting...") }

        Window(onCloseRequest = ::exitApplication, title = "Embed Spike Host") {
            EasyAdbTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        "嵌入链路 Spike——结果同时打印到控制台",
                        fontSize = 13.sp, color = easyAdbColors.TextPrimary
                    )
                    report.takeLast(14).forEach {
                        Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = easyAdbColors.TextSecondary)
                    }
                    SwingPanel(
                        background = Color.White,
                        factory = {
                            hostPanel.apply {
                                layout = BorderLayout()
                                add(hostCanvas, BorderLayout.CENTER)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(380.dp).border(1.dp, Color.Red)
                    )
                }

                LaunchedEffect(Unit) {
                    delay(1500)
                    runSpike(hostPanel, hostCanvas, child, report)
                    delay(6000)
                    exitApplication()
                }
            }
        }
    }
}

private fun runSpike(
    hostPanel: JPanel,
    hostCanvas: Canvas,
    child: Frame,
    report: MutableList<String>
) {
    val out: (String) -> Unit = { line -> report.add(line); println(line) }

    val panelHwnd = Win32Interop.componentHwnd(hostPanel)   // 轻量：预期上溯到窗口级 HWND
    val canvasHwnd = Win32Interop.componentHwnd(hostCanvas) // 重型：预期有独立 HWND
    val childHwnd = Win32Interop.componentHwnd(child)
    val childFound = Win32Interop.findWindowByTitle("EASYADB_SPIKE_CHILD")
    out("hwnd: panel=${panelHwnd.hex()} canvas=${canvasHwnd.hex()} child=${childHwnd.hex()} childFound=${childFound.hex()}")
    val step1 = childFound != null && childHwnd != null && childFound == childHwnd
    out("STEP1 FindWindowByTitle==componentHwnd(child): ${pass(step1)}")

    val hasOwnHwnd = canvasHwnd != null && canvasHwnd != panelHwnd
    val host = if (hasOwnHwnd) canvasHwnd else panelHwnd
    out("embed host = ${if (hasOwnHwnd) "Canvas(独立 HWND)" else "Panel(上溯到窗口级)"}")
    if (host == null || childHwnd == null) {
        out("SPIKE RESULT: FAIL (hwnd missing)")
        return
    }

    val originalStyle = Win32Interop.applyChildStyle(childHwnd)
    val step2 = originalStyle != null
    out("STEP2 applyChildStyle: ${pass(step2)} (origStyle=0x${Integer.toHexString(originalStyle ?: 0)})")

    val step3 = Win32Interop.setParent(childHwnd, host)
    out("STEP3 SetParent: ${pass(step3)}")

    val parent = Win32Interop.parentOf(childHwnd)
    val step4 = parent == host
    out("STEP4 GA_PARENT==host: ${pass(step4)} (parent=${parent.hex()})")

    val hostRect = Win32Interop.windowRect(host)
    val hostW = hostRect?.width ?: 400
    val hostH = hostRect?.height ?: 300
    val step5 = Win32Interop.moveWindow(childHwnd, 0, 0, hostW, hostH)
    out("STEP5 moveWindow(${hostW}x${hostH}): ${pass(step5)}")

    val childRect = Win32Interop.windowRect(childHwnd)
    val step6 = childRect != null && hostRect != null &&
        childRect.left == hostRect.left && childRect.top == hostRect.top &&
        childRect.width == hostW
    out("STEP6 geometry对齐: ${pass(step6)} (child=$childRect host=$hostRect)")

    val step7 = Win32Interop.focusWindow(childHwnd)
    out("STEP7 focusWindow: ${pass(step7)}")

    val step8 = Win32Interop.isWindowVisible(childHwnd)
    out("STEP8 isWindowVisible: ${pass(step8)}")

    // 取消嵌入路径：脱离 + 还原样式。脱离后 GA_PARENT 返回桌面窗口（顶层窗口的 parent），
    // 因此断言为“不再是嵌入宿主”而非 null
    val detachOk = Win32Interop.setParent(childHwnd, null)
    Win32Interop.restoreTopLevelStyle(childHwnd, originalStyle ?: 0)
    val topParent = Win32Interop.parentOf(childHwnd)
    val step9 = detachOk && topParent != host
    out("STEP9 detach+restore: ${pass(step9)} (parent=${topParent.hex()}, desktop=非null即顶层)")

    val all = listOf(step1, step2, step3, step4, step5, step6, step7, step8, step9).all { it }
    out("SPIKE RESULT: ${if (all) "PASS ✅" else "FAIL ❌"}")
}

private fun pass(ok: Boolean) = if (ok) "PASS" else "FAIL"

private fun Long?.hex(): String = this?.let { "0x${java.lang.Long.toHexString(it)}" } ?: "null"
