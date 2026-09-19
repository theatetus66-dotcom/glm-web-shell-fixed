enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

rootProject.name = "glm-web-shell"

include(":app")

include(":core:common")
include(":core:ui")
include(":core:data")

include(":pageengine")

include(":adapter")

include(":features:chat")
include(":features:diagnostics")
include(":features:history")
include(":features:prompts")
include(":features:settings")
