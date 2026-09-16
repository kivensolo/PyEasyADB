plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm("desktop") {
        withJava()
    }

    sourceSets.all {
        languageSettings {
            optIn("kotlin.RequiresOptIn")
            progressiveMode = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val desktopMain by getting {
            dependencies {
                // Win32 窗口互操作（P9 Scrcpy 嵌入：FindWindow/SetParent/SetWindowPos 等）
                implementation(libs.jna.platform)
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
