plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun localProperty(name: String): String? = providers.gradleProperty(name).orNull
fun envProperty(name: String): String? = providers.environmentVariable(name).orNull

val releaseStoreFilePath = localProperty("NEOLINK_ANDROID_KEYSTORE") ?: envProperty("NEOLINK_ANDROID_KEYSTORE")
val releaseStorePassword = localProperty("NEOLINK_ANDROID_KEYSTORE_PASSWORD") ?: envProperty("NEOLINK_ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = localProperty("NEOLINK_ANDROID_KEY_ALIAS") ?: envProperty("NEOLINK_ANDROID_KEY_ALIAS")
val releaseKeyPassword = localProperty("NEOLINK_ANDROID_KEY_PASSWORD") ?: envProperty("NEOLINK_ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// Release signing is intentionally configured through Gradle properties or environment variables:
// NEOLINK_ANDROID_KEYSTORE, NEOLINK_ANDROID_KEYSTORE_PASSWORD, NEOLINK_ANDROID_KEY_ALIAS,
// NEOLINK_ANDROID_KEY_PASSWORD. Do not commit keystores or passwords into the repository.

android {
    namespace = "neoproxy.neolink.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "neoproxy.neolink.android"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = rootProject.extra["neoLinkApiVersion"] as String
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    implementation(project(":common")) {
        exclude(group = "top.ceroxe.api", module = "neolinkapi-shared")
    }
    implementation("top.ceroxe.api:neolinkapi-android:${rootProject.extra["neoLinkApiVersion"]}")

    // Jetpack Compose BOM - 统一 Compose 库版本
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AndroidX 核心
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // 测试
    testImplementation("junit:junit:4.13.2")
}
