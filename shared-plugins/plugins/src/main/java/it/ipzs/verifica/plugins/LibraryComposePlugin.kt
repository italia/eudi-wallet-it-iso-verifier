package it.ipzs.verifica.plugins

import com.android.build.api.dsl.LibraryExtension
import it.ipzs.verifica.plugins.utils.DependencyConfiguration
import it.ipzs.verifica.plugins.utils.dependencies
import it.ipzs.verifica.plugins.utils.enableCompose
import it.ipzs.verifica.plugins.utils.getVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class LibraryComposePlugin: Plugin<Project> {
    override fun apply(target: Project) {

        target
            .extensions
            .getByType<LibraryExtension>()
            .enableCompose(target.getVersion("compose-compiler"))

        target.dependencies(
            DependencyConfiguration.Implementation to "compose-ui",
            DependencyConfiguration.Implementation to "compose-ui-tooling",
            DependencyConfiguration.Implementation to "compose-foundation",
            DependencyConfiguration.Implementation to "compose-material",
            DependencyConfiguration.Implementation to "compose-material3",
            DependencyConfiguration.Implementation to "compose-material-icons-core",
            DependencyConfiguration.Implementation to "compose-material-icons-ext",
            DependencyConfiguration.Implementation to "compose-animation"
        )
    }
}