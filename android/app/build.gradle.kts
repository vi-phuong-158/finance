import java.util.Properties
import java.net.URI

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

val webhookUrl = providers.environmentVariable("WEBHOOK_URL").orNull ?: localProps.getProperty("WEBHOOK_URL").orEmpty()
val webhookSecret = providers.environmentVariable("WEBHOOK_SECRET").orNull ?: localProps.getProperty("WEBHOOK_SECRET").orEmpty()
fun javaLiteral(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\""
fun validWebhookConfiguration(endpoint: String, secret: String): Boolean {
    val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
    return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        && uri.rawQuery == null && uri.rawFragment == null && uri.path == "/api/transaction"
        && Regex("[A-Za-z0-9_-]{32,128}").matches(secret)
        && !Regex("replace|placeholder|change-?me|default|your[-_]|example|test[-_]|sample|mat-khau", RegexOption.IGNORE_CASE).containsMatchIn(secret)
}

android {
    namespace = "vn.phuong.financebridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.phuong.financebridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "WEBHOOK_URL", javaLiteral(webhookUrl))
        buildConfigField("String", "WEBHOOK_SECRET", javaLiteral(webhookSecret))
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
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

android.sourceSets.getByName("test").resources.srcDir("../../shared")

val validateReleaseWebhookConfig = tasks.register("validateReleaseWebhookConfig") {
    doLast {
        if (!validWebhookConfiguration(webhookUrl, webhookSecret)) {
            throw GradleException("Release builds require a private HTTPS /api/transaction endpoint and a non-placeholder URL-safe secret of 32-128 characters.")
        }
    }
}

tasks.register("verifyWebhookConfigurationPolicy") {
    doLast {
        val endpoint = "https://finance.example.invalid/api/transaction"
        val syntheticSecret = "a".repeat(64)
        check(validWebhookConfiguration(endpoint, syntheticSecret))
        listOf("" to syntheticSecret, endpoint.replace("https", "http") to syntheticSecret,
            endpoint to "", endpoint to "default".repeat(8), endpoint to "placeholder".repeat(5),
            "$endpoint?secret=x" to syntheticSecret).forEach { (url, secret) -> check(!validWebhookConfiguration(url, secret)) }
        println("Webhook build policy: 7 checks passed")
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name.startsWith("bundleRelease")) {
        dependsOn(validateReleaseWebhookConfig)
    }
}

