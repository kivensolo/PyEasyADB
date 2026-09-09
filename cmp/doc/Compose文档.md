# Android Compose 组件
https://developer.android.google.cn/develop/ui/compose/components/menu?hl=zh-cn

---

# remember：让值在重组（Recomposition）之间存活

## 一、它解决什么问题

Composable 函数会被 Compose **反复重新执行**（重组）：依赖的状态变化、父级重组等都可能触发。
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
