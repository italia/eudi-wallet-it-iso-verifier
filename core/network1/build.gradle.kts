plugins {
    id("androidLib")
    id("hilt")
    id("ksp")
}
dependencies {
    implementation(libs.bundles.network)
    implementation(libs.bundles.cryptography)
    implementation(project(":core:preferences"))
}