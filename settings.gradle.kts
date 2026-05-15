pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://repo1.maven.org/maven2")
    }
}

rootProject.name = "NeoLink"

include(":common")
