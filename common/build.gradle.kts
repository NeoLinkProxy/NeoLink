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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")

    // 测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
