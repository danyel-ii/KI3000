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

rootProject.name = "KITT"

include(
    ":app",
    ":core-ui",
    ":core-llm",
    ":core-chat",
    ":core-audio",
    ":core-storage",
    ":feature-console",
    ":feature-settings",
    ":feature-transcript",
)
