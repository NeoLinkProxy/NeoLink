// ============================================================================
// NeoLink Desktop 客户端构建脚本
// 职责：以仓库根项目作为桌面端应用本体，保留 :common 作为共享业务模块。
// 这样 feature 分支可以独立演进 NeoAuthServer API 客户端，而不再受旧多端结构牵连。
// ============================================================================

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    jacoco
}

val neoLinkApiVersion = "7.2.0"
val skikoVersion = "0.7.97"

group = "neoproxy"
version = neoLinkApiVersion

// :common 仍通过 rootProject.extra 读取协议版本；这里继续暴露同一真源，避免版本漂移。
extra["neoLinkApiVersion"] = neoLinkApiVersion

// ============================================================================
// 仓库源：根桌面应用与 :common 都需要解析同一组依赖。
// ============================================================================
allprojects {
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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
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
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    // JNA — Windows DWM 特效等原生调用
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Shadow JAR 必须可在任意构建机上产出所有目标平台包。
    // compose.desktop.currentOs 只会解析当前构建机平台的 Skiko native，因此平台包在这里显式声明。
    shadowJarWindowsRuntime("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion")
    shadowJarMacosRuntime("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:$skikoVersion")
    shadowJarMacosRuntime("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skikoVersion")
    shadowJarLinuxRuntime("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion")

    // 桌面 UI 需要直接解析 NAS 响应与本地隧道 JSON；这里使用同一 Jackson 版本避免运行时漂移。
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")

    // 测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

// ============================================================================
// 资源处理：将 Gradle 版本号注入 app.properties
// ============================================================================
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("app.properties") {
        expand("version" to project.version)
    }
}

// ============================================================================
// 版本绑定校验：确保项目 version 与 neolinkapi 依赖版本严格一致。
// 防止桌面客户端发布包与协议层版本因人为修改而漂移。
// ============================================================================
val verifyNeoLinkApiVersionBinding by tasks.registering {
    group = "verification"
    description = "Ensures project version matches neolinkapi dependency version."
    doLast {
        val projectVersion = project.version.toString()
        require(projectVersion == neoLinkApiVersion) {
            "Version mismatch! project.version=$projectVersion but neoLinkApiVersion=$neoLinkApiVersion"
        }
        logger.lifecycle("[VersionBinding] OK: project=$projectVersion == neolinkapi=$neoLinkApiVersion")
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
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarWindows") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("windows")
    from(tasks.named("jar").map { it.outputs })
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowJarWindowsRuntime)
    excludeMacosSkikoNative()
    excludeLinuxSkikoNative()
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarMacos") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("macos")
    from(tasks.named("jar").map { it.outputs })
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowJarMacosRuntime)
    excludeWindowsSkikoNative()
    excludeLinuxSkikoNative()
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarLinux") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("linux")
    from(tasks.named("jar").map { it.outputs })
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowJarLinuxRuntime)
    excludeWindowsSkikoNative()
    excludeMacosSkikoNative()
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register("shadowJarAll") {
    group = "build"
    description = "Builds universal + all platform-specific shadow JARs."
    dependsOn("shadowJar", "shadowJarWindows", "shadowJarMacos", "shadowJarLinux")
}

// ============================================================================
// 测试配置
// ============================================================================
tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    finalizedBy(tasks.jacocoTestReport)
}

// ============================================================================
// JaCoCo 代码覆盖率
// 最低行覆盖率 50%，GUI 包不参与统计（UI 层难以纯单元测试覆盖）。
// ============================================================================
jacoco {
    toolVersion = "0.8.11"
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
    dependsOn(verifyNeoLinkApiVersionBinding)
}

