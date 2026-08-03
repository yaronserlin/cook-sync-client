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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        // Serves the shared cooksync-DTOs repository (github.com/yaronserlin/cooksync-DTOs)
        // as a Maven artifact, built directly from its git tags/branches. Both this client
        // and the cook-sync-server depend on the exact same DTO sources through this.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CookSync"
include(":app")
