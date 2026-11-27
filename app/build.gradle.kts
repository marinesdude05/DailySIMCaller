plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val deviceTrackingSheetId = providers.gradleProperty("DEVICE_TRACKING_SHEET_ID").orElse("").get()
require(deviceTrackingSheetId.isEmpty() || deviceTrackingSheetId.matches(Regex("[A-Za-z0-9_-]+"))) {
    "DEVICE_TRACKING_SHEET_ID must be a spreadsheet ID, not a URL"
}

val callLogSheetId = providers.gradleProperty("CALL_LOG_SHEET_ID").orElse("").get()
require(callLogSheetId.isEmpty() || callLogSheetId.matches(Regex("[A-Za-z0-9_-]+"))) {
    "CALL_LOG_SHEET_ID must be a spreadsheet ID, not a URL"
}

android {
    namespace = "com.example.dailysimcaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dailysimcaller"
        minSdk = 26
        targetSdk = 36
        versionCode = 51
        versionName = "1.3.24-v51-guided-setup-preview"
        buildConfigField("String", "DEVICE_TRACKING_SHEET_ID", "\"$deviceTrackingSheetId\"")
        buildConfigField("String", "CALL_LOG_SHEET_ID", "\"$callLogSheetId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Install alongside existing versions without affecting their records.
            applicationIdSuffix = ".weekpreview"
        }
        release {
            optimization {
                enable = false
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}