package it.ipzs.settings

import it.ipzs.utils.BaseDestination
import kotlin.Array
import kotlin.Boolean
import kotlin.String

sealed class SettingsGraph(
  paths: Array<out String>,
  queryParams: Array<out String>,
  dynamicTitle: Boolean,
) : BaseDestination(paths, queryParams, dynamicTitle) {
  companion object {
    val graphRoute: String = "settingsgraph"

    fun fromPath(path: String): BaseDestination? {
      val name = if(path.contains("/")) {
        path.split("/").first()
      }
      else if (path.contains("?")) {
        path.split("?").first()
      }
      else path
      return when(name){
       "SettingsScreen" -> SettingsScreen
       else -> null
      }
    }
  }

  object SettingsScreen : SettingsGraph(arrayOf(), arrayOf(), false) {
    fun buildPath(): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      return super.buildPath(pathMap, queryMap)
    }
  }
}
