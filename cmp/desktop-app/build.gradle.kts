plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.slf4j.simple)

    implementation(project(":platform"))
    implementation(project(":core:log"))
    implementation(project(":core:util"))
    implementation(project(":core:config"))
    implementation(project(":core:database"))
    implementation(project(":core:adb"))
    implementation(project(":core:device"))
    implementation(project(":core:apk"))
    implementation(project(":ui:designsystem"))
    implementation(project(":ui:home"))
    implementation(project(":ui:devicelist"))
}

compose.desktop {
    application {
        mainClass = "com.easyadb.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )

            packageName = "EasyADB"
            packageVersion = "2.0.0"
            vendor = "EasyADB"
            copyright = "© 2026 EasyADB contributors"
            description = "EasyPyADB - Compose Multiplatform rewrite"

            windows {
                menuGroup = "EasyADB"
                upgradeUuid = "8c7b3a92-4e1d-4f2b-9c5a-easyadb2026"
            }
            macOS {
                bundleID = "com.easyadb.desktop"
            }
        }
    }
}
