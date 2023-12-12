package it.ipzs.verifica.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import it.ipzs.verifica.plugins.utils.enableCompose
import it.ipzs.verifica.plugins.utils.getVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class BaseComposePlugin: Plugin<Project>{

    override fun apply(target: Project) {

        target.extensions.let {
            if(target.plugins.hasPlugin("androidLib"))
              it.getByType<LibraryExtension>()
            else it.getByType<ApplicationExtension>()
        }.enableCompose(target.getVersion("compose-compiler"))

    }

}