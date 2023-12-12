plugins {
    id("androidLib")
    id("composeLib")
    id("hilt")
    id("ksp")
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
    implementation(libs.destination.builder)
    ksp(libs.destination.builder)
}