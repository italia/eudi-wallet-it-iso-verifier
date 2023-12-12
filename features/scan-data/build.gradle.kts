plugins {
    id("androidLib")
    id("hilt")
}
dependencies{
    implementation(project(":core:utils"))
    implementation(project(":core:app-scope"))
    implementation(project(":features:debug-data"))
    implementation(libs.bundles.cryptography)
}