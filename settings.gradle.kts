pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        maven("https://repo1.maven.org/maven2")
    }
}

rootProject.name = "NeoLink"

include(":common")
