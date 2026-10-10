
# expoect & actual object
`expoect object` 用于在‌公共代码‌中声明一个“期望的单例”，然后在‌各平台代码‌中分别给出具体实现`actual object`。<br>
简单说，它就是“跨平台单例”的声明与实现配对。

## expoect object

可以用 expect object 在 commonMain 中声明一个单例的“壳”，不写实现；<br>
然后在 Android、iOS 等各平台源码集中，用 actual object 写出真正的实现。<br>
编译器在构建时会自动把 expect 和对应的 actual 匹配起来，最终各平台只保留自己那份实现。

示例代码：
```kotlin
// commonMain
expect object PlatformInfo {
    fun platformName(): String
}

// androidMain
actual object PlatformInfo {
    actual fun platformName(): String = "Android"
}

// iosMain
actual object PlatformInfo {
    actual fun platformName(): String = "iOS"
}
```

# ⚠️ 注意事项
- `actual object` 只能写在平台源码集里（如 androidMain、iosMain），不能放在公共代码中。
- 各平台 actual object 的包名必须和 expect object 保持一致。
- 从 Kotlin 2.0 开始，actual 的可见性限制有所放宽，实现可以比声明更公开。
