# SP-5001 文件传输双栏 Device Explorer — 实施计划

## 0. 目标与视觉契约

按本会话迭代完成的 H5 参考稿 `doc/ui-reference/index.html` 文件传输 Tab 终稿，在 CMP 端实现：
- **双栏树**（SplitPane 拖拽分栏）：左 PC 本地树（3 列：文件/时间/大小）、右设备树（4 列：+权限，`ls -l` 同构）；行选中（淡底+左缘竖条）、目录行展开/收起（箭头旋转、子级缩进 16dp）、路径栏显示选中节点完整路径；
- **双侧工具栏**：PC=[投递→][提示][刷新]；设备=[投递←][新建文件夹][另存…][推送…][删除][提示][刷新]；投递图标用 H5 定稿的「两栏边界+跨栏箭头」组合图形；
- **传输语义**：投递=本侧选中文件→对侧当前目录；另存=弹窗（任意目录+改名）；推送=FileDialog 选本机任意文件；设备面板支持**拖放任意文件**直接推送到当前选中目录（v1 纳入）；
- **传输队列**：底部卡片，v1 不确定进度动画+成功(绿)/失败终态（真实百分比留 v2 sync 协议）；
- **旧入口彻底移除**（兑现 SP-7001 §6 预留）。

已确认范围决策：进度=动画+终态；彻底移除旧三大卡；拖放纳入 v1；目录收藏延后 v2（写入演进章节）。

## 1. 架构总览（三层，全部落在现有单模块 :core / :ui）

```
core/src/main/kotlin/com/easyadb/core/
├── file/FileEntry.kt            数据模型
├── file/DeviceFileLister.kt     设备侧目录列举（adb shell ls -la 懒加载）
├── file/PcFileLister.kt         PC 侧目录列举（NIO，权限/隐藏过滤）
└── file/TransferCenter.kt       传输任务中心（状态+执行，宿主持有）
ui/src/main/kotlin/com/easyadb/ui/filetransfer/
├── FileTransferPanel.kt         Tab 根组件（双栏+队列编排）
├── FileTreePane.kt              通用树面板（PC/设备参数化）
├── FileTreeState.kt             树状态（缓存/展开/选中/加载中）
├── SaveAsDialog.kt              另存弹窗（DialogWindow 模式）
└── TransferQueue.kt             队列卡片列表
desktop-app .../Main.kt          宿主接线（TransferCenter 创建/日志/清理旧回调）
ui .../MainWindowScreen.kt       TAB_FILES 分支替换 + 参数清理
```

## 2. core 层设计

**FileEntry**（data class，沿用工程 val+默认值约定）：
`FileEntry(path, name, isDir, perm: String? = null, modifiedAt: String? = null, sizeText: String = "", sizeBytes: Long = -1, childCount: Int = 0)`

**DeviceFileLister**（object，AppListLoader 同款模式）：
- `suspend fun list(deviceIp: String, dirPath: String): List<FileEntry> = withContext(Dispatchers.IO)`：`adb -s ip shell "ls -la '<dir>'"`（useShell=true；路径用内层单引号包裹，规避空格断词——Windows cmd 嵌套引号行为需实现时用带空格目录实测并固化注释）；走 `ExecUtils.execCmd`（**已显式 UTF-8**、并发读 stdout/stderr、超时设 15s）；
- 解析规则：跳过 `total N`/`.`/`..` 行；按空白切前 7 列（权限/链接/主/组/大小/日期/时间），**文件名=第 7 列后的剩余串**（保留空格）；目录 childCount 由首访子目录列出时回填（v1 目录行显示「N 项」可用 `ls | wc` 合并命令或子级懒加载后更新——实现时选后者，首次未加载显示 `--`）；失败返回 emptyList + 日志（AppListLoader 先例）；
- `fun mkdirCmd(deviceIp, dir)` / `fun rmCmd(deviceIp, path)` 命令构建器。

**PcFileLister**（object）：
- `suspend fun list(dir: File): List<FileEntry>`：`Files.newDirectoryStream` + `readAttributes`；`DosFileAttributes` 过滤隐藏/系统项；**每个条目独立 try-catch** 吞 `AccessDeniedException`（Windows 受限目录大量抛异常的既知坑）；时间格式化 `yyyy/MM/dd HH:mm`、大小人性化（B/KB/MB/GB）；根入口 `listRoots()` 供初始视图（默认打开 `user.home`）。

**TransferCenter**（class，宿主创建一次）：
- `data class TransferJob(id, direction: Direction(PC_TO_DEV/DEV_TO_PC), fileName, fromPath, toPath, state: State(RUNNING/DONE/FAILED), error: String?)`；
- `val jobs: MutableStateFlow<List<TransferJob>>`（完成项 1.2s 后淡出移除，对齐 H5）；
- `fun submit(...)`：在内部 `SupervisorJob + Dispatchers.IO` scope 执行 `adb -s ip push/pull "<from>" "<to>"`（引号包裹、`>` 命令回显走注入的 logAppender，Main.kt 先例格式）；**生命周期挂宿主——面板随 Tab 切换销毁传输不中断**（AnimatedContent 销毁面板的关键约束，与 consoleLogFlow 同层级）。

## 3. UI 层设计

**FileTreeState**（每栏一个，面板内 remember）：`entriesCache: MutableMap<String, List<FileEntry>>`、`expanded: MutableSet<String>`、`selectedPath: String?`、`loadingPath: String?`；展开即懒加载一层（loading 期间行尾小 spinner）；刷新=清缓存重载已展开路径；设备栏 `LaunchedEffect(deviceIp)` 进入 Tab/换设备自动加载根（AppManagerPanel 先例）。

**FileTreePane**（通用组件）：参数 `pc: Boolean`（列数 3/4 与反斜杠路径显示）、state、工具栏 slot；结构=头部(OS 徽章+标题+路径)→工具栏→列头→`LazyColumn` **扁平化可见行**（DFS 展开结果，`key = { it.path }` 稳定键，LogcatPanel seq-key 先例）；行=caret(旋转动画)+文件夹/文件图标+名称(+省略)+perm/time/size 等宽列；选中=Selected 底+左缘 2dp Primary 竖条；`VerticalScrollbar` 复用；Tooltip 用 SysBadgeButton 的 Popup 模式提取复用。新图标进 `EasyAdbTileIcons`：DeliverRight/DeliverLeft（跨栏组合图形，按 H5 SVG path 直译）、FolderPlus、Folder、SaveAs；复用 Refresh/Clear/PushFile/PullFile。

**工具栏行为**：投递（守卫：须选中文件，否则 toast/日志提示）→ TransferCenter.submit；设备侧新建文件夹（重名追加序号）→ `shell mkdir` → 刷新该目录；删除（仅文件，`shell rm -f`）→ 行移除；另存=打开 SaveAsDialog（预填 PC 侧当前目录+源文件名，可改目录/改名）→ submit 拉取；推送=FileDialog(LOAD)（Main.kt 同款）→ push 到设备选中目录。

**拖放**：把 `ApkDropTarget.kt` 的能力泛化为 `Modifier.fileDropTarget(extensions: Set<String>? = null, onFilesDropped)`（apkDropTarget 改为薄封装调用它，安装卡行为不变），设备树整块挂载→ 取首个文件 push 到设备当前选中目录。

**TransferQueue**：底部队列卡片（方向标签/文件名/LinearProgressIndicator indeterminate/DONE 绿色+淡出/FAILED 红字+错误摘要），空时占位 0 高。

**SaveAsDialog**：DialogWindow 模式（rememberDialogState 420×220）：目录输入（手输任意路径）+文件名输入+说明文案+取消/开始拉取。

## 4. 宿主接线与旧入口清理（兑现 SP-7001 §6.1）

- `MainWindowScreen`：TAB_FILES 分支由 `TemplateGrid` 改为 `FileTransferPanel(deviceIp, transferCenter, onLog, modifier)`；删除 `filesItems` 过滤、`onPushFile/onPullFile/onPullApk` 三个参数及 Main.kt 对应实现块（Main.kt:668-773 中相关段）；`onExtractApk`（应用管理）**保留不动**；
- 删除 `PullApkDialog.kt`、`DialogsPlayground` 演示段、`FileTransferCard.kt`（含 CompactFilePathInput）、`TemplateGrid` 的 FILE_TRANSFER_ACTIONS/m_pull_apk 分发逻辑；
- XML 种子不动（Python 版共用），CMP 加载侧沿用 `REMOVED_MENU_ACTIONS` 先例过滤 `tab=="files"` 的三个条目（m_push_file/m_pull_file/m_pull_apk）与快捷操作里的 m_pull_apk 入口（实现时以种子实际归属为准核查）；
- `TileIconMapper` 中失去引用的 action 映射同步清理。

## 5. SP-5001 文档落地（先行产出，编码前提交评审）

按 SP-7001 骨架写 `doc/solution-proposals/SP-5001-文件传输双栏设备文件管理.md`：属性表（领域 50 首案/状态/涉及代码/关联 SP-7001）→ 1.背景与目标（含待重构 TODO 收编说明）→ 2.布局与交互（ASCII 图，对齐 H5 终稿）→ 3.UI 还原清单（H5 元素→实现两列表）→ 4.数据采集与解析（ls 格式/解析规则/编码/引号策略/PC NIO 权限策略）→ 5.状态与数据流（树状态/TransferCenter 宿主持有原因）→ 6.旧入口清理清单 → 7.后续演进（真实进度=adb sync 协议、常用目录收藏、目录删除/重命名、盘符导航）→ 8.验证（编译+实机走查清单）→ 9.变更记录。
同步更新：solution-proposals/README.md 索引表加 SP-5001 行；`doc/待重构功能.md` 远程文件操作 TODO 标注「pull/push/拖放已立项 SP-5001，目录收藏待办」。

## 6. 实施顺序（每步可编译验证）

1. **SP-5001 文档 + README 索引 + 待重构更新**（纯文档，先落地供评审）
2. **core 层**：FileEntry/DeviceFileLister/PcFileLister（含 ls 解析——先写 fixture 样例串在 main 校验脚本里跑通）→ `gradlew :core:compileKotlin`
3. **UI 骨架**：FileTreeState/FileTreePane/新图标 → PC 树先单独可浏览（DialogsPlayground 式独立预览或直接进 Tab）
4. **完整面板**：设备树+工具栏+投递+新建/删除+SaveAsDialog+推送+队列+拖放泛化 → 接入 TAB_FILES
5. **旧入口清理 + 宿主接线收尾** → `gradlew :desktop-app:build` 全量编译 + 实机走查（SP §8 清单：懒加载展开/选中投递双向/另存改名/推送选择器/拖放/新建删除/刷新/明暗主题/Tab 切换传输不中断/中文与空格路径）
6. **提交**（单提交，沿用 `Title(feat)：文件传输双栏 Device Explorer（SP-5001）——…` 约定）；本会话已完成的 H5 参考稿改动（徽标/控制台/文件传输全部迭代）另起一个 `Title(feat)` 提交（H5 参照稿同步）

## 7. 已识别风险与对策（写入 SP）

- Windows cmd 嵌套引号+路径空格：内层单引号方案，带空格目录实测定稿；
- adb shell 输出编码：ExecUtils 已 UTF-8，直接复用不重写；
- AnimatedContent 切 Tab 销毁面板：TransferCenter 宿主持有，传输不中断；
- Windows 受限/网络目录列举阻塞：全部 Dispatchers.IO + 行级 loading 态；
- ls 日期格式差异（toybox 版本）：解析器宽容设计（识别 `yyyy-MM-dd HH:mm` 与月日时分两种），fixture 覆盖。

预估工期：core 2~3 天、UI 4~5 天、清理接线 1 天、文档 0.5 天，合计约 8~9 个工作日。