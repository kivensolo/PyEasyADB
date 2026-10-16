# SP-9001 实时预览嵌入键盘透传与 Tab 激活期窗口级接管

| 属性 | 内容 |
|------|------|
| 编号 | SP-4001（领域 40 镜像预览，序号 01） |
| 状态 | 已实施（用户实机验证 2026-10-16：点击嵌入区外任意位置后键盘仍可控设备、控制台过滤框输入不被劫持、切 Tab 恢复常规键盘） |
| 日期 | 2026-10-16 |
| 涉及代码 | `platform/src/desktopMain/kotlin/com/easyadb/platform/Win32Interop.kt`（焦点/输入族）<br>`ui/mirror/src/main/kotlin/com/easyadb/ui/mirror/MirrorPanel.kt`（EmbeddedScrcpyRegion）<br>`ui/home/src/main/kotlin/com/easyadb/ui/home/MainWindowScreen.kt`（根级接管）<br>`ui/designsystem/src/main/kotlin/com/easyadb/ui/designsystem/TextInputFocusTracker.kt` |
| 关联方案 | 嵌入时序与焦点规则总纲见 `cmp/doc/Scrcpy嵌入模式与焦点控制方案.md`；Tab 切换隐藏托管见迁移进度 P9 节 2026-10-15 修复记录 |

## 1. 背景与问题

scrcpy 嵌入模式（reparent 进主窗口）的键盘控制链路存在一个作用域漏洞：

**主路径**（原生直达）：收编时常驻 `AttachThreadInput` + 裸 `SetFocus(child)`，焦点落在 scrcpy 子窗口后键盘消息由 Windows 原生投递，不经应用代码——零延迟零开销。

**问题场景**：用户点击嵌入窗口**之外**的任意控件（设备卡 / Tab / 侧栏）：

1. 点击落在 Compose 控件上，Compose 焦点转移到该控件（`clickable` 均取焦点）；
2. Win32 层焦点同步从 scrcpy 子窗口移到主窗口 AWT 组件——**原生直达路径断裂**；
3. 原有的兜底 handler（`EmbeddedScrcpyRegion` 的 `onPreviewKeyEvent`）挂在**嵌入区自己的 Box 上**，只在 Compose 焦点位于嵌入区子树内才能看到按键——焦点已离开，**兜底也断裂**。

两路同时失效，键盘"死"到用户点击预览画面内部（原生取回焦点）或切 Tab 往返（嵌入区重建重新 SetFocus）为止。Python 版存在同样限制（红字提示条让用户手工点击恢复）。

## 2. 方案设计

**核心思路**：键盘归属跟随内容 Tab——「实时预览 Tab 可见 + 嵌入运行中」即视为用户当前工作在预览，窗口级按键一律转投 scrcpy；文本输入聚焦时本地优先。

三层结构：

| 层 | 职责 | 位置 |
|---|---|---|
| 主路径 | 原生焦点直达（收编时 SetFocus 交付，焦点在子窗口时零介入） | EmbeddedScrcpyRegion 收编流程 |
| 嵌入区兜底 | 焦点在嵌入区子树内被抢时的按键转投（原有，保留） | EmbeddedScrcpyRegion 的 Box `onPreviewKeyEvent` |
| **窗口级接管（本方案新增）** | 焦点在**整个嵌入区之外**时的按键转投 | MainWindowScreen 根 Column `onPreviewKeyEvent` |

**接管条件**（全部满足才转投，否则事件原样放行）：

```
mirrorState is Running                // scrcpy 进程存活
&& mirrorEmbedEnabled                 // 嵌入模式（独立窗口模式不接管——画面在系统窗口，主窗口键盘无设备语义）
&& Win32Interop.isWindows             // 平台守卫
&& contentTab == TAB_MIRROR           // 实时预览 Tab 可见（键盘归属随 Tab）
&& !TextInputFocusTracker.hasFocusedTextInput   // 无文本框聚焦（打字本地优先）
&& embeddedScrcpyHwnd != 0            // 嵌入区已收编成功（句柄经回调登记）
```

**转投动作**（与嵌入区兜底同款机制）：KeyDown 时 `SetFocus(child)`（归还焦点）+ `PostMessage(WM_KEYDOWN)`，KeyUp 只 Post——首个按键后焦点回到子窗口，后续按键恢复原生直达，接管自动退化为空转。

## 3. 实现链路

### 3.1 句柄登记（宿主只读）

`EmbeddedScrcpyRegion` 收编成功 / onDispose 脱离时经 `onEmbeddedHwndChange(hwnd|0)` 回调；MirrorPanel 透传给 MainWindowScreen 存入 `embeddedScrcpyHwnd` 状态。句柄生命周期归嵌入区管理，宿主只读消费——Tab 切走（嵌入区销毁→脱离）自动清零，接管随之失能。

### 3.2 根级接管（MainWindowScreen）

根 Column 的 `onPreviewKeyEvent` 做"条件判定 + KeyDown/KeyUp 分派"，preview 阶段（父→子 Initial 遍历）先于任何焦点控件处理，保证接管优先级；条件不满足返回 `false` 原样放行，对常规交互零侵入。

### 3.3 文本框守卫（TextInputFocusTracker）

全局聚焦计数器对象（`mutableIntStateOf`）：主窗口内所有文本输入框在 `onFocusChanged` 中上报聚焦/失焦，`hasFocusedTextInput` 汇总。**接入清单（6 处，新增文本框必须同步接入，漏接的后果是该框聚焦时按键被转投设备）**：

| 文本框 | 文件 |
|---|---|
| 参数卡 CompactTextField（activity/action/extend/包名 4 框共用） | `ui/functions/AppParamPanel.kt` |
| 文件传输路径框 | `ui/functions/FileTransferCard.kt` |
| 遥控器自定义键码框 | `ui/functions/RemoteKeyPad.kt` |
| 实时日志过滤框 + Ctrl+F 查找框 | `ui/logcat/LogcatPanel.kt`（2 处） |

弹窗（DialogWindow）为独立 OS 窗口、独立键盘管线，不接入也不受接管影响。

## 4. 验证

- [x] 嵌入运行中，点击设备卡 / Tab / 侧栏 / 头栏任意位置 → 方向键 / Enter 仍控制设备（首个按键即恢复，无感延迟）
- [x] 底部实时日志过滤框 / 查找框聚焦打字 → 按键本地输入，不进设备（守卫生效）
- [x] 切到其他 Tab → 键盘恢复常规行为（`contentTab == TAB_MIRROR` 失败即放行）
- [x] 回归：嵌入区原生直达、Tab 切换隐藏托管（SP 记录于迁移进度 P9 节）、独立窗口模式不受影响

## 5. 已知边界与后续

- **接管与 ⋮ 菜单共存**：MenuBarDropdown 为非 focusable 弹层，键盘事件仍走主窗口管线——接管激活时菜单内的键盘导航（Esc 关闭等）会被转投设备；用鼠标点选菜单项不受影响。若实际使用遇到冲突，可增加「溢出菜单展开中不接管」守卫。
- **按键不透传给 scrcpy 的键**：接管转发的键与嵌入区兜底一致（VK 非 0 全量转发），与原生直达路径的行为对齐。
- 方案重大变更时在文末追加变更记录。
