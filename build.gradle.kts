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

// UI 应用版本与 NeoLink API 依赖版本是两个独立发布节奏，禁止互相推导。
extra["neoLinkUiVersion"] = "7.2.1"
extra["neoLinkApiVersion"] = "7.2.0"

version = extra["neoLinkUiVersion"] as String

// ============================================================================
// 子模块公共配置：确保所有子模块继承仓库源
// ============================================================================
subprojects {
    repositories {
        // Compose Desktop 的平台 runtime 会传递解析 Skiko native JAR。
        // 阿里云 central 镜像存在“POM 可见但 native JAR 缺失”的情况；Gradle 一旦在某个仓库解析到
        // module metadata，就会把该 module 的 artifact 固定到同一个仓库，后续不会再回退到其它仓库。
        // 因此必须用 exclusiveContent 把 Compose/Skiko 明确绑定到官方 Compose 仓库，避免低 CPU 的
        // 重复 HEAD/GET 探测和最终解析失败。
        exclusiveContent {
            forRepository {
                maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
            }
            filter {
                includeGroup("org.jetbrains.compose")
                includeGroup("org.jetbrains.skiko")
            }
        }

        mavenLocal()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
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

