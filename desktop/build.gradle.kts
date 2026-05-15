// ============================================================================
// NeoLink Desktop Module — 桌面端（CLI + Compose GUI）构建脚本
// 职责：编译桌面专属代码、生成跨平台 shadow JAR、JaCoCo 覆盖率强校验
// ============================================================================

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.gradleup.shadow")
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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// ============================================================================
// Shadow Runtime Classpaths
// ============================================================================

fun createShadowRuntimeClasspath(name: String): Configuration =
    configurations.create(name) {
        isCanBeConsumed = false
        isCanBeResolved = true

        // Shadow 打包必须从生产运行时依赖出发，而不能继承 test/runtimeOnly 以外的临时依赖；
        // 同时不使用 compose.desktop.currentOs，避免在 Windows 上构建出的 universal 只携带 Windows 原生库。
        extendsFrom(
            configurations.implementation.get(),
            configurations.runtimeOnly.get()
        )

        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
    }

val universalShadowRuntimeClasspath = createShadowRuntimeClasspath("universalShadowRuntimeClasspath")
val windowsShadowRuntimeClasspath = createShadowRuntimeClasspath("windowsShadowRuntimeClasspath")
val macosShadowRuntimeClasspath = createShadowRuntimeClasspath("macosShadowRuntimeClasspath")
val linuxShadowRuntimeClasspath = createShadowRuntimeClasspath("linuxShadowRuntimeClasspath")

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
    implementation("org.jetbrains.compose.desktop:desktop:1.11.0")
    implementation("org.jetbrains.compose.material:material:1.11.0")
    // Compose 1.11 no longer publishes a fresh Material Icons Extended artifact.
    // Keep the last supported icon artifact explicit instead of relying on the deprecated alias.
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.compose.ui:ui:1.11.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.0")
    implementation("org.jetbrains.compose.runtime:runtime:1.11.0")

    // Shadow JAR 的平台原生库边界在独立 classpath 中声明：
    // universal 明确包含四套 Compose Desktop runtime；平台包只解析自己的 runtime。
    add(universalShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.11.0")
    add(universalShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:1.11.0")
    add(universalShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.11.0")
    add(universalShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.11.0")
    add(windowsShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.11.0")
    add(macosShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:1.11.0")
    add(macosShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.11.0")
    add(linuxShadowRuntimeClasspath.name, "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    // JNA — Windows DWM 特效等原生调用
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")

    // Jackson 运行时：common 模块 api() 暴露了 jackson-databind，
    // 但 shadow JAR 需要确保类路径完整
    runtimeOnly("com.fasterxml.jackson.core:jackson-databind:2.21.3")

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
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    // 排除签名文件，防止 JAR 签名校验失败
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

fun ShadowJar.excludeNonWindowsJnaNative() {
    exclude(
        "com/sun/jna/aix-*/**",
        "com/sun/jna/darwin-*/**",
        "com/sun/jna/dragonflybsd-*/**",
        "com/sun/jna/freebsd-*/**",
        "com/sun/jna/linux-*/**",
        "com/sun/jna/openbsd-*/**",
        "com/sun/jna/sunos-*/**",
        "com/sun/jna/win32-aarch64/**",
        "com/sun/jna/win32-x86/**"
    )
}

fun ShadowJar.excludeNonMacosJnaNative() {
    exclude(
        "com/sun/jna/aix-*/**",
        "com/sun/jna/dragonflybsd-*/**",
        "com/sun/jna/freebsd-*/**",
        "com/sun/jna/linux-*/**",
        "com/sun/jna/openbsd-*/**",
        "com/sun/jna/sunos-*/**",
        "com/sun/jna/win32-*/**"
    )
}

fun ShadowJar.excludeNonLinuxJnaNative() {
    exclude(
        "com/sun/jna/aix-*/**",
        "com/sun/jna/darwin-*/**",
        "com/sun/jna/dragonflybsd-*/**",
        "com/sun/jna/freebsd-*/**",
        "com/sun/jna/linux-aarch64/**",
        "com/sun/jna/linux-arm/**",
        "com/sun/jna/linux-armel/**",
        "com/sun/jna/linux-loongarch64/**",
        "com/sun/jna/linux-mips64el/**",
        "com/sun/jna/linux-ppc/**",
        "com/sun/jna/linux-ppc64le/**",
        "com/sun/jna/linux-riscv64/**",
        "com/sun/jna/linux-s390x/**",
        "com/sun/jna/linux-x86/**",
        "com/sun/jna/openbsd-*/**",
        "com/sun/jna/sunos-*/**",
        "com/sun/jna/win32-*/**"
    )
}

tasks.named<ShadowJar>("shadowJar") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("universal")
    configurations = listOf(universalShadowRuntimeClasspath)
    dependsOn(verifyNeoLinkApiVersionBinding)
}

// --- 平台特定 Shadow JAR ---

tasks.register<ShadowJar>("shadowJarWindows") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("windows")
    from(sourceSets.main.get().output)
    configurations = listOf(windowsShadowRuntimeClasspath)
    excludeNonWindowsJnaNative()
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarMacos") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("macos")
    from(sourceSets.main.get().output)
    configurations = listOf(macosShadowRuntimeClasspath)
    excludeNonMacosJnaNative()
    dependsOn(verifyNeoLinkApiVersionBinding)
}

tasks.register<ShadowJar>("shadowJarLinux") {
    configureCommonShadow()
    archiveBaseName.set("NeoLink")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("linux")
    from(sourceSets.main.get().output)
    configurations = listOf(linuxShadowRuntimeClasspath)
    excludeNonLinuxJnaNative()
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
    dependsOn(verifyNeoLinkApiVersionBinding)
}
