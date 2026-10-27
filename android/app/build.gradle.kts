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

// Release signing comes only from the environment (CI secrets or the owner's shell), never from the repo.
// A stable key is what lets Android install a new APK over the old one without wiping the queued data.
val releaseSigningEnv = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }

// Every distributed build needs a higher versionCode than the installed one, or Android refuses the update.
val appVersionCode = providers.gradleProperty("versionCode").orNull?.let {
    it.toIntOrNull()?.takeIf { code -> code in 1..2_100_000_000 } ?: throw GradleException("versionCode must be an integer in 1..2100000000.")
} ?: 1

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
        versionCode = appVersionCode
        versionName = "1.0 ($appVersionCode)"

        buildConfigField("String", "WEBHOOK_URL", javaLiteral(webhookUrl))
    }

    signingConfigs {
        create("release") {
            releaseSigningEnv["ANDROID_KEYSTORE_PATH"]?.let { storeFile = file(it) }
            storePassword = releaseSigningEnv["ANDROID_KEYSTORE_PASSWORD"]
            keyAlias = releaseSigningEnv["ANDROID_KEY_ALIAS"]
            keyPassword = releaseSigningEnv["ANDROID_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
    // Test-only: runs the real SQLite queue (schema upgrades, atomic edit replacement) on the JVM.
    testImplementation("org.robolectric:robolectric:4.12.2")
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

        listOf("debug", "release").forEach { variant ->
            val buildConfigFile = project.layout.buildDirectory.file("generated/source/buildConfig/$variant/vn/phuong/financebridge/BuildConfig.java").get().asFile
            if (buildConfigFile.exists()) {
                val content = buildConfigFile.readText()
                check(!content.contains("WEBHOOK_SECRET")) {
                    "SECURITY VIOLATION: BuildConfig contains WEBHOOK_SECRET"
                }
            }
        }
        println("Webhook build policy: 7 checks passed")
    }
}

// Fails before any release packaging instead of letting AGP emit an unsigned or wrongly signed APK.
// Only names are reported; values never reach the log.
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        val missing = releaseSigningEnv.filterValues { it == null }.keys
        if (missing.isNotEmpty()) throw GradleException("Release signing requires ${missing.joinToString()}.")
        if (!file(releaseSigningEnv.getValue("ANDROID_KEYSTORE_PATH")!!).isFile) throw GradleException("ANDROID_KEYSTORE_PATH does not point to a file.")
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name.startsWith("bundleRelease")) {
        dependsOn(validateReleaseWebhookConfig, validateReleaseSigning)
    }
}
