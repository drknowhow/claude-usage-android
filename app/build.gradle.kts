plugins {
    // Kotlin comes from AGP 9 itself; do not add org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val baseVersion = "0.1.0"

fun git(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args) }
        .standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
}.getOrNull()

// Stamped from git so the build on the phone is identifiable from Settings.
// On a feature branch the count can exceed master's: install with `adb install -r -d`.
val gitCommitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitShortSha = git("rev-parse", "--short=7", "HEAD") ?: "nogit"

android {
    namespace = "com.yepgent.usage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yepgent.usage"
        minSdk = 29
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = "$baseVersion+$gitCommitCount.g$gitShortSha"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
