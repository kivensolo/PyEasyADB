plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm("desktop") {
        withJava()
    }

    sourceSets {
        val commonMain by getting
        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
