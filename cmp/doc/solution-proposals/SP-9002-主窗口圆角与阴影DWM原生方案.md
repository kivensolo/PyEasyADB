# SP-9002 主窗口圆角与阴影（Win11 DWM 原生圆角）

| 属性 | 内容 |
|------|------|
| 编号 | SP-9002（领域 90 其他/通用，序号 02） |
| 状态 | 已实施（用户实机验收 2026-10-16：「DWM 圆角版本，效果就是我想要的」） |
| 日期 | 2026-10-16 |
| 涉及代码 | `platform/src/desktopMain/kotlin/com/easyadb/platform/Win32Interop.kt`（`setWindowCornerPreference`）<br>`desktop-app/src/main/kotlin/com/easyadb/desktop/Main.kt`（窗口创建后 DWM 偏好设置） |
| 关联方案 | 窗口壳（undecorated 一体式标题栏）见迁移进度 P13 记录；H5 参照稿 `.window` 定义见 `cmp/doc/ui-reference/index.html` |

## 1. 背景与目标

H5 参照稿的窗口壳是悬浮卡片形态（`.window`：`border-radius:16px` + `box-shadow 0 30px 90px` 大范围软阴影）。目标：CMP 端 undecorated 窗口实现同等观感。

## 2. 方案演进（四轮，前三轮否决的原因是本方案的核心价值）

| 轮次 | 方案 | 结果 | 否决/保留原因 |
|---|---|---|---|
| 1 | `Modifier.shadow(48dp)` 大 elevation | ✗ | Compose **桌面端** shadow 是小 blur 高斯（无 Android AGSL 大范围柔化），大 elevation 只会把生硬的**阴影矩形**越推越大 |
| 2 | 6 层递增透明度圆角描边叠加 | ✗ | 叠加结果是**实体环带**：层与层之间离散、外缘有硬边，非连续渐变；且 `padding` 不接受负值（`IllegalArgumentException: Padding must be non-negative`，外扩须直接计算总边距） |
| 3 | 每像素透明窗口（`transparent=true`）+ Skia `BlurMaskFilter` 高斯柔影画在透明边距 | ✓ 技术成立 ✗ 观感 | 模糊本身正确（`MaskFilter.makeBlur(FilterBlurMode.NORMAL, σ)`，σ=24dp 过浓改 12dp；`drawRRect`；`nativeCanvas` 桌面扩展在 `androidx.compose.ui.graphics`），**透明也实测生效**（Win32 GetPixel 采样边距透出桌面内容）——但 32dp 边距**透出桌面杂物**：背后有窗口时整体坍缩成"灰色直角矩形包白卡"，悬浮观感完全依赖桌面背景，不可控 |
| 4（**定稿**） | **Win11 DWM 原生圆角**：`DwmSetWindowAttribute(DWMWA_WINDOW_CORNER_PREFERENCE=33, DWMWCP_ROUND=2)` | ✓ | 窗口即卡片（无边距无透明），系统渲染圆角 + **系统自带阴影**，任何桌面背景下干净，零性能成本。用户验收定稿 |

## 3. 定稿实现

- `Win32Interop.setWindowCornerPreference(hwnd, round)`：`DwmSetWindowAttribute(hwnd, 33, DWMWCP_ROUND=2 / DONOTROUND=1, 4)`；调用失败静默返回 false
- `Main.kt`：窗口组合后 `LaunchedEffect` 内 `componentHwnd(composeWindow)` 取句柄设置一次；**最大化时 DWM 自动隐去圆角**（对齐 `.window.maximized`），无需手工处理

## 4. 已知限制（定稿边界）

- **圆角半径不可自定义**：`DWMWA_WINDOW_CORNER_PREFERENCE` 只接受偏好枚举（DEFAULT/DONOTROUND/ROUND/ROUNDSMALL），实际弧度（约 8px）由系统决定，**无自定义数值 API**——与 H5 的 16px 存在差异，属平台标准对齐的有意取舍（用户裁决：接受）
- **仅 Win11 有效**：Win10 无该属性（调用失败静默直角，无副作用）；Win10 若需圆角只能走第 3 轮透明路线并接受其背景依赖
- 自定义半径的唯一途径是第 3 轮透明自绘路线（任意半径 + 高斯阴影），**未选用**；若未来需求反转，按 §2 第 3 轮实现要点恢复（注意 `nativeCanvas`/`MaskFilter.makeBlur` 均为桌面专属 API）

## 5. 验证

- [x] Win11 实机：窗口四角系统圆角 + 系统阴影，任意桌面背景下干净（用户确认）
- [x] 最大化/还原：圆角随系统行为自动隐去/恢复
- [x] 头栏拖拽/双击最大化/窗口按钮无回归（与 P13 一体式标题栏共用 WindowControls）
