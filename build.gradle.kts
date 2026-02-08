import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    idea
}

group = "neoproxy"
version = "5.10.4"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/public/compose/dev")
    google()
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        val main by getting {
            kotlin.srcDirs("src/main/kotlin", "src/main/java")
            resources.srcDirs("src/main/resources")
        }
    }
}

dependencies {
    implementation("fun.ceroxe.api:ceroxe-core:0.2.7")
    implementation("fun.ceroxe.api:ceroxe-detector:0.2.7")

    // 这个你已经配对了，它包含全平台解压库
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01")

    implementation(compose.desktop.common)

    // 【核心修复】不要使用 currentOs，要显式列出所有目标平台
    // 这样 shadowJar 会把这些平台的原生动态库全部打包进去
    implementation(compose.desktop.windows_x64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.macos_arm64) // 适配 M1/M2/M3 芯片
    implementation(compose.desktop.linux_x64)

    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.runtime)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    // JNA 本身是跨平台的，会自动根据系统加载内部的 .so/.dylib/.dll
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

// 资源处理修复
tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("app.properties") {
        expand("version" to project.version)
    }
}

// ShadowJar 任务
tasks.named<ShadowJar>("shadowJar") {
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    mergeServiceFiles()
    archiveBaseName.set("NeoLink")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// 编译编码修复
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

// 运行编码修复
tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
}