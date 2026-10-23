plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.artii.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.artii.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.7.1-alpha"
        val apiUrl = providers.gradleProperty("ARTII_API_URL").orElse(providers.environmentVariable("ARTII_API_URL")).orElse("").get()
        require(apiUrl.isEmpty() || (apiUrl.startsWith("https://") && !apiUrl.contains('"') && !apiUrl.contains('\\'))) { "ARTII_API_URL must be an HTTPS URL" }
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Release artifacts must never silently point at localhost.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { require(!providers.gradleProperty("ARTII_API_URL").orElse(providers.environmentVariable("ARTII_API_URL")).orElse("").get().isBlank()) { "Set ARTII_API_URL to the deployed HTTPS origin before building release" } }
}
