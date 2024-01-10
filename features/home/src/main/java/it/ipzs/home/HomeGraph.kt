package it.ipzs.home

import it.ipzs.utils.navigation.BaseDestination

sealed class HomeGraph(
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
       "HomeScreen" -> HomeScreen
       else -> null
      }
    }
  }

  data object HomeScreen : HomeGraph()
}
