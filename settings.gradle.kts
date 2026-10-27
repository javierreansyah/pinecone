@file:Suppress("UnstableApiUsage")

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
    @Suppress("UnstableApiUsage") repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Pinecone"
include(":app")
include(":core:model")
include(":publication")
include(":data")

if (providers.gradleProperty("mupdfFromSource").map(String::toBoolean).getOrElse(false)) {
    include(":mupdf")
    project(":mupdf").projectDir = file("native/mupdf")
} else {
    apply(from = "gradle/mupdf-prebuilt.settings.gradle.kts")
}
