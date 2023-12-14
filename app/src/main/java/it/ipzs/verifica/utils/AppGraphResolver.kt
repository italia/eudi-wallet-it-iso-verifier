package it.ipzs.verifica.utils

import it.ipzs.debug.DebugGraph
import it.ipzs.home.HomeGraph
import it.ipzs.onboarding.OnboardingGraph
import it.ipzs.scan.ScanFlowGraph
import it.ipzs.settings.SettingsGraph
import it.ipzs.utils.BaseDestination
import kotlin.String

object AppGraphResolver {
  fun resolve(route: String): BaseDestination? =
  	HomeGraph.fromPath(route) ?:
  	ScanFlowGraph.fromPath(route) ?:
  	SettingsGraph.fromPath(route) ?:
  	OnboardingGraph.fromPath(route) ?:
  	DebugGraph.fromPath(route)
}
