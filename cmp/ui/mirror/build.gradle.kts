plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":ui:designsystem"))
    implementation(project(":core:adb"))
    implementation(project(":platform"))
}

// P9 Spike 入口（开发工具）：gradlew :ui:mirror:run。默认 EmbedSpike（几何/链路
// 断言，无需设备）；验证键盘透传时切 KeySpike（需真机 + scrcpy，见 cmp/doc 方案文档）
compose.desktop {
    application {
        mainClass = "com.easyadb.ui.mirror.EmbedSpikeKt"
    }
}
