package it.ipzs.home

import it.ipzs.utils.BaseDestination
import kotlin.Array
import kotlin.Boolean
import kotlin.String

public sealed class HomeGraph(
  paths: Array<out String>,
  queryParams: Array<out String>,
  dynamicTitle: Boolean,
) : BaseDestination(paths, queryParams, dynamicTitle) {
  public companion object {
    public val graphRoute: String = "homegraph"

    public fun fromPath(path: String): BaseDestination? {
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

  public object HomeScreen : HomeGraph(arrayOf(), arrayOf(), false) {
    public fun buildPath(): String {
      val pathMap = mutableMapOf<String, String>()
      val queryMap = mutableMapOf<String, String?>()
      return super.buildPath(pathMap, queryMap)
    }
  }
}
