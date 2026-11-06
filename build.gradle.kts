// ==============================================================================
// PLAIN-ENGLISH GUIDE
// Project-level build file: lists the plugin versions (Android, Kotlin, KSP)
// shared by the app module. Usually you don't need to touch it.
// ==============================================================================
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
