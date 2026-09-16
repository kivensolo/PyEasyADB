package com.easyadb.ui.mirror

import com.easyadb.platform.Win32Interop
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.Frame
import javax.swing.JPanel
import kotlin.concurrent.thread

/**
 * P9 键盘透传 Spike（开发工具，非产品代码）。
 *
 * 定位实验：scrcpy 对「投递的键盘消息」只在窗口拥有键盘焦点时处理（SDL 焦点门槛）。
 * 已知：独立窗口启动时（自带前台焦点）PostMessage 生效；任何 focusWindow/
 * AttachThreadInput 操作之后（含嵌入流程）全部失效。本 Spike 验证：
 *  1. 独立窗口零焦点操作 → Post 生效（基准）
 *  2. 嵌入（reparent，完全不碰焦点）→ Post 是否仍生效 ← 关键分叉
 *  3. EDT 上 attach+SetFocus（产品链路）→ Post 是否生效/被破坏
 *
 * 断言：scrcpy 收到键会注入设备，`dumpsys input` RecentQueue 出现 5s 内的
 * keyCode=92（PAGE_UP）条目；正对照为 `adb shell input keyevent 92`。
 * 运行：`gradlew :ui:mirror:run`（当前 mainClass=KeySpike）。
 */
fun main() {
    val out: (String) -> Unit = { println(it) }
    val results = mutableListOf<Pair<String, Boolean>>()

    val serial = firstDeviceSerial()
    if (serial == null) {
        out("STEP0 adb devices: FAIL (无设备)")
        out("SPIKE RESULT: FAIL")
        return
    }
    out("STEP0 adb devices: PASS (serial=$serial)")

    fun freshCount(entry: String): Int {
        val dump = adb(serial, "shell", "dumpsys", "input") ?: return -1
        return dump.lineSequence().count { line ->
            line.contains(entry) &&
                Regex("age=([\\d.]+)ms").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                    ?.let { it < 5000.0 } == true
        }
    }

    fun freshAppears(entry: String, timeoutMs: Long = 3000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            if (freshCount(entry) > 0) return true
        }
        return false
    }

    fun keyPath(inject: () -> Unit): Boolean {
        repeat(2) {
            inject()
            if (freshAppears("keyCode=92")) return true
        }
        return false
    }

    // ── STEP1 正对照：注入必须可检出 ──
    val ctrlKey = adb(serial, "shell", "input", "keyevent", "92").let { freshAppears("keyCode=92") }
    results.add("STEP1 正对照" to ctrlKey)
    out("STEP1 keyevent 正对照: ${pass(ctrlKey)}")
    if (!ctrlKey) {
        out("SPIKE RESULT: FAIL（断言方法不可用，终止）")
        return
    }

    // ── STEP2 启动 scrcpy（独立窗口，唯一标题） ──
    val scrcpyExe = locateScrcpy() ?: run {
        out("STEP2 locateScrcpy: FAIL"); out("SPIKE RESULT: FAIL"); return
    }
    val title = "EasyADB KeySpike ${System.currentTimeMillis()}"
    val proc = ProcessBuilder(scrcpyExe.absolutePath, "-s", serial, "--window-title", title)
        .redirectErrorStream(true)
        .start()
    thread(isDaemon = true) { proc.inputStream.readBytes() } // 排空防管道死锁（P7 坑点）

    val hwnd = waitWindowReady(title, findTimeoutMs = 9000, readyTimeoutMs = 6000, out) ?: run {
        proc.destroy()
        out("STEP2 scrcpy 启动/找窗: FAIL"); out("SPIKE RESULT: FAIL"); return
    }
    out("STEP2 scrcpy 启动: PASS (hwnd=0x${java.lang.Long.toHexString(hwnd)})")

    // ── STEP3 独立窗口 Post（不碰焦点，窗口保有启动时的前台焦点） ──
    val s3 = keyPath { postPageUp(hwnd) }
    results.add("STEP3 独立零焦点Post" to s3)
    out("STEP3 独立窗口 Post(零焦点操作): ${pass(s3)}")

    // ── STEP4 嵌入：只 reparent（样式 + SetParent + 贴合），完全不碰焦点 ──
    val hostCanvas = Canvas()
    val hostFrame = Frame("KeySpike Host").apply {
        setBounds(660, 120, 420, 320)
        add(JPanel(BorderLayout()).apply { add(hostCanvas, BorderLayout.CENTER) })
        isVisible = true
    }
    Thread.sleep(400)

    var attached = false
    var origStyle: Int? = null
    try {
        val host = Win32Interop.componentHwnd(hostCanvas)
        origStyle = host?.let { Win32Interop.applyChildStyle(hwnd) }
        val s4pre = host != null && origStyle != null && Win32Interop.setParent(hwnd, host)
        if (s4pre) {
            Win32Interop.windowRect(host!!)?.let { Win32Interop.moveWindow(hwnd, 0, 0, it.width, it.height) }
        }
        results.add("STEP4 嵌入(零焦点)" to s4pre)
        out("STEP4 嵌入收编(零焦点): ${pass(s4pre)}")

        Thread.sleep(600)
        val s4 = s4pre && keyPath { postPageUp(hwnd) }
        results.add("STEP4b 嵌入后Post" to s4)
        out("STEP4b 嵌入后 Post(零焦点): ${pass(s4)}")

        // ── STEP5a PyQt 复刻：EDT 上裸 SetFocus（无 attach）→ 焦点断言 → Post ──
        EventQueue.invokeAndWait { Win32Interop.setFocusWindow(hwnd) }
        Thread.sleep(300)
        val focusA = Win32Interop.focusOwnerOf(hwnd)
        val s5a = keyPath { postPageUp(hwnd) }
        results.add("STEP5a 裸SetFocus+Post" to s5a)
        out("STEP5a 裸 SetFocus+Post: ${pass(s5a)} (焦点=0x${focusA?.toString(16) ?: "null"} 子窗=0x${hwnd.toString(16)})")

        // ── STEP5b 持久 attach + SetFocus（不 detach）→ 焦点断言 → Post ──
        EventQueue.invokeAndWait {
            attached = Win32Interop.attachThreadInputToWindow(hwnd, true)
            Win32Interop.setFocusWindow(hwnd)
        }
        Thread.sleep(300)
        val focusB = Win32Interop.focusOwnerOf(hwnd)
        val s5b = keyPath { postPageUp(hwnd) }
        results.add("STEP5b 持久attach+SetFocus" to s5b)
        out("STEP5b 持久 attach+SetFocus+Post: ${pass(s5b)} (attach=${pass(attached)} 焦点=0x${focusB?.toString(16) ?: "null"})")

        // ── STEP5c attach+SetFocus+Post 同步一击（同一 invokeAndWait 块内连续执行，
        //    不给 AWT 焦点管理在中间抢回焦点的机会） ──
        var s5c = false
        EventQueue.invokeAndWait {
            Win32Interop.setFocusWindow(hwnd)
            postPageUp(hwnd)
            postPageUp(hwnd)
        }
        s5c = freshAppears("keyCode=92", timeoutMs = 3000)
        if (!s5c) {
            EventQueue.invokeAndWait {
                Win32Interop.setFocusWindow(hwnd)
                postPageUp(hwnd)
            }
            s5c = freshAppears("keyCode=92", timeoutMs = 3000)
        }
        results.add("STEP5c 同步SetFocus+Post" to s5c)
        out("STEP5c 同步一击 SetFocus+Post: ${pass(s5c)}")

        // ── STEP6 前台激活后 SetFocus：真实点击宿主标题栏（宿主成为前台，等价
        //    用户点击 App）→ SetFocus → 焦点断言 → Post ──
        val hostFrameHwnd = Win32Interop.componentHwnd(hostFrame)
        var s6 = false
        var focusC: Long? = null
        if (hostFrameHwnd != null) {
            val fr = Win32Interop.windowRect(hostFrameHwnd)
            if (fr != null) {
                Win32Interop.sendMouseClick((fr.left + fr.right) / 2, fr.top + 12)
                Thread.sleep(600)
                EventQueue.invokeAndWait { Win32Interop.setFocusWindow(hwnd) }
                Thread.sleep(150)
                focusC = Win32Interop.focusOwnerOf(hwnd)
                s6 = keyPath { postPageUp(hwnd) }
            }
        }
        results.add("STEP6 前台激活+SetFocus+Post" to s6)
        out("STEP6 前台激活后 SetFocus+Post: ${pass(s6)} (焦点=0x${focusC?.toString(16) ?: "null"} 子窗=0x${hwnd.toString(16)})")

        // ── STEP7 焦点确在子窗口时发真实键击（SendInput）：真实输入走 OS 输入流，
        //    若焦点在子窗则应原生送达（= 用户敲键盘的场景） ──
        var s7 = false
        var focusD: Long? = null
        if (hostFrameHwnd != null) {
            val fr = Win32Interop.windowRect(hostFrameHwnd)
            if (fr != null) {
                Win32Interop.sendMouseClick((fr.left + fr.right) / 2, fr.top + 12)
                Thread.sleep(600)
                EventQueue.invokeAndWait { Win32Interop.setFocusWindow(hwnd) }
                Thread.sleep(150)
                focusD = Win32Interop.focusOwnerOf(hwnd)
                s7 = focusD == hwnd && keyPath { sendInputPageUp() }
            }
        }
        results.add("STEP7 焦点在子窗+SendInput" to s7)
        out("STEP7 真实键击(焦点已断言): ${pass(s7)} (焦点=0x${focusD?.toString(16) ?: "null"})")

        // ── STEP8 产品转发处理器仿真：焦点先在宿主（Compose 态）→ 同步块内
        //    SetFocus(子窗)+SendInput 一击 ──
        var s8 = false
        runCatching {
            EventQueue.invokeAndWait {
                hostFrame.requestFocus() // 模拟焦点回到应用自身
                Thread.sleep(100)
                Win32Interop.setFocusWindow(hwnd)
                sendInputPageUp()
            }
            s8 = freshAppears("keyCode=92", timeoutMs = 3000)
            if (!s8) {
                EventQueue.invokeAndWait {
                    Win32Interop.setFocusWindow(hwnd)
                    sendInputPageUp()
                }
                s8 = freshAppears("keyCode=92", timeoutMs = 3000)
            }
        }
        results.add("STEP8 同步SetFocus+SendInput" to s8)
        out("STEP8 转发处理器仿真: ${pass(s8)}")

        // ── STEP9 无样式篡改嵌入：脱离还原后，仅 SetParent（不动 GWL_STYLE，
        //    对齐 Qt createWindowContainer 的做法）→ 前台+SetFocus → Post ──
        if (origStyle != null && hostFrameHwnd != null) {
            runCatching {
                if (attached) {
                    Win32Interop.attachThreadInputToWindow(hwnd, false)
                    attached = false
                }
                Win32Interop.setParent(hwnd, null)
                Win32Interop.restoreTopLevelStyle(hwnd, origStyle)
            }
            Thread.sleep(400)
            val host = Win32Interop.componentHwnd(hostCanvas)
            if (host != null) {
                Win32Interop.setParent(hwnd, host) // 无 applyChildStyle
                Win32Interop.windowRect(host)?.let { Win32Interop.moveWindow(hwnd, 0, 0, it.width, it.height) }
                Thread.sleep(600)
                val fr = Win32Interop.windowRect(hostFrameHwnd)
                if (fr != null) {
                    Win32Interop.sendMouseClick((fr.left + fr.right) / 2, fr.top + 12)
                    Thread.sleep(600)
                }
                EventQueue.invokeAndWait {
                    attached = Win32Interop.attachThreadInputToWindow(hwnd, true)
                    Win32Interop.setFocusWindow(hwnd)
                }
                Thread.sleep(150)
                val focusE = Win32Interop.focusOwnerOf(hwnd)
                val s9 = keyPath { postPageUp(hwnd) }
                results.add("STEP9 无样式嵌入+Post" to s9)
                out("STEP9 无样式篡改嵌入 Post: ${pass(s9)} (焦点=0x${focusE?.toString(16) ?: "null"})")
            }
        }

        // ── 清理：脱离 + 还原 + 杀进程（destroy→exitCode 1 为正常主动关闭） ──
        runCatching {
            if (attached) Win32Interop.attachThreadInputToWindow(hwnd, false)
            Win32Interop.setParent(hwnd, null)
            origStyle?.let { Win32Interop.restoreTopLevelStyle(hwnd, it) }
        }
    } finally {
        proc.destroy()
        Thread.sleep(300)
        proc.destroyForcibly()
        hostFrame.dispose()
    }

    out(results.joinToString("  ") { "${it.first}=${pass(it.second)}" })
    out("SPIKE RESULT: ${if (results.all { it.second }) "PASS ✅" else "FAIL ❌（见上）"}")
}

private const val VK_PRIOR = 0x21 // PAGE_UP

private fun postPageUp(hwnd: Long) {
    Win32Interop.postKeyDown(hwnd, VK_PRIOR)
    Thread.sleep(40)
    Win32Interop.postKeyUp(hwnd, VK_PRIOR)
}

/** SendInput 合成真实键击（OS 输入流），发给当前焦点窗口 */
private fun sendInputPageUp() {
    Win32Interop.sendKeyInput(VK_PRIOR, up = false)
    Thread.sleep(40)
    Win32Interop.sendKeyInput(VK_PRIOR, up = true)
}

private fun firstDeviceSerial(): String? {
    val list = adb(null, "devices") ?: return null
    return list.lineSequence()
        .drop(1)
        .map { it.trim() }
        .firstOrNull { it.endsWith("\tdevice") }
        ?.substringBefore('\t')
}

private fun adb(serial: String?, vararg args: String): String? = runCatching {
    val cmd = buildList {
        add("adb")
        if (serial != null) {
            add("-s")
            add(serial)
        }
        addAll(args.toList())
    }
    ProcessBuilder(cmd).redirectErrorStream(true).start()
        .let { p -> val text = p.inputStream.bufferedReader().readText(); p.waitFor(); text }
}.getOrNull()

/** 与 ScrcpyLauncher 同序的 scrcpy.exe 搜索（本地已验证的安装路径优先） */
private fun locateScrcpy(): java.io.File? {
    val appData = System.getenv("LOCALAPPDATA") ?: return null
    val candidates = listOf(
        java.io.File(appData, "EasyADB/tool/scrcpy-win64/scrcpy.exe"),
        java.io.File(appData, "EasyADB/tools/scrcpy-win64/scrcpy.exe"),
        java.io.File("tool/scrcpy-win64/scrcpy.exe"),
        java.io.File("../tool/scrcpy-win64/scrcpy.exe"),
        java.io.File("../../tool/scrcpy-win64/scrcpy.exe"),
    )
    return candidates.firstOrNull { it.isFile }
}

/** 找窗 + 就绪判定（尺寸连续两拍 ≥50，防 SDL 初始化中途 reparent 的 0xC000041D） */
private fun waitWindowReady(
    title: String,
    findTimeoutMs: Long,
    readyTimeoutMs: Long,
    out: (String) -> Unit
): Long? {
    var hwnd: Long? = null
    val findDeadline = System.currentTimeMillis() + findTimeoutMs
    while (hwnd == null && System.currentTimeMillis() < findDeadline) {
        hwnd = Win32Interop.findWindowByTitle(title)
        if (hwnd == null) Thread.sleep(200)
    }
    hwnd ?: return null

    var stable = 0
    val readyDeadline = System.currentTimeMillis() + readyTimeoutMs
    while (stable < 2 && System.currentTimeMillis() < readyDeadline) {
        val rect = Win32Interop.windowRect(hwnd)
        if (rect != null && rect.width >= 50 && rect.height >= 50) stable++ else stable = 0
        Thread.sleep(250)
    }
    return if (stable >= 2) hwnd else null
}

private fun pass(ok: Boolean) = if (ok) "PASS" else "FAIL"
