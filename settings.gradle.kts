// ==============================================================================
// PLAIN-ENGLISH GUIDE
// Tells Gradle where to download libraries from (Google and Maven Central) and
// that the project has one module: "app".
// ==============================================================================
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GymNam"
include(":app")
