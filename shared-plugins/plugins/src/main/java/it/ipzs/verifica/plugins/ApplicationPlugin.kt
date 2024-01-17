package it.ipzs.verifica.plugins

import com.android.build.api.dsl.ApplicationExtension
import it.ipzs.verifica.plugins.utils.DependencyConfiguration
import it.ipzs.verifica.plugins.utils.dependencies
import it.ipzs.verifica.plugins.utils.getFlavorName
import it.ipzs.verifica.plugins.utils.getPluginId
import it.ipzs.verifica.plugins.utils.kotlinOptions
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.currentBuildId

private const val FLAVOR_DIMENSION = "product"
private const val FLAVOR_PROD = "prod"
private const val FLAVOR_QA = "qa"

class ApplicationPlugin : Plugin<Project>{
    override fun apply(target: Project) {

        val applicationPlugin = target.getPluginId("android-application")
        val kotlinPlugin = target.getPluginId("kotlin-android")

        target.pluginManager.apply {
            apply(applicationPlugin)
            apply(kotlinPlugin)
        }

        target.extensions.configure<ApplicationExtension> {

            apply {

                namespace = "it.ipzs.verifica"
                compileSdk = 34

                defaultConfig {
                    applicationId = "it.ipzs.verifica"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 5
                    versionName = "1.5"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = target.getFlavorName() == FLAVOR_PROD
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }
                flavorDimensions += FLAVOR_DIMENSION
                productFlavors {
                    create(FLAVOR_PROD) {
                        dimension = FLAVOR_DIMENSION
                        applicationIdSuffix = ".$FLAVOR_PROD"
                        versionNameSuffix = "-$FLAVOR_PROD"
                    }
                    create("qa") {
                        dimension = FLAVOR_DIMENSION
                        applicationIdSuffix = ".$FLAVOR_QA"
                        versionNameSuffix = "-$FLAVOR_QA"
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions {
                    jvmTarget = "17"
                }

            }

        }

        target.dependencies(
            DependencyConfiguration.Implementation to "core-ktx",
            DependencyConfiguration.Implementation to "appcompat",
            DependencyConfiguration.Implementation to "material",
            DependencyConfiguration.TestImplementation to "junit",
            DependencyConfiguration.AndroidTestImplementation to "androidx-test-ext-junit",
            DependencyConfiguration.AndroidTestImplementation to "espresso-core"
        )
    }
}