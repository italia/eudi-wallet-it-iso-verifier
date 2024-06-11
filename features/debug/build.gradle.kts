plugins {
    id("androidLib")
    id("composeLib")
    id("hilt")
    id("ksp")
}

dependencies {
    implementation(project(":core:theme"))
    implementation(project(":core:architecture"))
    implementation(project(":core:utils"))
    implementation(project(":features:debug-data"))
    implementation(libs.destination.builder)
    ksp(libs.destination.builder)
}