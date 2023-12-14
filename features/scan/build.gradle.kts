plugins {
    id("androidLib")
    id("composeLib")
    id("hilt")
}
dependencies {
    implementation(project(":core:utils"))
    implementation(project(":core:theme"))
    implementation(project(":core:architecture"))
    implementation(project(":core:preferences"))
    implementation(project(":features:scan-data"))
    implementation(libs.coil)
    implementation(libs.compose.constraintlayout)
    implementation(libs.bundles.camera)
    implementation(libs.barcode)
}