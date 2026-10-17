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

// 弹窗画廊 Playground 入口：gradlew :ui:run，仅供 IDE/命令行独立调试运行，
// 不参与 desktop-app 打包。原 ui/mirror 的 EmbedSpike / KeySpike 仍可从 IDE
// 直接运行 main()（EmbedSpike 无需设备；KeySpike 需真机 + scrcpy，见 cmp/doc 方案文档）。
compose.desktop {
    application {
        mainClass = "com.easyadb.ui.dialogs.playground.DialogsPlaygroundKt"
    }
}
