pluginManagement {
    repositories {
        google { content {
            includeGroupByRegex("com\\.android.*")
            includeGroupByRegex("androidx.*")
            includeGroupByRegex("com\\.google\\.testing.*")
            includeGroup("com.google.android.play")
            includeGroup("com.google.android.gms")
        } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { content {
            includeGroupByRegex("com\\.android.*")
            includeGroupByRegex("androidx.*")
            includeGroupByRegex("com\\.google\\.testing.*")
            includeGroup("com.google.android.play")
            includeGroup("com.google.android.gms")
        } }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "DOUPAD"
include(":app")
