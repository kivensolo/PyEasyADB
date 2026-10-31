# Compose-Desktop 崩溃后进程无法退出（僵尸进程）

| 项 | 内容 |
|---|---|
| 分类 | 踩坑记录 |
| 日期 | 2026-10-31 |
| 复现 | CMP 桌面版（Compose Multiplatform 1.7.3 / Kotlin 2.0.21 / JDK 17） |
| 影响 | App 内部异常崩溃后，点关闭按钮无效，进程无法退出，只能任务管理器强杀 |

## 症状

- App 内部任意异常崩溃后，**窗口还在但像一张"死照片"**：点击无响应。
- 点关闭（×）**没用**，只能从 Windows 任务管理器杀进程。
- 残留的进程在任务管理器里显示为 `Java(TM) Platform SE binary`（开发态
  `gradlew :desktop-app:run` 直接用系统 JDK 起 java.exe，与 App 无关，见文末）。
- 一次崩溃可能残留**多个**这种僵尸进程，越积越占内存。

## 根因（基于实测取证，非推测）

### 完整因果链

```
App 内部某处抛未捕获异常（本例：ScrcpyLauncher.stop() 抛 NoClassDefFoundError）
  → 异常抛回 AWT EventDispatchThread（EDT）
  → EDT 按设计「捕获 Throwable → 打印 → 继续泵事件」（JDK 源码确证）        ①
  → 所有 UI 代码（含 Compose 重组/绘制）都跑在 EDT 上，Compose 的
    Recomposer 随之失效：「界面线程还活着，但 UI 更新机制已停摆」            ②
  → 点关闭按钮 → Window.windowClosing → closeApp
  → closeApp 里某步清理（本例 scrcpyLauncher.stop()）抛异常
    → 把后面的 exitApplication() 顶掉                                       ③
  → main 线程还卡在 application { } 的 runBlocking 里，等「所有窗口
    正常关闭」的信号——永远等不到                                            ④
  → 进程僵死：EDT 活着空转、界面死照片、关闭钮点了没反应
```

### 关键概念：EDT / Recomposer 失效

- **EDT（Event Dispatch Thread）**：Java 桌面程序的 UI 总线程。Windows 把用户操作
  （点按钮、拖窗口、按键盘）变成消息塞进队列，EDT 死循环地取一条 → 分发给组件 →
  再取下一条。**所有 UI 代码必须跑在这个线程上**。
- **EDT 的关键设计**：处理某条事件抛异常时线程**不死**——捕获、打印栈、继续循环
  （JDK 17 `EventDispatchThread.pumpOneEventForFilters` 内
  `catch (Throwable e) → processException(e)`，已解包 src.zip 确证）。
  平时是优点（单个按钮 bug 不拖垮界面），在本场景成了帮凶：**它让"已坏掉的窗口"
  看起来还半死不活地活着**。
- **Recomposer（重组器）**：Compose 的心脏。`@Composable` 声明 UI 描述；状态一变，
  Recomposer 负责「哪里变了就重新执行哪里」。重组过程抛未捕获异常后，Recomposer
  调度卡死/放弃——**之后无论状态怎么变，界面都不再更新、按钮点击不再有响应**。
  「EDT/Recomposer 失效」= 界面线程活着 + UI 更新机制已死——僵尸的根源。

### 触发本案例的具体异常（开发态诱因）

栈：`NoClassDefFoundError: com/easyadb/core/adb/MirrorState$Connecting`
（`ScrcpyLauncher.stop(ScrcpyLauncher.kt:211)` ← `Main.kt` closeApp ← `windowClosing`）

- 该类在源码里**存在**、编译也正常，是**运行时类加载**问题。
- 真正原因：该 JVM 运行近 2 小时，期间 gradle 多次重新构建把脚下的 `core.jar`
  换掉了（**跑着 run 又重新 build**）。JVM 类加载器按需懒加载，等
  `ScrcpyLauncher.stop()` 第一次真正用到 `MirrorState$Connecting` 时，读到的已是
  换过的新 jar → `ClassNotFoundException` → `NoClassDefFoundError`。
- **只在开发态出现**（run 中重复 build），打包版不会有这个诱因。但暴露的
  **结构性缺陷是真实的**：`closeApp` 的清理步骤没有任何隔离，任何一步炸都读不到
  `exitApplication()`，换成打包版里的任意真 bug 结果一样。

## 取证过程（复盘方法论）

1. **jstack 身份判定**：对残留进程 `jstack <pid>`——`main` 卡在
   `Application_desktopKt.application:115` 的 `runBlocking`；EDT 活着、空转等事件；
   0 个 BLOCKED、协程 worker 全 park。排除死锁，指向「关闭链路没走完」。
2. **日志对照定位卡点**：崩溃会话里点关闭时 `[DeviceWatcher] stopped` 打了、
   `Database connection closed` 没打——两者都在 closeApp 顺序段内，
   **抛在中间某步**（本例 `scrcpyLauncher.stop()`），`exitApplication` 没跑到。
3. **补抓崩溃栈**：EDT 的异常栈打到 stdout/控制台，**不在 easyadb.log**；
   最终从后台 run 任务的输出里抓到完整 `NoClassDefFoundError` 栈。
   排查这类问题时不要只看应用文件日志。
4. **设计前确证 JDK 行为**：解包 JDK src.zip 读 `EventDispatchThread`，确认
   「EDT 吞异常不死」的设计，才定「看门狗兜底」而非「优雅恢复」的方案方向。

## 修复（两层防御，commit ab0f9df）

`desktop-app/.../Main.kt` 的 `closeApp`：

```kotlin
val closeApp: () -> Unit = {
    runCatching { watcher.stop() }                     // 四步清理各自隔离
        .onFailure { appLogger.error { "closeApp: watcher.stop failed: ${it.message}" } }
    runCatching { scrcpyLauncher.stop() }
        .onFailure { appLogger.error { "closeApp: scrcpy stop failed: ${it.message}" } }
    runCatching { Win32Interop.terminateStaleScrcpy(ScrcpyLauncher.WINDOW_TITLE) }
        .onFailure { appLogger.error { "closeApp: terminateStaleScrcpy failed: ${it.message}" } }
    runCatching { DbManager.close() }
        .onFailure { appLogger.error { "closeApp: db close failed: ${it.message}" } }
    AppLogger.closeAll()
    /* 为防止 exitApplication 失败，则此处延迟强杀，保证进程必死。
     * 正常退出时 daemon 线程随 JVM 无感消失，不参与任何清理 */
    thread(isDaemon = true, name = "exit-watchdog") {
        Thread.sleep(3000)
        Runtime.getRuntime().halt(1)
    }
    exitApplication()
}
```

- **第 1 层（runCatching 逐步隔离）**：清理单步失败只记一行日志、继续后续清理，
  保证 `exitApplication()` 必被调到——直接治愈本案例（异常传不到 EDT）。
  选逐步包裹而非整段包一个：避免 `watcher.stop()` 先炸时跳过 scrcpy/DB 清理
  （scrcpy 进程残留、DB 连接不关）。
- **第 2 层（退出看门狗）**：即使 `exitApplication()` 被调到、但因 EDT/Recomposer
  失效而卡死（窗口拆除永不完成），3s 后 `Runtime.halt(1)` 强杀，进程必死。
  `AppLogger.closeAll()` 在看门狗启动前执行，保证文件日志先落盘；正常退出时
  daemon 线程随 JVM 无感消失，零副作用。

## 附带：为什么进程名是 "Java Platform SE binary"

开发态 `gradlew :desktop-app:run` 直接用系统 JDK（`D:\...\jdk-17.0.13\bin\java.exe`）
起裸 JVM 跑 `com.easyadb.desktop.MainKt`，任务管理器看到的就是 java.exe 的产品名。
**与僵尸残留是两回事**——打包后由 `desktop-app/build.gradle.kts` 的
`packageName = "EasyADB"` 决定进程名（EasyADB.exe），开发态看到
"Java Platform SE binary" 属正常现象，无需处理。

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-31 | 创建：僵尸进程根因、EDT/Recomposer 原理、取证复盘、两层防御修复（ab0f9df）。 |
