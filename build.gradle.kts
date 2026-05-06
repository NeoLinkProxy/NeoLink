// ============================================================================
// NeoLink Root Build Script — "Umbrella" Project
// 职责：统一插件版本声明 + 公共变量管理 + 子模块仓库继承
// 子模块：:common (Java 17)、:desktop (Java 21)、:android (Java 17)
// ============================================================================

plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("org.jetbrains.compose") version "1.6.1" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    // Android 生态插件（供 :android 子模块使用）
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

group = "neoproxy"

// 统一版本管理：所有子模块通过 rootProject.extra["neoLinkApiVersion"] 引用
extra["neoLinkApiVersion"] = "7.1.12"

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

