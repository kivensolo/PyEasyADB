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
    implementation(project(":ui:devicelist"))
    implementation(project(":ui:functions"))
    implementation(project(":core:config"))
    implementation(project(":core:log"))
    implementation(project(":core:device"))
    implementation(project(":core:database"))
}