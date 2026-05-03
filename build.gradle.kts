import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    jacoco
}

group = "neoproxy"

val neoLinkApiVersion = "7.1.6"

version = neoLinkApiVersion

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/public/compose/dev") {
        content {
            includeGroup("org.jetbrains.compose")
            includeGroup("org.jetbrains.skiko")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("top.ceroxe.api:neolinkapi:$neoLinkApiVersion")
    implementation("top.ceroxe.api:ceroxe-core:2.0.0")
    implementation("top.ceroxe.api:ceroxe-detector:2.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation(compose.desktop.common)
    implementation(compose.desktop.windows_x64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.macos_arm64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

val verifyNeoLinkApiVersionBinding by tasks.registering {
    inputs.property("neoLinkApiVersion", neoLinkApiVersion)
    doLast {
        if (project.version.toString() != neoLinkApiVersion) {
            throw GradleException(
                "NeoLink version ${project.version} must match NeoLinkAPI version $neoLinkApiVersion"
            )
        }
    }
}

tasks.withType<ProcessResources> {
    dependsOn(verifyNeoLinkApiVersionBinding)
    inputs.property("appVersion", project.version.toString())
    filteringCharset = "UTF-8"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching("app.properties") {
        expand(
            "version" to project.version.toString()
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    mergeServiceFiles()
    archiveBaseName.set("NeoLink-universal")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
    jvmArgs("-Dfile.encoding=UTF-8")
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "neoproxy/neolink/gui/**",
                    "neoproxy/neolink/**/ComposeEntry*.class"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(verifyNeoLinkApiVersionBinding)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register<ShadowJar>("shadowJarWindows") {
    group = "build"
    description = "Creates a Shadow JAR for Windows platform only"
    
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    mergeServiceFiles()
    archiveBaseName.set("NeoLink-windows")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    
    from(sourceSets.main.get().output)
    configurations.add(project.configurations.runtimeClasspath.get())
    
    dependencies {
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.97"))
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.7.97"))
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.7.97"))
    }
}

tasks.register<ShadowJar>("shadowJarMacos") {
    group = "build"
    description = "Creates a Shadow JAR for macOS platform only"
    
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    mergeServiceFiles()
    archiveBaseName.set("NeoLink-macos")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    
    from(sourceSets.main.get().output)
    configurations.add(project.configurations.runtimeClasspath.get())
    
    dependencies {
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.97"))
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.7.97"))
    }
}

tasks.register<ShadowJar>("shadowJarLinux") {
    group = "build"
    description = "Creates a Shadow JAR for Linux platform only"
    
    manifest {
        attributes["Main-Class"] = "neoproxy.neolink.NeoLink"
    }
    mergeServiceFiles()
    archiveBaseName.set("NeoLink-linux")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    
    from(sourceSets.main.get().output)
    configurations.add(project.configurations.runtimeClasspath.get())
    
    dependencies {
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.7.97"))
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.7.97"))
        exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.7.97"))
    }
}

tasks.register("shadowJarAll") {
    group = "build"
    description = "Creates all platform-specific Shadow JARs plus the universal JAR"
    
    dependsOn(tasks.named("shadowJar"))
    dependsOn(tasks.named("shadowJarWindows"))
    dependsOn(tasks.named("shadowJarMacos"))
    dependsOn(tasks.named("shadowJarLinux"))
}
