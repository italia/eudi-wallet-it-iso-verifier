package it.ipzs.verifica.plugins

import it.ipzs.verifica.plugins.utils.DependencyConfiguration
import it.ipzs.verifica.plugins.utils.dependencies
import it.ipzs.verifica.plugins.utils.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project

class RoomPlugin: Plugin<Project> {

    override fun apply(target: Project) {

        if(target.plugins.hasPlugin("ksp").not()){

            val kspPlugin = target.getPluginId("ksp")

            target.pluginManager.apply {
                apply(kspPlugin)
            }
        }

        target.dependencies(
            DependencyConfiguration.Implementation to "room-runtime",
            DependencyConfiguration.Implementation to "room-coroutines",
            DependencyConfiguration.Ksp to "room-processor",
        )

    }

}