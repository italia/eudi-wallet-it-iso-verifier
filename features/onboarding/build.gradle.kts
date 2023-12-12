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
    implementation(libs.destination.builder)
    ksp(libs.destination.builder)
}