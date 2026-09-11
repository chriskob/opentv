pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "OpenTV"
include(":app")

/*
 * The baseline-profile module: it drives the app on a device or emulator, records which methods it
 * actually runs, and leaves a profile behind for the release build to carry. Not part of any APK.
 */
include(":baselineprofile")
