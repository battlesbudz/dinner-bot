plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.battlesbudz.dinnerbot.feature.premium"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        // The RevenueCat public API key (Phase C3) is supplied at build
        // time from the REVENUECAT_API_KEY environment variable or the
        // revenuecatApiKey Gradle property, and defaults to empty. An
        // empty key is a fully supported state: billing stays
        // unconfigured, the free tier works exactly as it does without
        // billing, and purchase actions say so honestly. No key is ever
        // committed to this repository.
        val revenueCatApiKey: String = System.getenv("REVENUECAT_API_KEY")
            ?: (findProperty("revenuecatApiKey") as String?)
            ?: ""
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    // RevenueCat (Phase C3): entitlement status, offerings, purchases
    // and restores. The SDK is the source of truth for premium status;
    // the app caches the last known status on device and fails soft
    // when the store cannot be reached.
    implementation(libs.revenuecat.purchases)
    testImplementation(libs.junit)
}
