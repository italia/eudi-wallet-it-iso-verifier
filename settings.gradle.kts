/*
* Modifications copyright (C) 2025 Wakala Srl
*/

rootProject.name = "WakalaIdentity"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    // Aggiunto:
    plugins {
        // Dichiarazione esplicita di AGP 8.4.0 (adatta la versione se serve)
        id("com.android.application") version "8.9.0"
        // Se hai moduli che usano "com.android.library", puoi aggiungere anche:
        // id("com.android.library") version "8.4.0"

        // Plugin Kotlin per Android (adatta la versione se serve, es. "1.8.22" o "2.1.0")
        id("org.jetbrains.kotlin.android") version "2.1.0"
    }

}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") {
            mavenContent {
                includeGroup("com.github.yuriy-budiyev")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.9.0")
}

include(":processor")
include(":processor-annotations")
include(":identity")
include(":identity-mdoc")
include(":identity-doctypes")
include(":identity-android")
include(":jpeg2k")
include(":appverifier")
