import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

private val CLOUD_PROJECT_NUMBER_PLACEHOLDER: String = "000000000000"

/**
 * Cloud Project Number used by the Play Integrity attestation during
 * `initializeRelyingPartyInstance`. Set `pv.cloudProjectNumber` in the root
 * `local.properties` (untracked) to point the sample at your own Google Cloud project;
 * the placeholder below is what ships with the repository. The value must be numeric.
 */
val cloudProjectNumber: String = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { fileText: String ->
        fileText.lineSequence()
            .map { line: String -> line.trim() }
            .firstOrNull { line: String -> line.startsWith("pv.cloudProjectNumber=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { value: String -> value.isNotEmpty() }
            ?: CLOUD_PROJECT_NUMBER_PLACEHOLDER
    }
    .getOrElse(CLOUD_PROJECT_NUMBER_PLACEHOLDER)

android {
    namespace = "it.infocert.etuitus.sampleandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.infocert.etuitus.sampleandroid"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLOUD_PROJECT_NUMBER", "\"$cloudProjectNumber\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation("androidx.navigation:navigation-compose:2.8.9")

    //implementation(libs.proximityverification)
    implementation(project(":proximityverification"))

    implementation(libs.barcode.scanning)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.multipaz)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.io.core)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)


}
