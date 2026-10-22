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
        versionCode = 1
        versionName = "0.1.0-phase1"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
