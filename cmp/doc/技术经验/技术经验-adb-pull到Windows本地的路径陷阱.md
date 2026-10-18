# 技术经验：adb pull 到 Windows 本地的路径陷阱——盘符根缺陷与驱动器相对路径（=E:）机制

> 类型：技术经验 / 坑点归档（区别于 solution-proposals 的实施方案文档）
> 关联：SP-5001 文件传输双栏设备文件管理（§4 数据采集与解析、§9 变更记录）
> 环境：Windows 11 + platform-tools（adb）+ Compose Desktop（JVM 子进程链 cmd.exe /c）
> 日期：2026-10-18

## 1. 问题现象

文件传输 Tab 从设备拉取文件（`adb pull`）时，App 表现为：

- 控制台回显命令与 **「[拉取成功]」**，传输队列绿色完成；
- 但**目标盘上找不到文件**——第一次复现时目标为 `E:\test.hprof`（盘符根）。

同样的命令在用户终端手工执行却成功：

```text
# PowerShell（工作目录在 C: 盘）
adb -s emulator-5554 pull "/sdcard/Download/test.hprof" "E:test.hprof"
→ 成功，文件落在 E:\ 根
```

同一命令「终端成功、App 失败（还报成功）」，且报错信息与路径形态强相关——详见下文对照矩阵。

## 2. 排查实测矩阵

以 `ProcessBuilder("cmd.exe", "/c", cmd)` + UTF-8 读取（精确复刻 App 的 ExecUtils 链路）逐一实测：

| # | 目标形态 | 结果 | 说明 |
|---|----------|------|------|
| 1 | `C:\Users\...\Temp\pull.hprof`（普通子目录） | ✅ exit=0 落盘 | TEMP 目录成功 |
| 2 | `E:\GitHubProjects\xxx.hprof`（普通子目录） | ✅ exit=0 落盘 | 链路与引号/编码无问题 |
| 3 | `E:\test.hprof`（**盘符根**） | ❌ exit=1 `cannot create file/directory` | 盘根可写仍失败 |
| 4 | `E:/test.hprof`（盘根 + 正斜杠） | ❌ 同 #3 | 斜杠方向无关 |
| 5 | `E:\GitHubProjects\...`（**深层路径拼错**） | ❌ `NoSuchFileException` | 目标目录本身不存在 |
| 6 | 工作目录=盘根 + 目标仅文件名 `easyadb.hprof` | ✅ exit=0 稳定落盘根 | 最终采用的解法（见 §6） |

## 3. 根因一：adb 对「盘符根」目标存在自身缺陷

`adb pull` 拉取时需把本地目标拆成「父目录 + 文件名」创建文件。

当父目录是**盘符根**（`E:\`）时，
Windows 版 adb 的拆分逻辑出错，即使盘根完全可写也报：

```shell
⮞  adb -s emulator-5554 pull "/sdcard/Sunshine_pwd.PNG" E:\pow.png
adb: error: cannot create file/directory 'E:\pow.png': No such file or directory
```

实测对照（同一源文件）：拉到 `E:\GitHubProjects\` 下 ✅ 成功， 拉到 `E:\` 根 ❌ 必失败；

与引号、正反斜杠、编码均无关。

**这是 adb 的缺陷/限制，规避而非修复。**

但是用不用分隔符，却可以成功，这个成功，是因为windows的隐藏变量机制(参见根因二)：

```shell
⮞  adb -s emulator-5554 pull "/sdcard/Sunshine_pwd.PNG" E:pow.png
/sdcard/Sunshine_pwd.PNG: 1 file pulled. 0.9 MB/s (41642 bytes in 0.043s)
```

## 4. 根因二：Windows 驱动器相对路径与隐藏变量「=E:」

### 4.1 每个盘符都有独立的「当前目录」

Windows 进程除了唯一的全局 CWD 外，还为**每个盘符**维护一份独立的当前目录。实测,在一个打开的 cmd 窗口里执行：
```shell
C:\Users\zhi.wang>cd /d E:\jianpianDownload  # 切换目录的同时切换盘符（d = drive)
E:\jianpianDownload>E:  #切回 E: 盘, 依旧会停在 E:\jianpianDownload 的机制来源

# 此时若执行 pull 到 "E:" 的操作, E:\jianpianDownload 就会被视为 "E:" 目录
E:\jianpianDownload> adb pull "/sdcard/Download/test.hprof" "E:test222223333.hprof"
/sdcard/Download/test.hprof: 1 file pulled. 43.8 MB/s (22418283 bytes in 0.488s)

E:\jianpianDownload>dir   # 查看是不是将 test222223333.hprof 文档拉取到 "当前工作目录"
 驱动器 E 中的卷没有标签。
 卷的序列号是 10A1-18B3

 E:\jianpianDownload 的目录

2026/10/18  21:19    <DIR>          .
2026/10/18  21:19    <DIR>          ..
2026/01/18  12:58    <DIR>          .__jianying_export_temp_folder__
2026/03/07  19:49    <DIR>          NES GAMEALL
2026/10/18  21:19        22,418,283 test222223333.hprof
               1 个文件     22,418,283 字节
               4 个目录 22,235,422,720 可用字节
```
这份「按盘的当前目录」保存在进程环境块的**隐藏变量**里，
变量名以等号开头：`=C:`、`=E:`、`=D:`……正因为带等号，`set` 永远不显示它们（`set =` 才可见），
这也是它几十年鲜为人知的原因。

### 4.2 `E:name`（冒号后无反斜杠）的解析规则

===================================【核心重点】===================================

`E:test.hprof` **不是**「E: 盘根下的文件」，而是「**E: 盘当前目录**下的文件」，落点取决于
进程环境里 `=E:` 的值：

===================================【核心重点】===================================

- `=E:` 不存在（进程从未在 E: 盘上待过）→ 默认 `E:\` 盘根；
- 进程从 E: 盘某目录启动 / 曾在该盘 cd 过 → `=E:` = 那个目录。

### 4.3 三方行为统一解释

| 场景 | 进程 CWD | `=E:` 的值 | `E:name` 落点 |
|------|----------|-----------|---------------|
| 用户 PowerShell（C: 盘启动，从未 cd 到 E:） | C:\... | 不存在 | `E:\` 根 ✅ 用户手工成功 |
| 用户其他终端（E: 盘当前目录恰为根） | E:\ | `E:\` | `E:\` 根 ✅ 手工成功 |
| **App**（从项目目录启动） | `E:\...\cmp\desktop-app` | `E:\...\cmp\desktop-app` | **项目目录里**（实锤：desktop-app\test.hprof） |

App「成功但找不到文件」的真相：**adb 真的成功了，文件落到了 App 进程的 E: 盘当前目录**
（实锤：在 `cmp\desktop-app\` 下找到误落的 test.hprof）。而 `E:\name`（带反斜杠）虽按盘根
解析，却撞上 adb 盘根缺陷——两条正路一条被 adb 堵、一条被 `=E:` 漂移坑。

## 5. 最终解决方案：子进程 CWD 锚定盘根 + 纯文件名

两条问题路径都带「盘符歧义」，解法是**不给 adb 任何带盘符的路径**：

1. adb 子进程的**工作目录直接设为盘根**（`ExecUtils.execCmd` 新增 `workingDir` 参数 →
   `ProcessBuilder.directory()`，默认 null 向后兼容）； <br> 锚定盘不是某个默认盘，而是从输入的保存目录里取出来的。
2. 命令里的目标参数**只写纯文件名**（`test.hprof`，无盘符无分隔符）——<br>相对路径解析只看真实
   CWD（= 盘根），与隐藏变量 `=E:` 完全无关，无环境可漂移；
3. 实测：文件确定性落在 `E:\name`（41.4 MB/s）；控制台回显仍显示规范形式 `E:\name`。
4. 另存到任意子目录 → 不锚定，直接绝对路径直拉。

```kotlin
// TransferCenter.submitPull（盘根分支，简化）
val dir = saveDir.trimEnd('\\', '/')        // 填 "X:\" → dir = "X:"
if (dir.length == 2 && dir[1] == ':') {     // 判定是盘符根
    run(
        command = "adb ... pull \"...\" \"$safeName\"",  // 目标只用纯文件名
        workingDir = File("$dir\\"),                      // 锚定 = 你填的那个盘的根 → X:\
    )
}
```

## 6. 复现与自验方法

1. **查看隐藏的按盘当前目录**：`cmd /c set =`（等号开头）；
2. **复现 =E: 漂移**：从项目目录启动任意进程执行 `adb pull ... E:name`，再从 C: 盘的
   PowerShell 执行同命令，对比落点；
3. **复现 adb 盘根缺陷**：`adb pull <设备文件> E:\任意名`（盘根可写时仍报 cannot create）；
4. **区分失败类型**：捕获异常时打印 `e.getClass().getName()`——`NoSuchFileException` 与
   `AccessDeniedException` 的 getMessage() 都只回显路径，仅凭消息无法区分。

## 7. 通用启示

- Windows 驱动器相对路径（`X:name`）的解析依赖进程环境，**跨进程传递时不可假设落点**；
- 子进程命令涉及相对/半相对路径时，优先用 `workingDir` 锚定 + 纯文件名，而非拼装半绝对路径；
- 外部命令的成功判定必须校验**退出码**（进程正常结束 ≠ 命令成功，ExecUtils 原 success 语义）；
- 排查文件类失败时，错误消息往往不带类型信息，**打印异常类名**才能区分 not-found / denied。

## 8. 老疑问顿悟
以前在 cmd 命令行中，从C盘切到E盘都是这样：
```shell
C:\Users> cd E:\project
C:\Users> E:
E:\project>
```
这是 cmd 从 DOS 时代延续的行为：把“换盘”和“换目录”当成两个动作。实际可以一步到位：
```shell
C:\Users> cd /d E:\project
E:\project>
```
原来 cd E:\project 只是改变了 E: 盘的当前目录记录，提示符还在C:\，人没过去。<br>
带 /d：一步到位，同时切盘符 + 切目录，人直接过去了。

cd /d（以及任何在 E: 盘上发生的 cd）会同时更新 cmd 环境块里那个隐藏变量 =E:

### 注意
PowerShell 没有这个限制，cd E:\project 天然跨盘一步到位，不需要 /d。所以这个参数只在写 cmd 批处理/命令行时才需要关心。

![powershell_without_split_d.png](img%2Fpowershell_without_split_d.png)