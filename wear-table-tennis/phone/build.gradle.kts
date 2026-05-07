import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningProperties = Properties().apply {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        localProperties.inputStream().use(::load)
    }
}

fun releaseSigningValue(key: String): String? =
    providers.environmentVariable(key).orNull
        ?: releaseSigningProperties.getProperty(key)

val releaseStoreFile = releaseSigningValue("TT_SCORE_UPLOAD_STORE_FILE")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseSigningValue("TT_SCORE_UPLOAD_STORE_PASSWORD"),
    releaseSigningValue("TT_SCORE_UPLOAD_KEY_ALIAS"),
    releaseSigningValue("TT_SCORE_UPLOAD_KEY_PASSWORD"),
).all { !it.isNullOrBlank() } && releaseStoreFile?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.uwe.tabletennisscore.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uwe.tabletennisscore"
        minSdk = 30
        targetSdk = 35
        // Keep watch and phone version names aligned, but use separate versionCode
        // ranges so both bundles can coexist under one Play listing.
        versionCode = 20103
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseSigningValue("TT_SCORE_UPLOAD_STORE_PASSWORD")
                keyAlias = releaseSigningValue("TT_SCORE_UPLOAD_KEY_ALIAS")
                keyPassword = releaseSigningValue("TT_SCORE_UPLOAD_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.0")

    testImplementation("junit:junit:4.13.2")
}
