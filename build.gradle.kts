/*
* Modifications copyright (C) 2025 Wakala Srl
*/

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Qui dichiari la versione AGP, ad esempio 8.4.0
        classpath("com.android.tools.build:gradle:8.9.0")

        // Se vuoi anche Kotlin plugin:
        // classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.gretty) apply false
    alias(libs.plugins.navigation.safe.args) apply false
    alias(libs.plugins.parcelable) apply false
    alias(libs.plugins.kapt) apply false
    alias(libs.plugins.buildconfig) apply false
}
