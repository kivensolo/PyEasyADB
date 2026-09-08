plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core:log"))
    implementation(project(":core:util"))
    implementation(project(":core:config"))
    implementation(libs.kotlinx.coroutines.core)
}
