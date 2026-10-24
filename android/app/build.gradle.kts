import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val webhookUrl = localProps.getProperty("WEBHOOK_URL").orEmpty()
val webhookSecret = localProps.getProperty("WEBHOOK_SECRET").orEmpty()

android {
    namespace = "vn.phuong.financebridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.phuong.financebridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "WEBHOOK_URL", "\"$webhookUrl\"")
        buildConfigField("String", "WEBHOOK_SECRET", "\"$webhookSecret\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

val validateReleaseWebhookConfig = tasks.register("validateReleaseWebhookConfig") {
    doLast {
        val endpoint = runCatching { java.net.URI(webhookUrl) }.getOrNull()
        if (endpoint?.scheme != "https" || endpoint.host.isNullOrBlank()) {
            throw GradleException("Release builds require WEBHOOK_URL to use HTTPS and include a host.")
        }
        if (webhookSecret.isBlank() || webhookSecret.contains("replace-with", ignoreCase = true)) {
            throw GradleException("Release builds require a privately configured WEBHOOK_SECRET.")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name.startsWith("bundleRelease")) {
        dependsOn(validateReleaseWebhookConfig)
    }
}

