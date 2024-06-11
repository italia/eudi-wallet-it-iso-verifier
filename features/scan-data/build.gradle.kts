plugins {
    id("androidLib")
    id("hilt")
}
dependencies{
    implementation(project(":core:utils"))
    implementation(project(":core:app-scope"))
    implementation(project(":features:debug-data"))
    implementation(project(":core:network1"))
    implementation(project(":core:preferences"))
    implementation(libs.bundles.cryptography)
    implementation(libs.bundles.network)
}