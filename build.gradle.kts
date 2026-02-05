import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // Kotlin 语言支持
    kotlin("jvm") version "1.9.22"
    // Compose 官方核心插件 (负责 UI 和打包)
    id("org.jetbrains.compose") version "1.6.1"
    // 显式引入 idea 插件，用于强制修复 IDEA 项目结构
    idea
}

group = "neoproxy"
version = "5.10.4"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

// === 核心修改 1：显式强制源码集 ===
kotlin {
    jvmToolchain(21)

    sourceSets {
        val main by getting {
            // 强制指定两个目录都作为 Kotlin 源码目录
            kotlin.srcDirs("src/main/kotlin", "src/main/java")
            resources.srcDir("src/main/resources")
        }
    }
}

dependencies {
    // 1. 核心业务
    implementation("fun.ceroxe.api:ceroxe-core:0.2.7")
    implementation("fun.ceroxe.api:ceroxe-detector:0.2.7")

    // 2. 7Zip 支持
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01")

    // 3. Compose UI 基础库
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.preview)
    // 强制引入 runtime 解决部分 import 无法识别问题
    implementation(compose.runtime)

    // 4. 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        // 增加编码参数，防止中文路径引起的文件读写异常
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }
}

// === Compose 官方打包配置 (保持不变) ===
compose.desktop {
    application {
        mainClass = "neoproxy.neolink.NeoLink"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "NeoLink"
            packageVersion = "5.10.4"
            description = "NeoLink Client powered by Compose"
            copyright = "© 2026 Ceroxe"
            vendor = "Ceroxe"
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "00000000-0000-0000-0000-000000000000"
            }
        }
    }
}
// === 核心修改：配置资源过滤，将 version 注入 app.properties ===
tasks.processResources {
    // 定义要注入的属性字典
    val props = mapOf("version" to project.version)

    // 输入声明（为了支持 Gradle 的增量构建缓存）
    inputs.properties(props)

    // 匹配到 app.properties 文件时执行替换
    filesMatching("app.properties") {
        expand(props)
    }
}
tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
}

// 同时建议给编译任务也加上编码限制
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}