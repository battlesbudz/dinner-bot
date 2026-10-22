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
rootProject.name = "dinner-bot"
include(":app")
include(":core-model")
include(":core-data")
include(":data-seed")
include(":feature-onboarding")
include(":feature-recipes")
include(":feature-pricebook")
include(":feature-pantry")
include(":feature-planner")
include(":feature-grocerylist")
include(":feature-backup")
