package com.easyadb.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Component

/** dwmapi.dll 最小绑定（JNA 5.14 未内置 Dwmapi）：仅 DwmSetWindowAttribute。 */
private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: WinDef.DWORD, value: Pointer, size: WinDef.DWORD): Int

    companion object {
        val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** 窗口屏幕矩形快照（物理像素），避免 JNA 类型泄漏到调用方。 */
data class Win32Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Win32 窗口互操作封装（P9 Scrcpy 嵌入模式，Windows 专用）。
 *
 * 仅封装嵌入链路所需调用：按标题找窗口、AWT 组件取 HWND、reparent（SetParent）、
 * 子窗口化样式（WS_CHILD 去边框）、窗口几何（SetWindowPos/GetWindowRect）、跨进程焦点。
 * 全部基于 jna-platform [User32] 现成映射，无手写 C 声明。
 *
 * 对外一律用 Long 句柄（HWND 指针值），JNA 类型不泄漏到调用方；
 * 样式位按 Int 运算（GWL_STYLE 是 32 位，WS_POPUP 为符号位负数，位运算语义正确）。
 * 非 Windows 平台所有方法返回 false/null，调用方回退独立窗口模式。
 */
object Win32Interop {

    val isWindows: Boolean =
        System.getProperty("os.name")?.lowercase()?.contains("win") == true

    private fun h(peer: Long): WinDef.HWND = WinDef.HWND(Pointer(peer))

    private fun WinDef.HWND?.peer(): Long? =
        this?.pointer?.let { Pointer.nativeValue(it).toLong() }?.takeIf { it != 0L }

    /** 按精确窗口标题查找顶层窗口（scrcpy 由 P8 启动时指定了唯一标题）。 */
    fun findWindowByTitle(title: String): Long? {
        if (!isWindows) return null
        return User32.INSTANCE.FindWindow(null, title).peer()
    }

    /**
     * 取 AWT 组件的原生 HWND。轻量组件（如 JPanel）会上溯到最近的重型祖先，
     * 因此需要独立 HWND 时传入重型组件（[java.awt.Canvas]）。
     * 组件必须已 displayable（已加入已显示的窗口）。
     */
    fun componentHwnd(component: Component): Long? {
        if (!isWindows) return null
        return runCatching {
            Native.getComponentPointer(component).let { WinDef.HWND(it) }.peer()
        }.getOrNull()
    }

    /** 把 [childHwnd] 挂到 [newParentHwnd] 下；传 null 表示脱离为顶层窗口。 */
    fun setParent(childHwnd: Long, newParentHwnd: Long?): Boolean {
        if (!isWindows) return false
        return User32.INSTANCE.SetParent(h(childHwnd), newParentHwnd?.let(::h)) != null
    }

    /**
     * 子窗口化样式：去掉 WS_POPUP、标题栏/可调边框/系统菜单等装饰位，加上 WS_CHILD。
     * @return 调用前的原样式（供 [restoreTopLevelStyle] 还原）；非 Windows 返回 null
     */
    fun applyChildStyle(hwnd: Long): Int? {
        if (!isWindows) return null
        val style = User32.INSTANCE.GetWindowLong(h(hwnd), WinUser.GWL_STYLE)
        val decorations = (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME
            or WinUser.WS_MINIMIZEBOX or WinUser.WS_MAXIMIZEBOX or WinUser.WS_SYSMENU)
        val newStyle = (style and WinUser.WS_POPUP.inv() and decorations.inv()) or WinUser.WS_CHILD
        User32.INSTANCE.SetWindowLong(h(hwnd), WinUser.GWL_STYLE, newStyle)
        return style
    }

    /**
     * 还原为顶层装饰样式（配合 [setParent] 传 null 脱离父窗口后调用）。
     *
     * [visible]=false 时掩掉 WS_VISIBLE 位：originalStyle 摄于收编前（窗口可见），
     * 直接写回会把刚 SW_HIDE 的窗口重新显示成独立窗口——Tab 切走隐藏托管场景必须掩掉。
     */
    fun restoreTopLevelStyle(hwnd: Long, originalStyle: Int, visible: Boolean = true) {
        if (!isWindows) return
        var decorated = originalStyle or WinUser.WS_CAPTION or WinUser.WS_THICKFRAME or WinUser.WS_SYSMENU
        if (!visible) decorated = decorated and WinUser.WS_VISIBLE.inv()
        User32.INSTANCE.SetWindowLong(h(hwnd), WinUser.GWL_STYLE, decorated and WinUser.WS_CHILD.inv())
    }

    /**
     * 设置子窗口在父窗口客户区内的位置与尺寸（物理像素）；
     * 子窗口坐标系原点即父客户区，x/y 传 0,0 为贴合左上角。
     */
    fun moveWindow(hwnd: Long, x: Int, y: Int, width: Int, height: Int): Boolean {
        if (!isWindows) return false
        return User32.INSTANCE.SetWindowPos(
            h(hwnd), null, x, y, width, height,
            WinUser.SWP_NOZORDER or WinUser.SWP_NOACTIVATE or WinUser.SWP_FRAMECHANGED
        )
    }

    /** 窗口当前屏幕坐标矩形（物理像素），用于几何断言与还原位置。 */
    fun windowRect(hwnd: Long): Win32Rect? {
        if (!isWindows) return null
        val rect = WinDef.RECT()
        return if (User32.INSTANCE.GetWindowRect(h(hwnd), rect)) {
            Win32Rect(rect.left, rect.top, rect.right, rect.bottom)
        } else null
    }

    /** 父窗口句柄（GA_PARENT），reparent 后用于验证父子关系；顶层窗口返回 null。 */
    fun parentOf(hwnd: Long): Long? {
        if (!isWindows) return null
        return User32.INSTANCE.GetAncestor(h(hwnd), WinUser.GA_PARENT).peer()
    }

    /**
     * 跨进程焦点：先 AttachThreadInput 把当前线程输入队列挂到目标窗口线程再 SetFocus
     * （对其他线程/进程的窗口直接 SetFocus 会失败），失败退回 SetForegroundWindow。
     * 对应 Python 版「键盘无法控制时点击红字」的手工 workaround，此处自动化。
     */
    fun focusWindow(hwnd: Long): Boolean {
        if (!isWindows) return false
        val currentThread = Kernel32.INSTANCE.GetCurrentThreadId()
        val targetThreadId = User32.INSTANCE.GetWindowThreadProcessId(h(hwnd), null)
        if (targetThreadId == 0) return false
        val attached = targetThreadId != currentThread &&
            User32.INSTANCE.AttachThreadInput(
                WinDef.DWORD(currentThread.toLong()), WinDef.DWORD(targetThreadId.toLong()), true
            )
        val focused = User32.INSTANCE.SetFocus(h(hwnd)) != null
        if (attached) {
            User32.INSTANCE.AttachThreadInput(
                WinDef.DWORD(currentThread.toLong()), WinDef.DWORD(targetThreadId.toLong()), false
            )
        }
        return focused || User32.INSTANCE.SetForegroundWindow(h(hwnd))
    }

    fun isWindowVisible(hwnd: Long): Boolean =
        isWindows && User32.INSTANCE.IsWindowVisible(h(hwnd))

    /**
     * 裸 SetFocus（无 AttachThreadInput 包装，不回退 SetForegroundWindow）：
     * 对齐 Python 版 win32Wrapper.mousePressEvent 的做法 —— 从宿主窗口属主线程
     * （GUI 线程）直接把键盘焦点交给嵌入的子窗口。注意返回值是"调用前的焦点窗口"，
     * 为 null 不代表失败（之前可能无焦点），成败用 [focusOwnerOf] 验证。
     */
    fun setFocusWindow(hwnd: Long): Boolean {
        if (!isWindows) return false
        return runCatching { User32.INSTANCE.SetFocus(h(hwnd)) }.isSuccess
    }

    /**
     * [hwnd] 所属线程当前的键盘焦点窗口（GetGUIThreadInfo.hwndFocus），
     * 用于嵌入链路的焦点状态断言（焦点是否真的落到了子窗口上）。
     */
    fun focusOwnerOf(hwnd: Long): Long? {
        if (!isWindows) return null
        val tid = User32.INSTANCE.GetWindowThreadProcessId(h(hwnd), null)
        if (tid == 0) return null
        val info = WinUser.GUITHREADINFO()
        info.cbSize = info.size()
        return if (User32.INSTANCE.GetGUIThreadInfo(tid, info)) info.hwndFocus.peer() else null
    }

    // ─────────────────────────────────────────────────────────
    // 键盘透传（P9 增强：嵌入模式下键盘事件转发给 scrcpy）
    // ─────────────────────────────────────────────────────────

    /**
     * 把当前线程输入队列与 [hwnd] 所属线程挂接/断开。嵌入期间保持挂接，
     * 点击子窗口时焦点才能跨线程转移（未挂接时跨进程子窗口点不进焦点）。
     */
    fun attachThreadInputToWindow(hwnd: Long, attach: Boolean): Boolean {
        if (!isWindows) return false
        val current = Kernel32.INSTANCE.GetCurrentThreadId()
        val target = User32.INSTANCE.GetWindowThreadProcessId(h(hwnd), null)
        if (target == 0) return false
        if (target == current) return true
        return User32.INSTANCE.AttachThreadInput(
            WinDef.DWORD(current.toLong()), WinDef.DWORD(target.toLong()), attach
        )
    }

    /** VK → 扫描码（MAPVK_VK_TO_VSC_EX=4：扩展键返回 0xE0 前缀形式，如 VK_LEFT→0xE04B） */
    private fun scanCodeEx(vk: Int): Int =
        User32.INSTANCE.MapVirtualKeyEx(vk, MAPVK_VK_TO_VSC_EX, null) // HKL null = 当前键盘布局

    /** PostMessage 键盘事件：lparam 按真实键盘布局拼装（重复数/扫描码/扩展位/释放位） */
    private fun postKey(hwnd: Long, vk: Int, up: Boolean): Boolean {
        val scanEx = scanCodeEx(vk)
        val extended = (scanEx ushr 8) == 0xE0
        val scan = scanEx and 0xFF
        var lparam = 1L or (scan.toLong() shl 16)
        if (extended) lparam = lparam or (1L shl 24) // KF_EXTENDED：方向键/Insert 等
        if (up) lparam = lparam or (0xC0000000L)     // KF_UP + previous state
        val msg = if (up) WinUser.WM_KEYUP else WinUser.WM_KEYDOWN
        // jna 的 PostMessage 映射为 void（异步投递，无返回值），能调用即视为已投递
        User32.INSTANCE.PostMessage(
            h(hwnd), msg, WinDef.WPARAM(vk.toLong()), WinDef.LPARAM(lparam)
        )
        return true
    }

    /** 转发一次按下（VK 码，即 AWT KeyEvent.keyCode） */
    fun postKeyDown(hwnd: Long, vk: Int): Boolean = isWindows && postKey(hwnd, vk, up = false)

    /** 转发一次释放 */
    fun postKeyUp(hwnd: Long, vk: Int): Boolean = isWindows && postKey(hwnd, vk, up = true)

    /**
     * SendInput 合成一次真实键击：进入 OS 输入队列、发给当前焦点窗口，与物理键盘
     * 不可区分。KeySpike 实测嵌入态下合成输入（PostMessage/SendInput）均送不进
     * scrcpy——键盘透传靠 [setFocusWindow] 交付焦点 + 真实键盘，本函数仅供 Spike
     * 实验用。扩展键（方向键/PageUp 等）自动带 KEYEVENTF_EXTENDEDKEY。
     * @return 是否成功注入 1 个事件
     */
    fun sendKeyInput(vk: Int, up: Boolean): Boolean {
        if (!isWindows) return false
        val scanEx = scanCodeEx(vk)
        val extended = (scanEx ushr 8) == 0xE0
        val input = WinUser.INPUT()
        input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        input.input.setType("ki")
        val ki = input.input.ki
        ki.wVk = WinDef.WORD(vk.toLong())
        ki.wScan = WinDef.WORD((scanEx and 0xFF).toLong())
        var flags = 0
        if (extended) flags = flags or WinUser.KEYBDINPUT.KEYEVENTF_EXTENDEDKEY
        if (up) flags = flags or WinUser.KEYBDINPUT.KEYEVENTF_KEYUP
        ki.dwFlags = WinDef.DWORD(flags.toLong())
        return User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(input), input.size()).toInt() == 1
    }

    // MOUSEEVENTF_*（Win32 稳定值，JNA 5.14 未提供常量）
    private const val MOUSEEVENTF_MOVE = 0x0001
    private const val MOUSEEVENTF_LEFTDOWN = 0x0002
    private const val MOUSEEVENTF_LEFTUP = 0x0004
    private const val MOUSEEVENTF_ABSOLUTE = 0x8000

    /**
     * SendInput 合成一次真实左键点击（主屏 0..65535 归一化坐标）。真实点击会走
     * 完整的激活/焦点转移链（跨进程子窗口由此获得键盘焦点），与物理鼠标不可区分。
     */
    fun sendMouseClick(screenX: Int, screenY: Int): Boolean {
        if (!isWindows) return false
        val sx = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN)
        val sy = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSCREEN)
        if (sx <= 0 || sy <= 0) return false
        val nx = (screenX * 65535 / (sx - 1)).coerceIn(0, 65535)
        val ny = (screenY * 65535 / (sy - 1)).coerceIn(0, 65535)
        fun mouse(flags: Int): WinUser.INPUT = WinUser.INPUT().apply {
            type = WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE.toLong())
            input.setType("mi")
            input.mi.dx = WinDef.LONG(nx.toLong())
            input.mi.dy = WinDef.LONG(ny.toLong())
            input.mi.dwFlags = WinDef.DWORD(
                (MOUSEEVENTF_MOVE or MOUSEEVENTF_ABSOLUTE or flags).toLong()
            )
        }
        val down = mouse(MOUSEEVENTF_LEFTDOWN)
        val up = mouse(MOUSEEVENTF_LEFTUP)
        val okDown = User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(down), down.size()).toInt() == 1
        Thread.sleep(60)
        val okUp = User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(up), up.size()).toInt() == 1
        return okDown && okUp
    }

    /**
     * 把窗口原生标题栏染成应用主题色（P12 webUI：与应用头栏融为一体）。
     *
     * 走 DwmSetWindowAttribute：DWMWA_USE_IMMERSIVE_DARK_MODE(20) / DWMWA_CAPTION_COLOR(35) /
     * DWMWA_TEXT_COLOR(36)。Windows 11（build 22000+）支持后两者；Win10 只支持暗亮模式切换，
     * 设置失败静默返回 false，调用方无需任何回退逻辑。
     *
     * @param captionColorRef 标题栏底色，COLORREF 格式（0x00BBGGRR）
     * @param textColorRef    标题文字色，COLORREF 格式
     */
    fun setCaptionColors(hwnd: Long, captionColorRef: Int, textColorRef: Int, darkMode: Boolean): Boolean {
        if (!isWindows) return false
        return runCatching {
            val dwm = DwmApi.INSTANCE
            fun setDword(attr: Int, value: Int): Boolean {
                Memory(4).use { mem ->
                    mem.setInt(0, value)
                    return dwm.DwmSetWindowAttribute(h(hwnd), WinDef.DWORD(attr.toLong()), mem, WinDef.DWORD(4)) == 0
                }
            }
            setDword(DWMWA_USE_IMMERSIVE_DARK_MODE, if (darkMode) 1 else 0)
            setDword(DWMWA_CAPTION_COLOR, captionColorRef)
            setDword(DWMWA_TEXT_COLOR, textColorRef)
        }.getOrDefault(false)
    }

    /** 隐藏窗口（SW_HIDE）：嵌入宿主销毁前收起子窗口，避免脱离后以独立窗口形态闪现/驻留任务栏。 */
    fun hideWindow(hwnd: Long): Boolean {
        if (!isWindows) return false
        return User32.INSTANCE.ShowWindow(h(hwnd), WinUser.SW_HIDE)
    }

    /** 显示窗口（SW_SHOW）：重嵌入流程在 reparent + 贴合之后调用，随后再 SetFocus。 */
    fun showWindow(hwnd: Long): Boolean {
        if (!isWindows) return false
        return User32.INSTANCE.ShowWindow(h(hwnd), WinUser.SW_SHOW)
    }

    /**
     * Win11 窗口圆角偏好（DWMWA_WINDOW_CORNER_PREFERENCE=33）：
     * round=true → DWMWCP_ROUND（系统标准圆角 ≈8px + 系统阴影）；false → DWMWCP_DONOTROUND。
     * Win10 无此属性（调用静默失败返回 false，直角无副作用）。
     */
    fun setWindowCornerPreference(hwnd: Long, round: Boolean): Boolean {
        if (!isWindows) return false
        return runCatching {
            Memory(4).use { mem ->
                mem.setInt(0, if (round) 2 else 1) // DWMWCP_ROUND=2 / DWMWCP_DONOTROUND=1
                DwmApi.INSTANCE.DwmSetWindowAttribute(
                    h(hwnd), WinDef.DWORD(33), mem, WinDef.DWORD(4)
                ) == 0
            }
        }.getOrDefault(false)
    }

    /** ARGB(0xAARRGGBB) → COLORREF(0x00BBGGRR) */
    fun argbToColorRef(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private const val MAPVK_VK_TO_VSC_EX = 4
}
