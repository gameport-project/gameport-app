pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "GamePort"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:steam")
include(":feature:auth")
include(":feature:library")
include(":feature:game")
include(":core:install")
include(":core:patch")
include(":core:device")
include(":feature:downloads")
include(":feature:compat")
include(":core:settings")
include(":core:sync")
include(":feature:settings")
include(":hook")
include(":feature:sync")
