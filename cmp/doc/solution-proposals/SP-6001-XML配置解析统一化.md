# SP-6001 XML 配置解析统一化

| 项 | 内容 |
|----|------|
| 领域 | 60 配置与持久化（XML 配置） |
| 状态 | 已实施 |
| 日期 | 2026-10-23 |
| 关联 | SP-2002（requireParam 走查事故，本方案的直接起因） |

## 1. 背景与目标

SP-2002 走查发现 requireParam 抖动不生效：`function_templates.xml` 存在**两份并行解析器**（`Main.kt` classpath 手写版 + `core` 的 `XmlConfigLoader`），多字段升级时只改了 XmlConfigLoader 一侧。由于 `FunctionItem` 字段全有默认值，漏配**静默降级**——不报编译错、不抛异常、测试全绿，仅功能失效。

探索确认重复范围更大（`Main.kt` 内 4 份手写解析函数）：

| 位置 | 问题 |
|---|---|
| `parseFunctionTemplateXml` / `parseFunctionTemplateAttrs` / `parseRequireParam` | 与 XmlConfigLoader 逐字段/逐字重复（事故根因） |
| `parseMenuConfigXml` | 语义分叉：不解析 action 的 `state` 属性、只遍历直接子节点 |
| `XmlConfigLoader.loadCmdConfig` | 零调用方（cmdConfig 消费 UI CommandPanel/MoreCommandsDialog 在 P12 后被硬编码 CommandsPanel 取代，成孤儿组件） |

重复根因 = XmlConfigLoader 只收 `File`，而 classpath 加载需要 `InputStream`。目标：**解析逻辑单一事实来源**，并加轻量单测防「漏字段静默失效」再犯。

## 2. 决策

### 2.1 InputStream 主入口 + File 便捷重载

`XmlConfigLoader` 三个加载函数（loadCmdConfig / loadFunctionTemplates / loadMenuConfig）均改为双入口：

- **`loadXxx(stream: InputStream)` 主入口**：解析逻辑搬入，外层 `stream.use { }`——**本函数负责关闭传入流**（KDoc 写明），解析异常原样抛出由调用方定策略。
- **`loadXxx(file: File)` 便捷重载**：签名不变（文件回退分支零改动），存在性检查（缺失 → warn + emptyList，保持现口径）后委托 `file.inputStream()`。

解析私有函数与字段映射**逐行不动**（requireParam/tab/size/desc/accent/state 保持 SP-2002 修复后的齐全状态）。

### 2.2 菜单语义统一：行为零变化论证

统一采用 XmlConfigLoader 深搜版（`getElementsByTagName("action")` + 解析 state）。对当前默认内置 XML 数据**行为零变化**，依据（已通读 CMP 默认内置 menus_ui.xml 验证）：

1. 全部 `<action>` 均为 `<menu>` 直接子节点，无嵌套——「只遍历直接子节点」与深搜收集结果相同；
2. 默认内置数据无任何 action 携带 `state` 属性——手写版恒空串与深搜版解析结果一致。

**残余风险**（记档）：未来若在 menus_ui.xml 引入嵌套 action，统一版会将其平铺收集进父 menu（深搜语义），属 XML 结构演进问题而非重构引入。

### 2.3 cmdConfig 死链路：保守保留

`loadCmdConfig` 零调用方，但模型类（CmdGroup/CmdItem/CmdSubGroup）与解析器**保留**：Python 对照链路存在（旧版设备树命令树由 cmdConfig.xml 驱动），CMP 端孤儿 UI（CommandPanel/MoreCommandsDialog）是否删除待后续 SP 决定。本方案仅统一其接口形态（补 InputStream 重载），不改变保留/删除状态。

### 2.4 防再犯：单测锁字段（本方案核心保障）

`core` 引入 `kotlin-test`（版本目录 `kotlin-test` 条目，version.ref = kotlin 自动对齐），新增 `XmlConfigLoaderTest`：

- **每个 XML 字段造一条非默认值样本并逐一断言**——任何解析器漏接字段都会在此红（含 requireParam 多值解析、空值过滤、state 元素属性 vs attr 子节点两类口径）；
- 覆盖 InputStream 入口（曾经的分叉路径）+ File 入口缺失口径；
- 数据用内联 XML 字符串，自包含、不受默认内置配置文件演进影响。

**约定**：今后给 `FunctionItem`/`MenuAction`/`CmdItem` 加新 XML 字段，必须同步在本测试补非默认值断言；XML 解析一律走 XmlConfigLoader，禁止在任何调用方自建解析副本。

### 2.5 默认内置文件分叉事实修正（顺带）

CMP classpath 默认内置文件（`desktop-app/resources/config/`）已与仓库根 `res/config/`（Python 使用）**分叉**：CMP 版多 3 个占位菜单项（打开 Shell/Root/Unroot）、function_templates 含 requireParam/tab/size 等新字段与 P12 卡片重排。Main.kt 原注释「Python 版共用」已不属实，改为如实描述。两份默认内置文件各自维护，是否重新对齐属待决事项（Python 侧不可动，超出本方案范围）。

## 3. 涉及文件

| 文件 | 改动 |
|------|------|
| `core/config/XmlConfigLoader.kt` | 三加载函数拆 InputStream 主入口（use 关流）+ File 重载委托；类头注明单一事实来源约定 |
| `desktop-app/Main.kt` | 删 4 个手写解析函数；2 个 loadFromClasspath classpath 分支委托 XmlConfigLoader；清 import；默认内置文件注释修正 |
| `core/build.gradle.kts` | + `testImplementation(libs.kotlin.test)` |
| `gradle/libs.versions.toml` | + `kotlin-test` 条目 |
| `core/src/test/.../XmlConfigLoaderTest.kt` | 新建：8 用例锁全部 XML 字段 |

**不改**：任何 XML 文件、仓库根 `res/config/`、Python 侧、AppPathsConfig、模型类、UI 组件。

## 4. 验证

- `:core:compileKotlin` / `:core:test`（8 用例全绿）/ `:desktop-app:compileKotlin` / 全量 `build` 通过。
- 人工走查项：`./gradlew :desktop-app:run` 启动日志 classpath 加载数量与重构前一致（模板 3 个）；快捷操作卡 desc/accent 正常；**点「启动应用」缺包名触发抖动**（SP-2002 事故直接回归点）。

## 5. 变更记录

| 日期 | 变更点 |
|------|--------|
| 2026-10-23 | 初版：InputStream 主入口统一 + Main.kt 手写解析器全删 + XmlConfigLoaderTest 字段锁测试。 |
