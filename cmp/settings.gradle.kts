pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "easyadb-cmp"

include(":desktop-app")
include(":platform")
include(":core:util")
include(":core:adb")
include(":core:database")
include(":core:config")
include(":core:log")
include(":core:device")
include(":core:apk")
include(":ui:designsystem")
include(":ui:home")
include(":ui:devicelist")
include(":ui:functions")
include(":ui:console")
include(":ui:logcat")
include(":ui:mirror")
include(":ui:dialogs")
