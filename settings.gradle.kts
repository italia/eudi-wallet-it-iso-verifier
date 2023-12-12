pluginManagement {
    includeBuild("shared-plugins")
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
        maven { url = uri("https://jitpack.io") }
    }
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

rootProject.name = "IT-Wallet-Verifica"
include(":app")
include(":core:architecture")
include(":core:theme")
include(":features:onboarding")
include(":core:utils")
include(":core:preferences")
include(":features:home")
include(":features:settings")
include(":features:scan")
include(":features:scan-data")
include(":core:database")
include(":core:app-scope")
include(":features:debug")
include(":features:debug-data")
