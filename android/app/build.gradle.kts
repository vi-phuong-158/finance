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

val defaultWebhookUrl = "https://finance-rust-nine-16.vercel.app/api/transaction"
val webhookUrl = providers.environmentVariable("WEBHOOK_URL").orNull
    ?: localProps.getProperty("WEBHOOK_URL")
    ?: defaultWebhookUrl

fun javaLiteral(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\""

fun validWebhookEndpoint(endpoint: String): Boolean {
    val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
    return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        && uri.rawQuery == null && uri.rawFragment == null && uri.path == "/api/transaction"
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
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

android.sourceSets.getByName("test").resources.srcDir("../../shared")

val validateReleaseWebhookConfig = tasks.register("validateReleaseWebhookConfig") {
    doLast {
        if (!validWebhookEndpoint(webhookUrl)) {
            throw GradleException("Release builds require a valid HTTPS /api/transaction endpoint.")
        }
    }
}

tasks.register("verifyWebhookConfigurationPolicy") {
    doLast {
        val endpoint = "https://finance.example.invalid/api/transaction"
        check(validWebhookEndpoint(endpoint))
        listOf(
            "",
            endpoint.replace("https", "http"),
            "https://user:pass@finance.example.invalid/api/transaction",
            "$endpoint?secret=x",
            "$endpoint#fragment",
            "https://finance.example.invalid/api/other",
        ).forEach { url -> check(!validWebhookEndpoint(url)) }

        val buildConfigFile = project.layout.buildDirectory.file("generated/source/buildConfig/debug/vn/phuong/financebridge/BuildConfig.java").get().asFile
        if (buildConfigFile.exists()) {
            val content = buildConfigFile.readText()
            check(!content.contains("WEBHOOK_SECRET")) {
                "SECURITY VIOLATION: BuildConfig contains WEBHOOK_SECRET"
            }
        }
        println("Webhook build policy: 7 checks passed")
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name.startsWith("bundleRelease")) {
        dependsOn(validateReleaseWebhookConfig)
    }
}
