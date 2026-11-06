// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the build recipe for the app (Gradle = the tool Android
// Studio uses to turn the code into an installable app).
//  - android { }: app id, minimum Android version (minSdk 29 = Android 10),
//    version number shown to users (versionName) and the internal counter
//    (versionCode, must go up with every release).
//  - release { }: shrink and optimize the final app (smaller download).
//  - dependencies { }: the outside libraries the app uses, with exact versions.
//    "implementation(...)" = include this library. "ksp(...)" = a code generator
//    that writes the database code for Room automatically.
// ==============================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gymnam.app"
    // compileSdk = which Android version's tools we build WITH (36 = Android 16).
    // Health Connect 1.1.0 requires 36. This does NOT change which phones can install
    // the app (that's minSdk = 29, Android 10+) or how the app behaves (targetSdk).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gymnam.app"
        minSdk = 29          // Android 10+
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas") // keeps DB migrations safe for later stages
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Barcode scanning: CameraX + ZXing (on-device, open source, no Google services)
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.zxing:core:3.5.3")
    // CameraX and Glance hand back a "ListenableFuture" (a value that arrives later).
    // That class comes from Google's Guava library, which must be listed here so the
    // compiler can see it. ("-android" = the Android-sized version of Guava.)
    implementation("com.google.guava:guava:33.3.1-android")

    // Home-screen widget (Jetpack Glance) and weekly auto-backup (WorkManager)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Optional app lock (fingerprint / face / phone PIN) and optional Health Connect
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.health.connect:connect-client:1.1.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
