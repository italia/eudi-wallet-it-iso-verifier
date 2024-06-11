@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("androidApp")
    id("composeApp")
    id("hilt")
    id("ksp")
}
dependencies {
    implementation(project(":core:theme"))
    implementation(project(":core:architecture"))
    implementation(project(":core:preferences"))
    implementation(project(":core:utils"))
    implementation(project(":features:onboarding"))
    implementation(project(":features:home"))
    implementation(project(":features:settings"))
    implementation(project(":features:scan"))
    implementation(project(":features:debug"))
    implementation(libs.accompanist.permissions)
    implementation(libs.destination.builder)
    ksp(libs.destination.builder)
}
true