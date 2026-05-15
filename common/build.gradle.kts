plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

dependencies {
    // 仅依赖 shared 协议模型（NeoLinkCfg、NeoNode、异常类等），不依赖 desktop 实现
    api("top.ceroxe.api:neolinkapi-shared:${rootProject.extra["neoLinkApiVersion"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")

    // 测试
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}
