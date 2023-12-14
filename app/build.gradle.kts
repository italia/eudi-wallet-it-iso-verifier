@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("androidApp")
    id("composeApp")
    id("hilt")
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
}
true