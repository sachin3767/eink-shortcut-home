import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Secrets that must not be committed live in local.properties (git-ignored).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.mp01home.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mp01home.launcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Google OAuth "Web application" client id used for Google Tasks sign-in (TASK-1).
        // Set GOOGLE_OAUTH_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com in local.properties.
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_OAUTH_WEB_CLIENT_ID", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    // Local cache (EINK-7)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background sync (EINK-6)
    implementation(libs.androidx.work.runtime.ktx)

    // Secure token storage + Google sign-in for Tasks (TASK-1)
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.services.auth)

    // JSON (settings/layout backup, CORE-7; Tasks REST payloads)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
