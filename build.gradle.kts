// ============================================================================
// NeoLink Root Build Script — "Umbrella" Project
// 职责：统一插件版本声明 + 公共变量管理 + 子模块仓库继承
// 子模块：:common (Java 17)、:desktop (Java 21)、:android (Java 17)
// ============================================================================

plugins {
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
    // Android 生态插件（供 :android 子模块使用）
    id("com.android.application") version "9.2.1" apply false
}

group = "neoproxy"

// 统一版本管理：所有子模块通过 rootProject.extra["neoLinkApiVersion"] 引用
extra["neoLinkApiVersion"] = "7.2.0"

version = extra["neoLinkApiVersion"] as String

// ============================================================================
// 子模块公共配置：确保所有子模块继承仓库源
// ============================================================================
subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/public/compose/dev") {
            content {
                includeGroup("org.jetbrains.compose")
                includeGroup("org.jetbrains.skiko")
            }
        }
    }
}

// IDEA sometimes asks the root project for :testClasses even though this is a
// pure aggregator project. Keep that entry point stable and delegate to the real
// module-specific test compilation tasks.
tasks.register("testClasses") {
    group = "verification"
    description = "Assembles test classes for all modules that expose test compilation tasks."
    dependsOn(":common:testClasses", ":desktop:testClasses", ":android:compileDebugUnitTestSources")
}

