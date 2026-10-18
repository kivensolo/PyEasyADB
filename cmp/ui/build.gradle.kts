plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)

    implementation(project(":platform"))
    implementation(project(":core"))
}

// 注：此处不再注册 compose.desktop.application 运行入口——原先把 mainClass 指向
// 弹窗画廊（:ui:run），与主程序 :desktop-app:run 在 Gradle 面板中并存，极易误点
// （走查反馈：跑成弹窗画廊）。画廊仍可从 DialogsPlayground.kt 的 main() 箭头启动。
