plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core:log"))
    implementation(project(":core:util"))
    implementation(libs.kotlinx.coroutines.core)
}
