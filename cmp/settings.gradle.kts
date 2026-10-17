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
include(":core")
include(":ui")
