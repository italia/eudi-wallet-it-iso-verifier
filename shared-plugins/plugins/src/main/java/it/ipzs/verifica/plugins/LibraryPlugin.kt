package it.ipzs.verifica.plugins

import com.android.build.api.dsl.LibraryExtension
import it.ipzs.verifica.plugins.utils.DependencyConfiguration
import it.ipzs.verifica.plugins.utils.dependencies
import it.ipzs.verifica.plugins.utils.getPluginId
import it.ipzs.verifica.plugins.utils.kotlinOptions
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class LibraryPlugin: Plugin<Project> {

    override fun apply(target: Project) {

        val libraryPlugin = target.getPluginId("android-library")
        val kotlinPlugin = target.getPluginId("kotlin-android")

        target.pluginManager.apply {
            apply(libraryPlugin)
            apply(kotlinPlugin)
        }

        target.extensions.configure<LibraryExtension>{

            namespace = "it.ipzs.${target.name.replace("-", "_")}"

            compileSdk = 34

            defaultConfig {
                minSdk = 26

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }

            buildTypes {
                release {
                    isMinifyEnabled = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
            }
            flavorDimensions += "product"
            productFlavors {
                create("prod")
                create("qa")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions {
                jvmTarget = "17"
            }

        }

        target.dependencies(
            DependencyConfiguration.Implementation to "core-ktx",
            DependencyConfiguration.Implementation to "appcompat",
            DependencyConfiguration.Implementation to "material",
            DependencyConfiguration.Implementation to "lifecycle-viewmodel",
            DependencyConfiguration.TestImplementation to "junit",
            DependencyConfiguration.AndroidTestImplementation to "androidx-test-ext-junit",
            DependencyConfiguration.AndroidTestImplementation to "espresso-core"
        )

    }

}