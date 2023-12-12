package it.ipzs.verifica.plugins

import it.ipzs.verifica.plugins.utils.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project

class KspPlugin: Plugin<Project> {

    override fun apply(target: Project) {

        val kspPlugin = target.getPluginId("ksp")

        target.pluginManager.apply {
            apply(kspPlugin)
        }

    }

}