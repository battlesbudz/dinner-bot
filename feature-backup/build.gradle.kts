plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core-model"))
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
