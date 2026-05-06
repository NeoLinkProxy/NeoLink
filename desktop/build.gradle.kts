// ============================================================================
// NeoLink Desktop Module — 桌面端（CLI + Compose GUI）构建脚本
// 职责：编译桌面专属代码、生成跨平台 shadow JAR、JaCoCo 覆盖率强校验
// ============================================================================

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("com.github.johnrengelman.shadow")
    jacoco
}

val neoLinkApiVersion: String = rootProject.extra["neoLinkApiVersion"] as String

group = rootProject.group
version = rootProject.version

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
dependencies {
    // 内部模块：共享业务逻辑（config、state、node、app、util）
    implementation(project(":common"))

    // NeoLink 协议层（desktop 完整实现，非 shared）
    implementation("top.ceroxe.api:neolinkapi-desktop:$neoLinkApiVersion")
    // 基础设施层：日志、系统检测
    implementation("top.ceroxe.api:ceroxe-core:2.0.0")
    implementation("top.ceroxe.api:ceroxe-detector:2.0.0")

    // Compose Desktop GUI
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    // JNA — Windows DWM 特效等原生调用
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Jackson 运行时：common 模块 api() 暴露了 jackson-databind，
    // 但 shadow JAR 需要确保类路径完整
    runtimeOnly("com.fasterxml.jackson.core:jackson-databind:2.21.2")

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
// 版本绑定校验：确保项目 version 与 neolinkapi 依赖版本严格一致
// 防止人为疏忽导致发布包与协议层版本漂移
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
// Universal JAR 包含所有平台 Compose 原生库；平台 JAR 仅含对应平台库
// ============================================================================

// 公共 shadow 配置抽取，避免重复
fun ShadowJar.configureCommonShadow() {
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    // 排除签名文件，防止 JAR 签名校验失败
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named<ShadowJar>("shadowJar") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("universal")
    dependsOn(verifyNeoLinkApiVersionBinding)
}

// --- 平台特定 Shadow JAR ---

tasks.register<ShadowJar>("shadowJarWindows") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("windows-x64")
    from(tasks.named("jar").map { it.outputs })
    configurations = listOf(project.configurations.runtimeClasspath.get())
    // 排除非 Windows 平台的 Skiko 原生库
    exclude("**/skiko-macos-*.jar")
    exclude("**/skiko-linux-*.jar")
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarMacos") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("macos")
    from(tasks.named("jar").map { it.outputs })
    configurations = listOf(project.configurations.runtimeClasspath.get())
    exclude("**/skiko-windows-*.jar")
    exclude("**/skiko-linux-*.jar")
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarLinux") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("linux-x64")
    from(tasks.named("jar").map { it.outputs })
    configurations = listOf(project.configurations.runtimeClasspath.get())
    exclude("**/skiko-windows-*.jar")
    exclude("**/skiko-macos-*.jar")
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
// 最低行覆盖率 50%，GUI 包不参与统计（UI 层难以纯单元测试覆盖）
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
