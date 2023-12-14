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
}