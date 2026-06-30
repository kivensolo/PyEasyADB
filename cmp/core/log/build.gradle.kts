plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":platform"))
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
}
