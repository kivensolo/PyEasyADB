# 技术经验：设备 `ls -la` 列布局多版本差异——真机文件树空白根因与解析策略化重构

> 类型：技术经验 / 坑点归档（区别于 solution-proposals 的实施方案文档）
> 关联：SP-5001 文件传输双栏设备文件管理（§4 数据采集与解析、§9 变更记录）
> 环境：Windows 11 + platform-tools（adb）+ Compose Desktop（ExecUtils 子进程链）
> 日期：2026-10-19
> 修复提交：`31c95d0`（兼容修复）、`014790f`（策略化重构）

## 1. 问题现象

文件传输 Tab 连接**真实设备**（Konka KKHi3751V553 电视，Android 5.1.1）时，设备文件树**整棵空白**——根目录 `/` 列举不出任何条目；同一份代码连模拟器（emulator-5554，Android 较新）则一切正常。

控制台无明显报错（命令退出码为 0），UI 侧表现为树面板空态。

## 2. 排查过程

先在真机上直接跑 App 实际执行的命令：

```shell
adb shell "ls -la '/sdcard/'"     # 返回空输出，exit=0（该机 /sdcard 本身是空挂载点）
adb shell "ls -la '/'"            # 正常输出，但格式与模拟器不同！
```

对比两台设备的 `/` 输出：

**模拟器（现代 toybox，Android 6+）**——8 列：

```text
drwxrwx--x  2 root sdcard  4096 2026-09-28 09:41 Download
-rw-rw----  1 root sdcard  4096 2026-10-09 11:02 device-info.txt
```

**真机（老版 toolbox，Android 5.1.1）**——**目录 6 列、文件 7 列**：

```text
drwxr-x--- root     root              2024-06-19 11:05 .android        （目录：无 links、无 size）
-rw------- root     root            2 1970-01-01 08:00 .layout_version（文件：无 links，有 size）
lrwxrwxrwx root     root              1970-01-01 08:00 sdcard -> /storage/sdcard（链接行）
```

真机输出被原解析器按现代格式逐行解析：`cols.size < 8` 全部返回 null → `parseLsOutput` 得 0 条 → 树空白。**命令成功、解析静默全灭**，所以控制台无报错——这是本 bug 难定位的原因。

## 3. 根因

Android 设备端 `ls` 不是同一个实现，历史上换过三代，**列布局互不相同**：

| 实现来源 | 出现场景 | 列布局 | 示例 |
|---|---|---|---|
| **toybox**（Android 6+ 至今） | 绝大多数现代设备、模拟器 | 8 列：perm links owner group size date(HH:mm) time name，ISO 日期 | `drwxrwx--x 2 root sdcard 4096 2026-09-28 09:41 Download` |
| **toolbox**（Android ≤5.1） | 老设备（本例电视） | 6~7 列：perm owner group [size] date time name——**无 links 列，目录/链接行无 size 列**，ISO 日期 | `drwxr-x--- root root 2024-06-19 11:05 .android` |
| **busybox / GNU coreutils**（刷机/第三方 ROM） | 刷过机设备 | 9 列：perm links owner group size month day time/year name，**月份名日期** | `-rw-rw---- 1 root sdcard 4096 Sep 28 09:41 name` |

要点：

1. **toybox 自身无版本差异**——从 Android 6 进入 AOSP 后 `ls -l` 格式稳定在 8 列 ISO 日期至今；「多版本」来自 OS 层换 ls 实现，不是 toybox 改版。
2. **月份名变体实为两种来源**（busybox 与 GNU coreutils 输出几乎一致，均遵循 GNU ls 的「近 6 个月给 `Sep 28 09:41`、更早给 `Sep 28 2021`」规则），一个策略可同时覆盖。
3. **toolbox 还有更老的次级差异**（Android 2.x~4.x 部分版本连 `-a` 都不支持、文件行也无 size 列），现役设备极罕见，遇到时按 §6 扩展方式追加策略即可。
4. locale 一般不影响月份名——`adb shell` 走非交互 shell，locale 为 `C`/英文，`MONTHS` 英文映射够用。

原实现（SP-5001 初版）按「现代 toybox 8 列 + 月份名 9 列」两种格式硬编码在**同一个函数**里 if-else 分流，且强制 `cols.size >= 8`、`cols[4]` 必须是数字 size——老版 6/7 列格式完全无法命中。

## 4. 隐藏陷阱：不能用「倒数第 N 列」定位日期

重构第一版曾用「倒数第 3 列是日期、倒数第 2 列是时间」定位老版格式的关键列——**fixture 验证即失败**：

符号链接行 `charger -> /sbin/healthd` 的 name 段含空格，整体占多个 token，倒数定位后 name 被截断成 `"charger -> "`、**丢掉链接目标**（linkTarget）。

正确做法：老版格式按 **`cols[3]` 是否数字判定有无 size 列**（数字=size，非数字=目录/链接行的日期列），日期/时间取固定位；name 用 `cols.drop(nameStart).joinToString(" ")` 把截断剩余列拼回（现代格式的 `limit=8` 切分天然把整段 name 收进最后一列，无此问题）。

## 5. 解决方案：格式策略列表

`DeviceFileLister.parseLine` 重构为**策略派发**（提交 `014790f`）：

```kotlin
private fun interface RowFormat {
    /** 能解析该行返回字段；不匹配返回 null（交下一个策略尝试） */
    fun parse(line: String): ParsedFields?
}

private val ROW_FORMATS = listOf(ToyboxModern, ToolboxMonthName, ToolboxLegacy)

// parseLine 核心逻辑一行：
val fields = ROW_FORMATS.firstNotNullOfOrNull { it.parse(line) } ?: return null
```

- **每种列布局一个独立策略**，策略内所有 `cols[x]` 拆成语义化局部变量（`perm`/`links`/`owner`/`size`/`date`/`time`/`name`），杜绝裸下标不可读问题；
- **策略间互不误命中**的判据：现代格式第 2 列（links）是数字、日期列在固定位；老版格式 owner 列非数字 + 日期为 ISO 格式；月份名格式第 6 列命中英文月份表；
- `ParsedFields` 数据类收敛产出（perm/name/time/sizeBytes），调用方不感知列下标；
- 顺手修了两个重构验证中暴露的口径回归：链接行 name 截断（§4）、现代格式日期 `-` 未换 `/` 分隔。

验证方式：临时 main 程序对三种格式的 fixture 做逐行 + 字段级断言（目录 size="--"、文件 size/时间、链接 target、月份名年份补齐/时分补齐），**ALL PASS** 后删除临时验证文件（未进 git 索引）。

## 6. 后续扩展约定

遇到新设备格式（如极老 toolbox 的无 size 文件行）时：

1. 新增一个 `RowFormat` 实现（局部变量语义命名 + 列位图 KDoc）；
2. 注册进 `ROW_FORMATS` 列表（注意注册顺序，越特异的格式越靠前）；
3. 在临时验证程序中加该设备的真实输出 fixture，全字段断言通过后移除。

不改任何既有策略代码。

## 7. 经验沉淀

- **「命令成功 + 结果空白」= 优先怀疑解析层全灭**，而不是命令没执行——`ls` 类命令对不认识的输出格式应显式报「无法解析」而不是静默空列表（当前 `firstErrorLine` 已把「非 total 且解析失败的行」作为失败原因呈现给 UI，本次正是靠它反推定位）。
- **新设备 = 新输入格式**。凡是解析设备端输出（`ls`/`dumpsys`/`pm list`）的代码，第一问应该是「目标设备谱系里这个命令有几种输出形态」，而不是只对当前测试机调试。
- 解析多版本输出，**策略列表模式**优于 if-else 累积——每加一种格式，if-else 版的圈复杂度线性上涨，策略版只加一个独立单元。
