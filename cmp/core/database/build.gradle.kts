plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core:log"))
    implementation(project(":core:config"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqlite.jdbc)
}
