# Scrcpy 嵌入模式与焦点控制方案（P9）

> 阶段：P9 Scrcpy 嵌入模式（2026-09-16 完成，真机验证通过）
> 范围：Windows 桌面端。核心结论：**嵌入 = 跨进程 SetParent + 正确的焦点序列；键盘透传靠「焦点交付 + 真实键盘输入」，不依赖特定 scrcpy 版本**。
> 配套进度记录见 `doc/迁移进度.md` 的 P9 章节；机制验证工具见 `ui/mirror` 的 EmbedSpike / KeySpike。

---

## 1. 目标与总体架构

把 scrcpy 镜像窗口收编进应用右栏「设备实时预览」面板，实现：窗口内嵌显示、鼠标/触摸透传、**键盘透传**（方向键/Enter 直达设备）、停止或异常时还原独立窗口兜底。

```
┌ desktop-app ────────────┐   启动/停止    ┌ core:adb ───────────────┐
│ Main.kt                  │ ──────────────▶│ ScrcpyLauncher           │
│  events 收集 → 应用控制台 │ ◀────────────── │ 进程生命周期 + 4通道诊断   │
└────────────┬─────────────┘   MirrorEvent  └───────────┬─────────────┘
             │ 参数/回调                                 │ scrcpy.exe（独立进程）
             ▼                                          │ --window-title 唯一标题
┌ ui:mirror ──────────────┐        Win32    ┌ platform ─▼─────────────┐
│ MirrorPanel              │ ──────────────▶│ Win32Interop（JNA 5.14） │
│  EmbeddedScrcpyRegion    │   Long 句柄    │ 找窗/reparent/样式/焦点   │
│  SwingPanel+Canvas 宿主   │ ◀────────────── │                          │
└──────────────────────────┘                └──────────────────────────┘
```

三个模块分工：`ScrcpyLauncher` 管进程与诊断（不感知嵌入）；`MirrorPanel` 管嵌入区组合与焦点策略；`Win32Interop` 封装全部 Win32 调用（对外只暴露 Long 句柄，JNA 类型不泄漏到 ui 层）。

## 2. 嵌入流程（EmbeddedScrcpyRegion 时序）

1. **启动**：`scrcpy -s <serial> --window-title "EasyADB Device Mirror"`（唯一标题是后续 FindWindow 的锚点，与 P8/Python 版一致）。
2. **找窗**：200ms×25 次（5s 上限）`FindWindow(null, title)`；找不到保持独立窗口兜底。
3. **就绪门控**：找到窗口后**不能立刻 reparent**——SDL 初始化中途被改父子关系会触发 `0xC000041D`（STATUS_FATAL_USER_CALLBACK_EXCEPTION，致命用户回调异常，进程秒退）。门控条件：`GetWindowRect` 连续 2 拍（间隔 250ms）宽高 ≥50，4s 上限；不达标放弃收编，独立窗口兜底。
4. **收编**（宿主 = SwingPanel 内的重型 `java.awt.Canvas`，见坑点）：
   - `componentHwnd(canvas)` 取宿主 HWND；
   - `applyChildStyle(child)`：`GWL_STYLE` 去掉 `WS_POPUP`/标题栏/可调边框，加 `WS_CHILD`，**返回原样式供还原**；
   - `setParent(child, hostCanvas)`；
   - `moveWindow(child, 0, 0, hostW, hostH)` 贴合宿主客户区。
5. **焦点交付**（键盘透传的核心，见 §3）：常驻 attach + 裸 SetFocus。
6. **尺寸跟随**：面板 `onSizeChanged` → `moveWindow`（Spike 几何断言已证明 **Compose px == Win32 物理像素**，无需 density 换算）。
7. **脱离/清理**（`onDispose` 或停止镜像）：`attachThreadInputToWindow(child, false)` → `setParent(child, null)` → `restoreTopLevelStyle` → `moveWindow(120,120,960,720)` 给个默认位置避免叠在 (0,0)。

配套 UI 约束：**嵌入 checkbox 在预览运行中锁定**（`enabled = !isRunning`）——运行中取消嵌入曾出现显示异常，且 reparent 状态机复杂度不值当；想切独立窗口就关预览再开。

## 3. 焦点控制（键盘透传核心）

### 3.1 Win32 机制要点

- 键盘焦点是**线程输入队列**的属性；对其他进程的窗口 `SetFocus` 要求先 `AttachThreadInput` 把两个线程的队列挂接。
- **`attach → SetFocus → detach` 序列是陷阱**：detach 把挂接的共享队列拆开，焦点状态随之被打散，子窗口实际拿不到焦点。KeySpike 用 `GetGUIThreadInfo(hwndFocus)` 断言过：detach 之后的焦点从不在子窗口上。
- 子窗口不能成为前台窗口（`SetForegroundWindow` 对 WS_CHILD 必失败），所以 `focusWindow` 里「失败回退 SetForegroundWindow」对嵌入场景没有意义。
- 合成消息（`PostMessage(WM_KEYDOWN)`）与真实输入（`SendInput`）在 scrcpy/SDL 侧的处理与版本强相关，详见 §5。

### 3.2 本方案的三条键盘通路

| 通路 | 触发条件 | 实现 | 状态 |
|------|---------|------|------|
| ① 原生直达（主路径） | 焦点在子窗口 | 嵌入完成即交付焦点，用户键盘由 OS 直接路由给 scrcpy | scrcpy ≥3.x 可用 |
| ② Compose 兜底转发 | 焦点被应用内其他控件拿走 | `onPreviewKeyEvent`：先 `setFocusWindow(child)` 归还焦点，再 `postKeyDown/Up`（VK + 扫描码 + 扩展键位 lparam）投递当前键 | 首键后焦点归还，后续恢复通路① |
| ③ 点击画面 | 用户点击视频区 | 系统原生行为（常驻 attach 保证跨进程点击可转移焦点） | 一直可用 |

### 3.3 焦点规则清单（照抄进代码注释的那套）

- ✅ 嵌入期**常驻** `AttachThreadInput`（EDT ↔ scrcpy 线程），`onDispose` 时断开
- ✅ 收编完成后**裸 `SetFocus`**（`setFocusWindow`：不 detach、不回退 SetForegroundWindow）
- ❌ `attach→SetFocus→detach`（焦点被打散）
- ❌ `focusRequester.requestFocus()`（把焦点抢回 Compose，等于亲手废掉键盘）
- ❌ 对子窗口调 `SetForegroundWindow`

## 4. 与 PyQt 版的对比

PyQt 版实现在 `src/widget/win32Wrapper.py`（ScrcpyEmbedWidget），机制同源、细节互补：

| 维度 | PyQt 版 | Compose 版 |
|------|---------|-----------|
| 嵌入宿主 | `QWindow.fromWinId(hwnd)` + `QWidget.createWindowContainer` | `SwingPanel` + 重型 `java.awt.Canvas`，`Native.getComponentPointer` 取 HWND |
| 窗口样式 | **不改**（保留 scrcpy 标题栏） | `WS_CHILD` 化去边框，视觉完全融合；脱离时还原 |
| 找窗 | QTimer 2s 单次 + 3 次重试 | 200ms×25 轮询 + 就绪门控（防 0xC000041D） |
| 焦点获取 | 用户点击嵌入区触发 `mousePressEvent` → 裸 `win32gui.SetFocus`；**无 attach** | 嵌入完成自动交付（常驻 attach + 裸 SetFocus）+ 按键兜底归还 |
| 键盘失效恢复 | 红字提示条「请点击一下此处红色文本」**手工修复** | 自动（通路①②③） |
| 合成转发 | `EventFilter` 用 `SendMessage(WM_KEYDOWN)` 转发——**废弃代码**（注释掉，注释自带「无法转发 Enter 键值」bug 说明） | `onPreviewKeyEvent` + PostMessage 作兜底，主路径靠原生焦点 |
| 运行中切换嵌入 | 不支持（锁定 checkbox） | 同样锁定（实测运行中切换有显示异常，收敛为关预览再开） |

PyQt 版最有价值的情报是 `mousePressEvent` 的裸 `SetFocus`：它证明「GUI 属主线程 + 合适时机」下焦点可以跨进程交付，这正是 Compose 版 `setFocusWindow` 的原型；而它那条被废弃的 EventFilter 转发路径，也印证了**合成转发不是正道，焦点才是**。

## 5. 键盘透传的输入形态边界（重要）

以下均在本机 scrcpy 3.3.4 上实测（KeySpike 设备侧断言 + 用户真实键盘验证）：

- **嵌入态下，合成输入不可达**：`PostMessage(WM_KEYDOWN)` 与 `SendInput`（真实键击合成）在所有焦点配置下都送不进嵌入的 scrcpy——包括「前台激活 + `GetGUIThreadInfo` 断言焦点确已落在子窗口」的组合。独立窗口 + 启动焦点时 PostMessage 可用，但不可依赖。
- **真实键盘在焦点落到子窗口后原生直达**（用户实测，产品最终形态）：嵌入收编 + 常驻 attach + 裸 SetFocus 之后，用户敲键盘直接生效，无需任何消息注入。
- 结论：**键盘透传的正确实现 = 焦点交付（§3），不是消息转发**。`onPreviewKeyEvent` 的 PostMessage 兜底仅在「焦点暂被 Compose 控件持有」时尝试归还焦点，不承担主要通路。
- 排查键盘无效时**先查焦点**（`focusOwnerOf` / KeySpike 断言），与 scrcpy 版本无关。早期文档曾把问题归因于「scrcpy 2.3.1(SDL2) 死路、需 ≥3.x(SDL3)」，后经核实属误判：本机 scrcpy-server（90980 字节，2026-02-27 起）从未更换，全部实验均在 3.3.4 上完成，不存在版本 A/B 依据。

## 6. 验证工具（ui/mirror 保留的 Spike）

- **EmbedSpike**（`gradlew :ui:mirror:run` 默认入口，无需设备）：9 步自动化断言——找窗一致性、Canvas 独立 HWND、SetParent 父子关系、几何对齐（Compose px == 物理像素）、跨线程焦点、可见性、脱离还原。
- **KeySpike**（切 `mainClass` 为 `KeySpikeKt`，需真机 + scrcpy）：设备侧断言键盘是否真送达。原理：scrcpy 收到按键会注入设备，`adb shell dumpsys input` 的 RecentQueue 出现 `keyCode=92`（PAGE_UP）/`MotionEvent` 条目；断言只认 **age<5s 的新鲜条目**（环形队列长度 10，设备侧其他输入会挤压旧条目，基线计数法会被抖掉）；正对照 `adb shell input keyevent 92` / `input tap`。怀疑键盘透传回归时跑它，一分钟出结论。
- 注意：KeySpike 的 SendInput 鼠标合成在 DPI 不感知的 JVM 里坐标换算有偏差（GetSystemMetrics 与物理像素不一致），只影响点击类断言的命中率，不影响键盘断言。

## 7. ScrcpyLauncher 配套加固（P9 期间一并完成）

虽属 P8 文件，但为支撑嵌入联调做了加固，随 P9 提交：

- **状态机竞态修复**：onExit 回调先于 `_state=Running` 注册导致的「秒退进程把状态卡死在 Running」（按钮永久灰、无报错）。修复：`stopRequested` 标志、回调归属校验（`currentProcess === process`）、start 自愈（Running 但进程已死则重置 Idle）、置 Running 后 isAlive 复查。
- **主动关闭误报**：`destroy()` 在 Windows 即 `TerminateProcess(handle, 1)`，exitCode=1 + stderr 空是正常关闭，不再报「异常退出」。
- **4 通道诊断**：MirrorEvent 同时写 `scrcpy-debug.log`（AppData，UTF-8 追加，排障首选）、stdout、应用控制台（经 Main.kt events 收集）、AppLogger。
- **stderr 尾巴**：排水线程保留最后 20 行，异常退出时随事件带出。
- **端口清理**：停止时 `adb forward --list` 清理残留 scrcpy 转发。

## 8. 相关文件

| 文件 | 职责 |
|------|------|
| `cmp/platform/src/desktopMain/kotlin/com/easyadb/platform/Win32Interop.kt` | Win32 封装：找窗/reparent/样式/几何/焦点（`setFocusWindow`、`focusOwnerOf`、`attachThreadInputToWindow`、`postKeyDown/Up`、`sendKeyInput`、`sendMouseClick`） |
| `cmp/ui/mirror/src/main/kotlin/com/easyadb/ui/mirror/MirrorPanel.kt` | 嵌入区 + 焦点策略 + checkbox 锁定 + 兜底转发 |
| `cmp/ui/mirror/src/main/kotlin/com/easyadb/ui/mirror/EmbedSpike.kt` | 几何/链路断言（无设备） |
| `cmp/ui/mirror/src/main/kotlin/com/easyadb/ui/mirror/KeySpike.kt` | 键盘透传设备侧断言 |
| `cmp/core/adb/src/main/kotlin/com/easyadb/core/adb/ScrcpyLauncher.kt` | 进程生命周期 + 诊断 + 端口清理 |
| `cmp/desktop-app/src/main/kotlin/com/easyadb/desktop/Main.kt` | MirrorEvent → 应用控制台桥接 |
