plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val centralSyncUrl = providers.gradleProperty("CENTRAL_SYNC_URL").orElse("").get()
require(centralSyncUrl.isEmpty() ||
    centralSyncUrl.matches(Regex("https://script[.]google[.]com/macros/s/[A-Za-z0-9_-]+/exec"))) {
    "CENTRAL_SYNC_URL must be a deployed Google Apps Script web app URL"
}

android {
    namespace = "com.example.dailysimcaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dailysimcaller"
        minSdk = 26
        targetSdk = 36
        versionCode = 52
        versionName = "1.3.25-v52"
        buildConfigField("String", "CENTRAL_SYNC_URL", "\"$centralSyncUrl\"")

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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}