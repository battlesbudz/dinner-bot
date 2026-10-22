plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.battlesbudz.dinnerbot.feature.reminders"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
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
    // On-device scheduling only: WorkManager persists the requests and
    // reschedules them after a reboot. No network is involved anywhere.
    implementation(libs.work.runtime.ktx)
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
