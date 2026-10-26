plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.battlesbudz.dinnerbot.feature.barcode"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
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
}

dependencies {
    implementation(project(":core-model"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    // Barcode scanning, shared by the shopping list and the
    // add-ingredient form (v0.6.3): ML Kit's bundled model decodes
    // on-device; CameraX is confined to this module with it.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    testImplementation(libs.junit)
}
