plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging) // core.log
    implementation(libs.sqlite.jdbc) // core.database
}
