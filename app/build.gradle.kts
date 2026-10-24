plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.battlesbudz.dinnerbot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.battlesbudz.dinnerbot"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.4.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is read from the environment (GitHub Actions secrets in
    // the release workflow). Without the env, release builds stay unsigned so
    // unit tests and lint never need the keystore.
    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    sourceSets {
        getByName("main") {
            // The canonical seed data lives in repo-root seed-data/ and is bundled
            // as app assets, so the app and the data-seed validator read the same files.
            assets.srcDir(rootProject.file("seed-data"))
        }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-data"))
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-recipes"))
    implementation(project(":feature-planner"))
    implementation(project(":feature-grocerylist"))
    implementation(project(":feature-pricebook"))
    implementation(project(":feature-pantry"))
    implementation(project(":feature-backup"))
    implementation(project(":feature-reminders"))
    implementation(project(":feature-nutrition"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    // FileProvider for the backup share sheet (Epic issue #9).
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    // Test-only: the instrumented barcode test drives ML Kit's decoder
    // directly. Production ML Kit stays confined to feature-grocerylist.
    androidTestImplementation(libs.mlkit.barcode.scanning)
    androidTestImplementation(libs.test.uiautomator)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
