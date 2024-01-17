package it.ipzs.settings

import androidx.annotation.Keep
import it.ipzs.utils.navigation.BaseDestination

@Keep
abstract class SettingsGraph(
  paths: List<String> = listOf(),
  queryParams: List<String> = listOf(),
  dynamicTitle: Boolean = false,
) : BaseDestination(paths, queryParams, dynamicTitle) {
  companion object {
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

  @Keep
  data object SettingsScreen : SettingsGraph()

}
