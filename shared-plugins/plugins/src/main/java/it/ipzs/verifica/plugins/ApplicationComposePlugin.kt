package it.ipzs.verifica.plugins

import com.android.build.api.dsl.ApplicationExtension
import it.ipzs.verifica.plugins.utils.DependencyConfiguration
import it.ipzs.verifica.plugins.utils.dependencies
import it.ipzs.verifica.plugins.utils.enableCompose
import it.ipzs.verifica.plugins.utils.getVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class ApplicationComposePlugin: Plugin<Project> {

    override fun apply(target: Project) {

        target
            .extensions
            .getByType<ApplicationExtension>()
            .enableCompose(target.getVersion("compose-compiler"))

        target.dependencies(
            DependencyConfiguration.Implementation to "compose-activity",
            DependencyConfiguration.Implementation to "compose-ui",
            DependencyConfiguration.Implementation to "compose-ui-tooling",
            DependencyConfiguration.Implementation to "compose-material",
            DependencyConfiguration.Implementation to "compose-material3",
            DependencyConfiguration.Implementation to "compose-navigation"
        )
    }

}