import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.kotlinSerialization)
    id("maven-publish")
}

private val branchEnvironmentVariableNames: List<String> = listOf(
    "PV_BUILD_BRANCH",
    "CI_COMMIT_REF_NAME",
    "CI_COMMIT_BRANCH",
    "GIT_BRANCH",
    "BRANCH_NAME",
)

private val shortShaEnvironmentVariableNames: List<String> = listOf(
    "PV_BUILD_SHORT_SHA",
    "CI_COMMIT_SHORT_SHA",
    "GIT_SHORT_SHA",
    "SHORT_SHA",
)

fun ProviderFactory.readFirstEnvironmentValue(variableNames: List<String>): String? {
    return variableNames
        .asSequence()
        .mapNotNull { variableName: String ->
            environmentVariable(variableName).orNull
                ?.trim()
                ?.takeIf { variableValue: String -> variableValue.isNotEmpty() }
        }
        .firstOrNull()
}

fun ProviderFactory.createGitValueProvider(commandArguments: List<String>): Provider<String?> {
    return exec {
            commandLine(commandArguments)
            isIgnoreExitValue = true
        }
        .standardOutput
        .asText
        .map { outputValue: String ->
            outputValue.trim().takeIf { trimmedOutputValue: String -> trimmedOutputValue.isNotEmpty() }
        }
}

fun ProviderFactory.readGitValue(commandArguments: List<String>): String? {
    return try {
        createGitValueProvider(commandArguments).get()
    } catch (err: Exception) {
        null
    }
}

fun ProviderFactory.resolveCurrentBranchName(): String? {
    val environmentBranchName: String? = readFirstEnvironmentValue(branchEnvironmentVariableNames)
    if (environmentBranchName != null) {
        return environmentBranchName
    }
    return readGitValue(
        commandArguments = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
    )
}

fun ProviderFactory.resolveCurrentShortSha(): String {
    val environmentShortSha: String? = readFirstEnvironmentValue(shortShaEnvironmentVariableNames)
    if (environmentShortSha != null) {
        return environmentShortSha
    }
    return readGitValue(
        commandArguments = listOf("git", "rev-parse", "--short", "HEAD"),
    ) ?: throw GradleException(
        "Unable to resolve Git short SHA. Set one of ${shortShaEnvironmentVariableNames.joinToString()} or run the build from a Git checkout.",
    )
}

fun normalizeBranchName(branchName: String?): String? {
    return branchName
        ?.trim()
        ?.removePrefix("refs/heads/")
        ?.removePrefix("origin/")
        ?.takeIf { normalizedBranchName: String -> normalizedBranchName.isNotEmpty() }
}

fun resolveArtifactVersion(baseVersion: String, branchName: String?, shortShaProvider: () -> String): String {
    val normalizedBranchName: String? = normalizeBranchName(branchName)
    return when (normalizedBranchName) {
        "main" -> baseVersion
        "develop" -> "$baseVersion-dev"
        else -> "$baseVersion-${shortShaProvider()}"
    }
}

val baseVersion: String = "0.6.9"
val resolvedBranchName: String? = providers.resolveCurrentBranchName()
val resolvedVersion: String = resolveArtifactVersion(
    baseVersion = baseVersion,
    branchName = resolvedBranchName,
    shortShaProvider = { providers.resolveCurrentShortSha() },
)

group = "it.infocert.proximityverification"
version = resolvedVersion

tasks.register("printResolvedVersion") {
    val taskResolvedVersion: String = resolvedVersion
    group = "versioning"
    description = "Prints the branch-aware project version."
    doLast {
        println(taskResolvedVersion)
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        version = resolvedVersion
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants(
            "release"
        )
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "proximityverification"
            isStatic = true
        }
    }

    targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class).all {
        binaries.all {
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.multipaz)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)

            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.cbor)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.signum)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation("androidx.startup:startup-runtime:1.2.0")
            implementation ("com.google.android.play:integrity:1.4.0")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    cocoapods {
        version = baseVersion
        summary = "Podspec for ProximityVerification"
        homepage = "ProximityVerification"
        ios.deploymentTarget = "16.0"
    }

}

android {
    namespace = "it.infocert.proximityverification"
    compileSdk = 35
    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(libs.androidx.annotation.jvm)
}
