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

rootProject.name = "FnMusic"

include(
    ":app",
    ":core:model",
    ":core:network",
    ":core:designsystem",
    ":core:player",
    ":data",
    ":feature:session",
    ":feature:music",
)
