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
    namespace = "com.uwe.tabletennisscore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uwe.tabletennisscore"
        minSdk = 30
        targetSdk = 35
        // Keep watch and phone version names aligned, but use separate versionCode
        // ranges so both bundles can coexist under one Play listing.
        versionCode = 30104
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
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.wear:wear-input:1.2.0")
    implementation("androidx.wear.compose:compose-foundation:1.6.0")
    implementation("androidx.wear.compose:compose-material3:1.6.0")
    compileOnly("androidx.wear:wear-tooling-preview:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.0")
    androidTestImplementation("androidx.wear:wear-input-testing:1.2.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
}
