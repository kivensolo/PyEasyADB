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
    implementation(project(":core:log"))
    implementation(project(":core:device"))
    implementation(project(":core:database"))
    implementation(project(":core:util"))
}
