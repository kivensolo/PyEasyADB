# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

EasyPyADB 是一款基于 Windows 的 ADB（Android Debug Bridge）操作 GUI 应用——涵盖设备管理、包管理、logcat 查看、屏幕镜像（scrcpy）。仓库包含**两套代码**：

1. **`cmp/` — Compose Multiplatform 重写版（v2.0.0，当前活跃开发）**。Kotlin 2.0.21 + Compose Multiplatform 1.7.3，仅桌面端。当前开发工作都在这里（分支 `dev/v2.0.0_cmp`）。
2. **旧版 Python + PyQt5 应用（v1.x，仓库根目录）** — 已稳定，偶尔维护。

Python → CMP 的迁移进度记录在 `doc/迁移进度.md`（P0–P13 已全部完成）、`doc/01_模块映射表.md`（逐文件 Python→Kotlin 映射）和 `doc/02_分阶段迁移计划.md`。移植行为前先查这三份文档：其中记录了迁移决策和已踩过的坑。

## 常用命令

### CMP（Kotlin）— 当前活跃代码库

```bash
cd cmp
./gradlew build          # 构建全部 4 个模块
./gradlew run            # 启动桌面应用
./gradlew :desktop-app:packageMsi   # 打 MSI 安装包（需 WiX；jlink 按需模块）
./gradlew test           # 运行全部测试
./gradlew :ui:run        # 独立运行弹窗画廊 Playground（调试用）
```

- JDK 17 通过 `cmp/gradle.properties` 固定（`org.gradle.java.home=D:\jdks\jdk-17.0.13`）。
- 依赖版本集中在 `cmp/gradle/libs.versions.toml`（version catalog）。
- MSI 配置：`cmp/desktop-app/build.gradle.kts` — 按用户安装，固定 `upgradeUuid`；运行时若报 `NoClassDefFoundError`，到该文件补缺失的 jlink 模块。

### Python 旧版应用

```bash
python -m venv ea_venv && ea_venv\Scripts\activate
pip install -r requriements.txt   # 注意：文件名拼写就是错的（requriements，非 requirements）
python EasyADB.py

pyinstaller EasyADB.spec          # 目录模式（推荐）
```

CA 证书注意事项：`--onefile` 打包会丢失 `certifi/cacert.pem`，导致 requests TLS 报错；需将证书文件随 exe 一并分发。

## 架构

### CMP 重写版（`cmp/`）

4 个 Gradle 模块，依赖方向严格单向：

```
desktop-app → ui → core → platform
```

- `desktop-app/` — `Main.kt` 入口、依赖注入装配、窗口创建（无边框一体式标题栏，Win11 通过 `Win32Interop.setWindowCornerPreference` 实现 DWM 原生圆角）。
- `ui/` — Compose UI，单模块、以包路径区分各功能区（2026-10 由 8 个子模块合并而来）：`ui.designsystem`（主题/色板 token/图标）、`ui.home`（主窗口壳：侧栏 + Tab + 底部区）、`ui.devicelist`、`ui.functions`（动态功能区）、`ui.console`、`ui.logcat`、`ui.mirror`（scrcpy 独立/嵌入模式）、`ui.dialogs`。`gradlew :ui:run` 可独立运行弹窗画廊 Playground。
- `core/` — 纯 Kotlin 业务/数据层，单模块、以包路径区分（**不得依赖 Compose**）：`core.adb`（AdbExecutor、LogcatStream、各类解析器）、`core.apk`、`core.config`、`core.database`（SQLite，与 Python 版共用同一 DB 文件）、`core.device`、`core.log`、`core.util`。
- `platform/` — KMP expect/actual（如 `AppPaths`）；Windows 专属代码（Win32 重父化嵌入 scrcpy、DWM 调用）位于 `desktopMain`。

UI 不得反向调用 desktop-app；业务层不得引入 Compose。

scrcpy **嵌入预览**通过 Win32 `SetParent` 将 scrcpy 原生窗口重父化进 Compose 窗口，键盘透传跟随当前激活 Tab（SP-4001）。若 Tab 切换后出现键盘/焦点问题，先读 `cmp/doc/solution-proposals/SP-4001-*.md` 再动手改。

### Python 旧版应用

- 入口：`EasyADB.py` → `AndroidDependencies.Check()`（自动下载 platform-tools/scrcpy 到 `%LOCALAPPDATA%/EasyADB/`）→ `MainWindow`。
- GUI（`src/`）：`MainWindow`（设备树 + 功能按钮 + 菜单栏）、`CenterWindow`（由 XML 模板构建的功能区）、`BottomWindow`（控制台 + 实时 logcat 双 Tab）、`DevicesWatcher`（连接状态轮询）、`src/logcat/` 日志查看器、`src/widget/` 自定义对话框。
- 业务层（`utils/`）：`ADBTools.py` 通过 `ActionCmdParams` 封装 adb（`{0}` 宏 → 包名；含 `isShellMode`、`needDstPkg`、`target_device_ip`），`CmdExecutor.py`（QThread + 超时定时器），`PackageManager.py`。
- 数据层：`src/DataBase.py`（SQLite）、`src/settings.py`（常量）。
- 配置（`res/config/`）：`cmdConfig.xml`（命令模板）、`function_templates.xml`（动态 UI）、`menus_ui.xml`、`AppConfig.ini`。这些区域的 UI 由 XML 加载，不做硬编码布局。
- 应用数据存于 `%LOCALAPPDATA%/EasyADB/`（`platform-tools/`、`tools/scrcpy-win64/`、`data/easyADB.db`、`tmp/`）。

## 项目约定

### 提交信息

格式：`Title(<type>)：<中文摘要>` — 常见 type：`feat`、`fix`、`refactor`、`doc`。摘要记录决策而非仅改动，但**保持简洁**（一两句话即可，不必罗列全部细节），无需用户每次提醒。提交信息使用中文。

### 实施方案文档（SP 文档）

功能级实现决策需在 `cmp/doc/solution-proposals/` 落一份 SP 文档，命名 `SP-<领域码2位><序号2位>-<中文主题>.md`。领域码：10 设备管理、20 ADB 命令、30 实时日志、40 镜像预览、50 文件传输、60 配置与持久化、90 其他。新增 SP 须在该目录的 `README.md` 索引表登记；被否决的方案标记「已废弃」，不删除文件，序号永不复用。

### H5 参照稿

CMP 端 UI 以 HTML 参照稿为准：`cmp/doc/ui-reference/index.html`。视觉工作（颜色、布局、动效、token）都对照它量取——例如主题 token 对应 CSS 变量、Tab 切换动效曲线对齐 CSS 缓动。做 UI 对齐时参照它，不要凭感觉猜。

### 语言

面向用户的文案、文档、提交信息均为中文。CMP 新功能的 UI 文案同样写中文，与现有 UI 保持一致。

## 已知问题 / 坑点

- **序列号/IP 端口口径**：设备身份比较使用**不带 :port 后缀**的地址——这里的口径不一致曾反复引发高亮/缓存 bug（已修复三次）。
- **scrcpy 下载**需要访问 GitHub（部分环境需 VPN）。
- **多屏弹窗定位**（Python 版）：无论在哪个屏幕点击，对话框总是显示在主屏中心（已知 FIXME）。
- Python 侧 `BASE_PATH = os.getcwd()` — Python 应用假定从项目根目录启动。
