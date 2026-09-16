plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":ui:designsystem"))
    implementation(project(":core:adb"))
    implementation(project(":core:apk"))
    implementation(project(":core:log"))
    implementation(project(":core:device"))
    implementation(project(":core:database"))
    implementation(project(":core:util"))
}

// 弹窗画廊 Playground 入口：仅供 IDE/命令行独立调试运行（gradlew :ui:dialogs:run），
// 不参与 desktop-app 打包，不影响库本身。
compose.desktop {
    application {
        mainClass = "com.easyadb.ui.dialogs.playground.DialogsPlaygroundKt"
    }
}
