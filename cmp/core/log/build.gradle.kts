plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":platform"))
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    // kotlin-logging 运行时委托 SLF4J；core:log 作为库模块本不该硬绑实现，但
    // 自检入口（LogSelfCheck main）独立运行需要，加 runtimeOnly 不影响下游
    runtimeOnly(libs.slf4j.simple)
}

// 日志落盘自检入口（开发工具）：gradlew :core:log:run
application {
    mainClass = "com.easyadb.core.log.LogSelfCheckKt"
}
