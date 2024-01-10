package it.ipzs.onboarding

import it.ipzs.utils.navigation.BaseDestination

sealed class OnboardingGraph(
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
       "OnboardingScreen" -> OnboardingScreen
       else -> null
      }
    }
  }

  data object OnboardingScreen : OnboardingGraph()
}
