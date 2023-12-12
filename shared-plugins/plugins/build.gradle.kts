plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin{

    plugins {

        register("androidApplication") {
            id = "androidApp"
            implementationClass = "it.ipzs.verifica.plugins.ApplicationPlugin"
        }
        register("androidLibrary") {
            id = "androidLib"
            implementationClass = "it.ipzs.verifica.plugins.LibraryPlugin"
        }
        register("composeBase") {
            id = "composeBase"
            implementationClass = "it.ipzs.verifica.plugins.BaseComposePlugin"
        }
        register("composeApplication") {
            id = "composeApp"
            implementationClass = "it.ipzs.verifica.plugins.ApplicationComposePlugin"
        }
        register("composeLibrary") {
            id = "composeLib"
            implementationClass = "it.ipzs.verifica.plugins.LibraryComposePlugin"
        }
        register("hilt") {
            id = "hilt"
            implementationClass = "it.ipzs.verifica.plugins.HiltPlugin"
        }
        register("ksp") {
            id = "ksp"
            implementationClass = "it.ipzs.verifica.plugins.KspPlugin"
        }
        register("room") {
            id = "room"
            implementationClass = "it.ipzs.verifica.plugins.RoomPlugin"
        }

    }

}