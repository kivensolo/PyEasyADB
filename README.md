# 简介

EasyADB 是一款便捷进行 ADB 操作的桌面软件，包含了开发者在工作过程中频繁使用的大部分功能，目的是让 ADB 相关操作变得更加简单方便。

当前仓库包含两个版本：

| 版本 | 技术栈 | 状态 | 位置 |
|---|---|---|---|
| **v2.0.0（CMP 重写版）** | Kotlin 2.0.21 + Compose Multiplatform 1.7.3 | 🚧 活跃开发（分支 `dev/v2.0.0_cmp`） | `cmp/` |
| **v1.x（Python 版）** | Python 3.11.1 + PyQt5 | ✅ 稳定，维护为主 | 仓库根目录 |

v2.0.0 是 v1.x 的完整重写：Python → CMP 的迁移已按 P0–P13 全部完成，UI 全面对齐 H5 参照稿，支持明暗主题切换与 Win11 原生圆角。

# v2.0.0（Compose Multiplatform 重写版）

## 环境要求

- JDK 17（路径在 `cmp/gradle.properties` 中通过 `org.gradle.java.home` 固定，可按本机修改）
- Windows 10/11（打包 MSI 需 WiX Toolset）

## 常用命令

在 `cmp/` 目录下执行：

```bash
./gradlew build                      # 构建全部 17 个模块
./gradlew run                        # 启动桌面应用
./gradlew :desktop-app:packageMsi    # 打 MSI 安装包
./gradlew test                       # 运行全部测试
./gradlew :core:adb:test             # 运行单个模块的测试
```

依赖版本统一管理在 `cmp/gradle/libs.versions.toml`。

## 模块结构

```
cmp/
├── desktop-app/     # 桌面入口：Main.kt、依赖注入装配、窗口创建
├── ui/              # Compose UI 层（8 个模块）
│   ├── designsystem #   主题、色板 token、图标
│   ├── home         #   主窗口壳（侧栏 + Tab + 底部区）
│   ├── devicelist   #   设备树 / 命令树
│   ├── functions    #   动态功能区
│   ├── console      #   应用日志控制台
│   ├── logcat       #   实时 logcat 视图
│   ├── mirror       #   scrcpy 镜像（独立窗口 / 嵌入）
│   └── dialogs      #   各类对话框
├── core/            # 纯 Kotlin 业务/数据层（7 个模块，不依赖 Compose）
│   ├── adb          #   ADB 命令执行、scrcpy 启动、各类解析
│   ├── database     #   SQLite（与 Python 版共用同一 DB 文件）
│   ├── config       #   XML / INI 配置加载
│   ├── device / apk / log / util
└── platform/        # KMP expect/actual；Windows 专属实现（scrcpy 嵌入、DWM）
```

依赖方向严格单向：`desktop-app → ui:* → core:* → platform`。UI 不反向调用 desktop-app，业务层不引入 Compose。

## 相关文档

| 文档 | 内容 |
|---|---|
| [`CLAUDE.md`](./CLAUDE.md) | 面向 AI 编码助手的仓库指引（架构、约定、坑点） |
| [`doc/迁移进度.md`](./doc/迁移进度.md) | P0–P13 迁移阶段记录（含踩坑归档） |
| [`doc/01_模块映射表.md`](./doc/01_模块映射表.md) | 逐文件 Python → Kotlin 映射 |
| [`doc/02_分阶段迁移计划.md`](./doc/02_分阶段迁移计划.md) | 分阶段迁移计划 |
| [`cmp/doc/solution-proposals/`](./cmp/doc/solution-proposals/README.md) | 实施方案（SP）文档索引 |
| [`cmp/doc/ui-reference/index.html`](./cmp/doc/ui-reference/index.html) | H5 UI 参照稿（视觉对齐基准） |
| [`cmp/doc/`](./cmp/doc) | CMP 端专题方案总纲 |

## 打包说明

```bash
cd cmp
./gradlew :desktop-app:packageMsi
```

- 产物在 `cmp/desktop-app/build/compose/binaries/main/msi/`。
- MSI 按用户安装（`perUserInstall`），固定 `upgradeUuid`，安装到 `%LOCALAPPDATA%\Programs` 保证日志目录可写。
- jlink 按需模块在 `cmp/desktop-app/build.gradle.kts` 中配置；运行时若报 `NoClassDefFoundError`/`NoSuchMethodError` 即缺模块，回该文件补。
- WiX 首次下载可能超时，参考 `cmp/doc` 中 P10 打包记录的镜像缓存方案。

# v1.x（Python 版）

## 工程说明

整体基于 **Python 3.11.1**，更高版本的兼容性需开发者自测。GUI 框架为 PyQt5。

## 虚拟环境

在工程根目录下运行以下命令来创建虚拟环境：

`python -m venv ea_venv`<br>
`ea_venv` 为环境名，可以自定义。

创建虚拟环境后，进入 `ea_venv/Scripts` 目录，执行 `activate` 可进入虚拟环境。
退出：`deactivate`

## 依赖库安装

所需依赖库信息（PyQt5、lxml、pyinstaller 等）在 `requriements.txt` 文件中包含（文件名历史拼写即如此，非笔误）。
安装命令：`pip install -r requriements.txt`

**注意**：不同版本的 Python 与依赖库之间存在兼容性问题，安装报错时需自行调整对应库的版本号。

## 运行

```bash
python EasyADB.py
```

## 结构说明

```
项目根目录/
├─ res/config/            应用配置文件
│  ├─ cmdConfig.xml       左侧命令行区域配置文件
│  ├─ function_templates.xml  主功能区域配置文件
│  └─ menus_ui.xml        菜单栏 UI 配置文件
├─ pyqtWidgets/           一些 pyqt 基础组件的 demo 文件
├─ res/                   应用资源文件（icons/img/layouts）
├─ src/                   应用源码目录
│  ├─ logcat/             日志模块
│  ├─ widget/             各种控件
│  ├─ settings.py         应用设置模块
│  ├─ DataBase.py         数据库模块
│  └─ ......
├─ utils/                 早期的工具类源码目录(暂时未移至 src)
├─ cmp/                   v2.0.0 Compose Multiplatform 重写版
├─ doc/                   Python → CMP 迁移文档
├─ EasyADB.py             程序 App 启动主入口
└─ requriements.txt       requirements 文件
```

## 应用数据位置

- v1.0.3 起数据库等应用数据存于 `%LOCALAPPDATA%\EasyADB\data\`（从旧版 `.\data\easyADB.db` 升级需手动迁移）。
- `platform-tools/`（首次运行自动下载）、`tools/scrcpy-win64/`（首次使用预览功能时自动下载）也在 `%LOCALAPPDATA%\EasyADB\` 下。

## 主要功能说明

### 画面实时预览

使用 scrcpy 实现实时预览手机画面的效果。
从 https://github.com/Genymobile/scrcpy/releases 下载编译好的版本，
例如下载 scrcpy-win64-v1.24.zip，解压缩后将文件夹改名为 `scrcpy-win64` 放置在 `%LOCALAPPDATA%\EasyADB\tools\` 目录下，
EasyADB 会自动调用 `tools/scrcpy-win64/scrcpy.exe`。
如果要升级替换 scrcpy 的版本，只需替换 `scrcpy-win64` 目录下的文件即可，实现无缝升级。

# FIXME

- [ ] [BUG] 弹窗位置要优化，多屏设备的时候，在屏幕 2 点击，会展示在屏幕 1 中心。

# 更新记录

## v 2.0.0（CMP 重写版，开发中）
- Compose Multiplatform 完整重写，P0–P13 迁移完成（详见 [`doc/迁移进度.md`](./doc/迁移进度.md)）
- UI 全面对齐 H5 参照稿：五 Tab 结构、明暗主题切换、一体式标题栏、Win11 DWM 原生圆角
- scrcpy 支持独立窗口与嵌入模式，嵌入模式支持键盘透传
- MSI 按用户安装包分发

## v 1.0.5
1. [x] [功能] 支持从目标设备提取 APK 文件。
2. [x] [优化] 关于页面重构，增加信息展示。
3. [x] [功能] 内置 Scrcpy 从 v2.3.1 升级至 v3.3.4

## v 1.0.4
1. [x] [优化] 修复真机通过有线连接时出现的连接问题。
2. [x] [功能] 🔥 增加 apk 文件的包名、版本号、签名、图标等信息的查看功能，右键图标支持导出；(快捷键 ALT+H)
3. [] [功能] 🔥 增加 frida 支持；

## v 1.0.3
1. [x] [功能] 增加 android platform-tools 的依赖，提供独立的 ADB 环境支撑;
2. [x] [功能] 🔥 增加设备自动检测功能，自动同步设备状态;
3. [x] [功能] 🔥 增加实时预览设备功能;
4. [x] [功能] 日志窗口与实时日志窗口新增 'Scroll To End' 的功能, 使用新图标;
5. [x] [优化] 优化屏幕截图，提高截图时的应用响应速度;
6. [x] [优化] 部分模块优化, bugs 修复;
7. [x] [重构] 数据库文件位置改动, 改为用户应用数据目录(涉及数据手动迁移);

数据库手动迁移说明，将原有 .\data\easyADB.db 手动复制到 C:\Users\<your_name>\AppData\Local\EasyADB\data 中即可。

## v 1.0.2
1. [功能] 🔥 增加实时 logcat 功能! 支持日志级别、进程 pid、指定文本的过滤。(暂不支持搜索);<br>
2. [功能] 设备列表增加对 offline 设备的状态展示，支持对 offline 设备的断开与重连操作;<br>
3. [优化] 设备信息栏目区域 UI 优化，调整 margin，降低整体高度。<br>
4. [优化] 🔥 优化 Open shell 功能，打开系统自带的控制台时，默认进入目标设备的 shell 模式
5. [修复] 修复已知 bugs;<br>

## v 1.0.1
1. [功能] 🔥 增加应用参数设置区域, 增加自定义下拉选择框，可实现对数据条目的管理;<br>
2. [功能] 🔥 增加广播发送功能;<br>
3. [功能] 🔥 新增 ContentProvider 查询功能;<br>
4. [功能] 🔥 新增广播发送功能;<br>
5. [功能] 增加异常捕获处理器，发生异常时不会崩溃闪退，且支持致命异常打印及日志记录;<br>
6. [功能] 卸载应用行为增加弹窗确认功能;<br>
7. [功能] 运行时进程信息列表动态更新，设备离线后会进行清空展示;<br>
8. [优化] 设备备注名称修改后，对话框自动关闭;<br>
9. [重构] 重构命令数据结构及数据传递流程，增强命令模式的扩展性;<br>
10. [修复] 修复已知 bugs;<br>

# 发布说明

## 打包说明（v1.x / Python）

1. 确保不在`虚拟环境`下，如果处于虚拟环境下，则进入`Scripts`目录，执行`deactivate`退出。
2. 执行以下命令打包(目前仅支持目录模式)：

目录模式：
```shell
pyinstaller EasyADB.spec
```

单个文件模式:
`pyinstaller --onefile EasyADB.py`<br>
参数说明：<br>
--noconsole : 指定不要命令行窗口，否则程序运行的时候，还会多一个黑窗口。但是在执行命令操作时，会闪现，体验不好。<br>
--onefile: 生成单个 exe 文件;<br>
【注意】：单文件打包后程序运行时会被解压到临时目录：`C:\Users\YourName\AppData\Local\Temp\_MEI12345\`
certifi 证书文件就被是放在临时目录中了。会导致 REQUESTS_CA_BUNDLE 环境变量无效，导致 requests 库无法找到证书文件。<br>
后续要支持的话，就要修改代码来自动定位 certifi 文件。

打包完成后，会生成 dist 目录，打包后的文件夹在此目录。

## 关于 CA 证书文件的说明

v1.0.3 开始，使用了 requests 库，通过 PyInstaller 打包独立文件后的程序会出现:<br>
OSError: Could not find a suitable TLS CA certificate bundle, invalid path:。
原因是使用`--onefile`打包时，requests 库释放在用户临时文件夹内的 CA 证书没有被一起打包，所以运行时无法找到。

解决方案就是将证书手动加入代码内, 将本地 CA 证书的路径临时地写入系统环境变量中。我这里指定的路径为'.\certifi\cacert.pem'<br>
cacert.pem 文件可以从 https://curl.se/docs/caextract.html 下载到;

【注意】: 打包成非独立文件时，此证书文件就会被打包进去，所以不需要再手动复制了。

# TODO
