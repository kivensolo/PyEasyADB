plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":platform"))
    implementation(project(":core:log"))
    implementation(libs.kotlinx.coroutines.core)
}
