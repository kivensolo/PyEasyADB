# Android Compose 组件
https://developer.android.google.cn/develop/ui/compose/components/menu?hl=zh-cn

---

# remember：让值在重组（Recomposition）之间存活

## 一、它解决什么问题

Composable 函数会被 Compose **反复重新执行**（重组）： 依赖的状态变化、父级重组等都可能触发。
函数体里的普通局部变量每次重组都会**重新初始化**，"上一次的值"天然留不住——

1. 普通变量被赋新值不会触发 UI 更新（Compose 感知不到）；
2. 即便因其他原因发生了重组，变量又回到初始值。

`remember` 的作用：**把 lambda 的计算结果缓存进组合（Composition）的槽位中，首次进入组合时
执行一次，之后的每次重组直接返回缓存值**，直到该 Composable 离开组合。

## 二、反例：没有 remember

```kotlin
@Composable
fun Counter() {
    var count = 0                        // 每次重组都归零，且改了也不会触发重组
    Button(onClick = { count++ }) {      // 点击后 UI 永远不变
        Text("count = $count")
    }
}
```

## 三、基础用法：remember + mutableStateOf（"可触发重组的缓存"）

```kotlin
import androidx.compose.runtime.*

@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }   // 初值 0，跨重组存活，写入触发重组
    Button(onClick = { count++ }) {
        Text("count = $count")                    // 点击后 UI 实时更新
    }
}
```

要点：

- `remember { ... }`：缓存"值"本身，不产生可观察性；
- `mutableStateOf(x)`：把值包装成**可观察状态**，写入时 Compose 自动调度依赖处重组；
- 两者组合 = "既跨重组、又能驱动重组的状态"（即官方所谓 State Hoisting API 的原料）；
- `by` 委托需配套导入 `getValue` / `setValue`（`import androidx.compose.runtime.*` 已覆盖）。

## 四、remember(keys)：依赖变了才重算

```kotlin
// 摘自本工程 Main.kt：menuConfigs 重新加载（XML 变更）时才重新解析快捷键，
// 平时的重组直接复用缓存，不重复 flatMap + parseShortcut
val shortcutActions = remember(menuConfigs) {
    menuConfigs.flatMap { it.actions }
        .mapNotNull { action -> parseShortcut(action.shortcut)?.let { spec -> spec to action } }
}
```

规则：任一 key 变化 → 丢弃旧值重新执行 lambda；key 不变 → 返回缓存。
适合缓存"由参数派生的昂贵计算结果"。

## 五、状态提升：多组件共享同一份 remember（本工程实战）

`CustomMenuBar`（MainWindowScreen.kt）——菜单模式的核心状态：

```kotlin
// 当前展开的菜单下标，null 表示全部收起；提升到菜单栏一级由各标题共享，
// 才能实现"已有菜单展开时，悬浮其他标题即切换"
var activeMenuIndex by remember { mutableStateOf<Int?>(null) }
```

对比旧实现：每个标题各自 `remember { mutableStateOf(false) }`，状态互不知晓——
"点其他菜单要先关再点一次"的交互问题正源于此。提升为一份共享状态后：

- 点击标题：`activeMenuIndex = if (isActive) null else index`（切换/收起）；
- 悬浮切换：`LaunchedEffect(hovered) { if (hovered && activeMenuIndex != null) activeMenuIndex = index }`
  ——注意把 `hovered` 作为 LaunchedEffect 的 key，状态变化驱动副作用；
- 弹层展开：`expanded = activeMenuIndex == index`，一份状态驱动全部标题。

原则：**状态放在需要它的最低公共父级，向下传值 + 向上传回调**（状态提升）。

## 六、本工程其他 remember 用法速览

| 位置 | 写法 | 目的 |
| --- | --- | --- |
| CustomMenuBar | `remember { mutableStateOf<Int?>(null) }` | 跨重组的共享菜单模式状态 |
| 标题 Box | `remember { MutableInteractionSource() }` + `collectIsHoveredAsState()` | 悬停检测（hoverable 的载体） |
| Main.kt | `remember(menuConfigs) { ... }` | 带依赖key缓存派生计算 |
| Main.kt | `remember { mutableStateOf(false) }`（多个对话框开关） | 对话框显示状态 |

## 七、生命周期与坑

1. **存活范围 = 在组合中的时间**。`if (!expanded) return`（MenuBarDropdown）——弹层收起即
   离开组合，其内部所有 remember 状态丢弃，下次展开重新初始化。想跨开关保留需把状态
   提升到始终在组合中的父级，或用 rememberSaveable。
2. **位置即身份**。remember 按调用位置存储。在 `forEach` 中使用时状态绑定到"下标/位置"
   而非数据项：列表增删、排序会串状态。数据可能变动的列表应配合业务 key：
   `items(devices, key = { it.ip }) { ... }`。
3. **remember ≠ rememberSaveable**。前者随组合销毁而丢；后者额外写入 SaveableStateRegistry，
   Android 上可跨横屏旋转/进程重建恢复，桌面端用于窗口尺寸等场景。
4. **缓存对象要"值得缓存"**：一次性的小对象随手 remember 意义不大；昂贵计算、可观察状态、
   需要稳定身份的对象（如 FocusRequester、InteractionSource）才是主战场——它们的稳定身份
   往往是正确性的前提，而不只是性能优化。

## 八、一句话总结

`remember` = 跨重组的局部缓存；`remember + mutableStateOf` = 跨重组且可触发重组的状态。
私有状态用它在组件内留存，跨组件共享时向上提升（State Hoisting）。

---

# 报错速查：`IllegalArgumentException: layouts are not part of the same hierarchy`

> 记录目的：报错成因与源码定位路径（修复方式因场景而异，不做记录）。
> 定位版本：Compose Multiplatform 1.7.3（desktop）。

## 一、唯一抛出点

字符串 `layouts are not part of the same hierarchy` 在整个 Compose UI 模块中**只出现于一处**：

```
androidx.compose.ui.node.NodeCoordinator.findCommonAncestor$ui(NodeCoordinator other)
```

内部逻辑：沿两个 LayoutNode 的 `getParent()` 链向上找交汇；**任一链先到 null（根）仍未相遇即抛**。

## 二、直接成因

两个布局节点**没有共同的组合树祖先**。最典型场景：一个节点在主窗口组合树、另一个在
**Popup / Dialog 的独立弹窗组合树**——桌面端弹窗是独立 window + 独立组合树，根部分叉，
按定义不可能有共同祖先。任何跨这两棵树的"节点间坐标换算 / 关系计算"都会触发。

## 三、典型触发链（指针事件路径，本工程两次遇到）

```
指针事件分发
→ androidx.compose.ui.input.pointer.Node.buildCache(changes, layoutCoordinates, ...)
    （把事件坐标从来源节点坐标换算到分发根坐标）
→ LayoutCoordinates.localPositionOf-R5De75A(sourceCoordinates, offset)
→ LookaheadLayoutCoordinates / NodeCoordinator
→ findCommonAncestor$ui(other)  ← 两个节点分属主窗口与弹窗 → throw
```

即：**交互一旦涉及"弹窗 ↔ 主窗口"的指针/坐标跨界**（弹窗内点击牵动主窗口命中路径、
弹窗内 hover、跨界拖拽等），就走这条路。

## 四、快速定位方法（可复用）

1. **找抛出类**：报错字符串在整个依赖里通常唯一，直接在 Gradle 缓存的 jar 里定位：

   ```bash
   # 解压候选 jar（foundation/ui 等 *-desktop-1.7.3.jar）后：
   grep -ral --include="*.class" "报错子串" <解压目录>
   # 注意：grep 命中 .class 二进制必须带 -a，否则静默返回 0 条（已踩坑）
   ```

2. **反汇编确认抛出方法与调用条件**：

   ```bash
   javap -c -p -classpath <解压目录> 'androidx.compose.ui.node.NodeCoordinator' \
     | grep -B20 "same hierarchy"
   ```

3. **顺着唯一调用方上溯**：`findCommonAncestor` 的调用方是 `LookaheadLayoutCoordinates.localPositionOf`，
   再往上 `Node.buildCache`（指针事件坐标缓存构建）——据此判定是"指针事件跨树路由"，
   而非布局/测量问题。

## 五、一句话成因

**两个节点分属主窗口与弹窗两棵组合树，却被要求做"同树"关系计算**——看到此报错先找
交互路径上有没有 Popup/Dialog 与主窗口内容的指针或坐标交互。
