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
    ":core:airplay",
    ":data",
    ":feature:session",
    ":feature:music",
)

// Host-owned adapters keep the pinned upstream submodules unchanged.
include(":accompanist-lyrics-core", ":accompanist-lyrics-ui")
project(":accompanist-lyrics-core").projectDir = file("third_party/adapters/lyrics-core")
project(":accompanist-lyrics-ui").projectDir = file("third_party/adapters/lyrics-ui")
