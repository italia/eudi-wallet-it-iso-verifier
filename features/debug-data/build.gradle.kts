plugins {
    id("androidLib")
    id("hilt")
}

dependencies{
    implementation(project(":core:database"))
    implementation(project(":core:app-scope"))
    implementation(project(":core:utils"))
}