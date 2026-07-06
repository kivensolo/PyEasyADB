plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)

    implementation(project(":platform"))
    implementation(project(":core:log"))
    implementation(project(":core:config"))
    implementation(project(":core:device"))
    implementation(project(":core:database"))
    implementation(project(":ui:designsystem"))
}
