package it.ipzs.verifica.plugins

import it.ipzs.verifica.plugins.utils.DependencyConfiguration
import it.ipzs.verifica.plugins.utils.dependencies
import it.ipzs.verifica.plugins.utils.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project

class HiltPlugin: Plugin<Project> {

    override fun apply(target: Project) {

        val hiltPlugin = target.getPluginId("hilt")
        val kapt = target.getPluginId("kapt")

        target.pluginManager.apply {
            apply(hiltPlugin)
            apply(kapt)
        }

        target.dependencies(
            DependencyConfiguration.Implementation to "hilt",
            DependencyConfiguration.Implementation to "hilt-navigation",
            DependencyConfiguration.Kapt to "hilt-compiler"
        )

    }

}