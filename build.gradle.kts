// ============================================================================
// NeoLink Desktop 客户端构建脚本
// 职责：以仓库根项目作为桌面端应用本体，保留 :common 作为共享业务模块。
// 这样 feature 分支可以独立演进 NeoAuthServer API 客户端，而不再受旧多端结构牵连。
// ============================================================================

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import java.io.File

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
    id("com.gradleup.shadow") version "9.4.1"
    jacoco
}

val neoLinkApiVersion = "7.2.0"
val neoLinkUiVersion = "7.2.1"
val skikoVersion = "0.144.6"

group = "neoproxy"
version = neoLinkUiVersion

// :common 仍通过 rootProject.extra 读取协议版本；UI 发布版本独立于协议层依赖版本。
extra["neoLinkApiVersion"] = neoLinkApiVersion
extra["neoLinkUiVersion"] = neoLinkUiVersion

// ============================================================================
// 仓库源：根桌面应用与 :common 都需要解析同一组依赖。
// ============================================================================
allprojects {
    repositories {
        // Compose Desktop 的平台 runtime 会传递解析 Skiko native JAR。
        // 国内 Maven 镜像可能出现 POM 已同步、native artifact 未同步的短暂不一致；Gradle 一旦在某个
        // 仓库解析到 module metadata，就会把该 module 的 artifact 固定到同一个仓库，不会再回退。
        // 用 exclusiveContent 将 Compose/Skiko 绑定到官方 Compose 仓库，避免低 CPU 的重复远程探测。
        exclusiveContent {
            forRepository {
                maven("https://packages.jetbrains.team/maven/p/cmp/dev")
            }
            filter {
                includeGroup("org.jetbrains.compose")
                includeGroup("org.jetbrains.skiko")
            }
        }

        mavenLocal()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central") {
            content {
                excludeGroup("top.ceroxe.api")
            }
        }
        maven("https://maven.aliyun.com/repository/public") {
            content {
                excludeGroup("top.ceroxe.api")
            }
        }
        maven("https://repo1.maven.org/maven2")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// ============================================================================
// 依赖声明
// ============================================================================
val shadowJarWindowsRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowJarMacosRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowJarLinuxRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    // 内部模块：共享业务逻辑（config、state、node、app、util）
    implementation(project(":common"))

    // NeoLink 协议层（desktop 完整实现，非 shared）
    implementation("top.ceroxe.api:neolinkapi-desktop:$neoLinkApiVersion")
    // 基础设施层：日志、系统检测
    implementation("top.ceroxe.api:ceroxe-core:2.0.0")
    implementation("top.ceroxe.api:ceroxe-detector:2.0.0")

    // Compose Desktop 图形界面
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material:1.11.0")
    // JetBrains no longer publishes fresh Material Icons Extended artifacts for Compose 1.11.x.
    // Keep the last supported artifact explicit so the build does not silently depend on a deprecated alias.
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.compose.ui:ui:1.11.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.0")
    implementation("org.jetbrains.compose.runtime:runtime:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    // JNA — Windows DWM 特效等原生调用
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")

    // Shadow JAR 必须可在任意构建机上产出所有目标平台包。
    // compose.desktop.currentOs 只会解析当前构建机平台的 Skiko native，因此平台包在这里显式声明。
    shadowJarWindowsRuntime("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion")
    shadowJarMacosRuntime("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:$skikoVersion")
    shadowJarMacosRuntime("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skikoVersion")
    shadowJarLinuxRuntime("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion")

    // 桌面 UI 需要直接解析 NAS 响应与本地隧道 JSON；这里使用同一 Jackson 版本避免运行时漂移。
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")

    // 测试
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

// ============================================================================
// 资源处理：将 Gradle 版本号注入 app.properties
// ============================================================================
tasks.processResources {
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    filesMatching("app.properties") {
        expand("version" to appVersion)
    }
}

// ============================================================================
// 版本边界校验：UI 发布版本与 neolinkapi 依赖版本各自独立。
// 防止桌面客户端显示/产物版本被协议层依赖版本隐式牵引。
// ============================================================================
val verifyNeoLinkVersionDeclarations by tasks.registering {
    group = "verification"
    description = "Ensures UI and API versions are explicitly declared and intentionally decoupled."
    val declaredUiVersion = neoLinkUiVersion
    val declaredApiVersion = neoLinkApiVersion
    val desktopArtifactVersion = project.version.toString()

    inputs.property("neoLinkUiVersion", declaredUiVersion)
    inputs.property("neoLinkApiVersion", declaredApiVersion)
    inputs.property("desktopArtifactVersion", desktopArtifactVersion)

    doLast {
        require(declaredUiVersion.isNotBlank()) {
            "neoLinkUiVersion must be declared."
        }
        require(declaredApiVersion.isNotBlank()) {
            "neoLinkApiVersion must be declared."
        }
        require(desktopArtifactVersion == declaredUiVersion) {
            "Desktop artifact version must use neoLinkUiVersion. project.version=$desktopArtifactVersion, neoLinkUiVersion=$declaredUiVersion"
        }
        logger.lifecycle("[VersionBinding] OK: ui=$declaredUiVersion, neolinkapi=$declaredApiVersion")
    }
}

// ============================================================================
// Shadow JAR 配置
// 主入口：neoproxy.neolink.NeoLink
// Universal JAR 包含所有平台 Compose 原生库；平台 JAR 仅含对应平台库。
// ============================================================================
fun ShadowJar.configureCommonShadow() {
    archiveClassifier.set("")
    mergeServiceFiles()
    from(sourceSets.main.get().output)
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    // ShadowJar 的默认 DEFLATED 压缩会让每个平台包进入单线程二次压缩；
    // 依赖 JAR 本身大多已压缩，继续压缩对体积收益有限，却会让 shadowJarAll 串行耗时膨胀。
    entryCompression = ZipEntryCompression.STORED
    // 排除签名文件，避免 fat JAR 聚合第三方包后触发签名校验失败。
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

fun ShadowJar.excludeWindowsSkikoNative() {
    exclude("skiko-windows-*.dll", "skiko-windows-*.dll.sha256")
}

fun ShadowJar.excludeMacosSkikoNative() {
    exclude("libskiko-macos-*.dylib", "libskiko-macos-*.dylib.sha256")
}

fun ShadowJar.excludeLinuxSkikoNative() {
    exclude("libskiko-linux-*.so", "libskiko-linux-*.so.sha256")
}

tasks.named<ShadowJar>("shadowJar") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("universal")
    configurations = listOf(
        project.configurations.runtimeClasspath.get(),
        shadowJarWindowsRuntime,
        shadowJarMacosRuntime,
        shadowJarLinuxRuntime
    )
    dependsOn(verifyNeoLinkVersionDeclarations)
}

tasks.register<ShadowJar>("shadowJarWindows") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("windows")
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowJarWindowsRuntime)
    excludeMacosSkikoNative()
    excludeLinuxSkikoNative()
    dependsOn(verifyNeoLinkVersionDeclarations)
}

tasks.register<ShadowJar>("shadowJarMacos") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("macos")
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowJarMacosRuntime)
    excludeWindowsSkikoNative()
    excludeLinuxSkikoNative()
    dependsOn(verifyNeoLinkVersionDeclarations)
}

tasks.register<ShadowJar>("shadowJarLinux") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("linux")
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowJarLinuxRuntime)
    excludeWindowsSkikoNative()
    excludeMacosSkikoNative()
    dependsOn(verifyNeoLinkVersionDeclarations)
}

tasks.register("shadowJarAll") {
    group = "build"
    description = "Builds universal + all platform-specific shadow JARs."
    dependsOn("classes", verifyNeoLinkVersionDeclarations)

    val gradleWrapperPath = layout.projectDirectory.file(
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "gradlew.bat"
        } else {
            "gradlew"
        }
    ).asFile.absolutePath
    val projectPath = projectDir
    val packageTasks = listOf(":shadowJar", ":shadowJarWindows", ":shadowJarMacos", ":shadowJarLinux")
    val commonArgs = listOf("--console=plain", "--configure-on-demand", "--configuration-cache", "--offline")
    val onlineFallbackArgs = listOf("--console=plain", "--configure-on-demand", "--configuration-cache")

    doLast {
        val gradleWrapper = File(gradleWrapperPath)
        require(gradleWrapper.isFile) {
            "Gradle Wrapper not found: ${gradleWrapper.absolutePath}"
        }

        fun runPackageBuilds(args: List<String>, tasksToRun: List<String>): List<String> {
            // Gradle 不会并行同一 project 内的多个重型归档任务；这里复用已有单包 ShadowJar 任务，
            // 用兄弟 Gradle 进程并发打包，避免四个平台包墙钟时间串行累加。
            val runningBuilds = tasksToRun.associateWith { taskPath ->
                ProcessBuilder(listOf(gradleWrapper.absolutePath, taskPath) + args)
                    .directory(projectPath)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
            }

            return runningBuilds.mapNotNull { (taskPath, process) ->
                val exitCode = process.waitFor()
                taskPath.takeIf { exitCode != 0 }
            }
        }

        // 热构建应直接消费本机 Gradle artifact cache，避免四个并发子构建重复做远程 metadata 探测。
        // 如果冷缓存缺 artifact，只对失败任务在线重试，保证新环境仍可自恢复。
        val offlineFailures = runPackageBuilds(commonArgs, packageTasks)
        val failedTasks = if (offlineFailures.isEmpty()) {
            emptyList()
        } else {
            logger.lifecycle(
                "[shadowJarAll] Offline parallel packaging missed cached artifacts for: ${offlineFailures.joinToString()}. Retrying online."
            )
            runPackageBuilds(onlineFallbackArgs, packageTasks.filter { it in offlineFailures })
        }

        require(failedTasks.isEmpty()) {
            "Parallel shadow packaging failed for: ${failedTasks.joinToString()}"
        }
    }
}

// ============================================================================
// 测试配置
// ============================================================================
tasks.test {
    mustRunAfter(":common:test")
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    finalizedBy(tasks.jacocoTestReport)
}

// ============================================================================
// JaCoCo 代码覆盖率
// 最低行覆盖率 50%，GUI 包不参与统计（UI 层难以纯单元测试覆盖）。
// ============================================================================
jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude("neoproxy/neolink/gui/**")
        },
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("neoproxy/neolink/gui/**")
        }
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude("neoproxy/neolink/gui/**")
        },
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("neoproxy/neolink/gui/**")
        }
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(verifyNeoLinkVersionDeclarations)
}

