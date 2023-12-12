@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("androidLib")
    id("composeBase")
    id("hilt")
}
dependencies {
    implementation(libs.compose.navigation)
    implementation(libs.compose.lifecycle)
}
true