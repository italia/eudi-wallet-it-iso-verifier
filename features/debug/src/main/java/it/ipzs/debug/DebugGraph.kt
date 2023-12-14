package it.ipzs.debug

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import it.ipzs.architecture.injection.composableVM
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.debug.detail.data.DebugDetailState
import it.ipzs.debug.detail.data.DebugDetailViewModel
import it.ipzs.debug.detail.ui.DebugDetailScreen
import it.ipzs.debug.list.data.DebugState
import it.ipzs.debug.list.data.DebugViewModel
import it.ipzs.debug.list.ui.DebugScreen
import it.ipzs.utils.BaseDestination

sealed class DebugGraph(
    paths: Array<out String>,
    queryParams: Array<out String>,
    dynamicTitle: Boolean,
) : BaseDestination(paths, queryParams, dynamicTitle) {
    companion object {
        val graphRoute: String = "debuggraph"

        fun fromPath(path: String): BaseDestination? {
            val name = if(path.contains("/")) {
                path.split("/").first()
            }
            else if (path.contains("?")) {
                path.split("?").first()
            }
            else path
            return when(name){
                "DebugDetailScreen" -> DebugDetailScreen
                "DebugScreen" -> DebugScreen
                else -> null
            }
        }
    }

    object DebugDetailScreen : DebugGraph(arrayOf("itemId"), arrayOf(), true) {
        const val KEY_ITEM_ID: String = "itemId"

        fun buildPath(androidAppTitle: String, itemId: String): String {
            val pathMap = mutableMapOf<String, String>()
            val queryMap = mutableMapOf<String, String?>()
            pathMap["androidAppTitle"] = androidAppTitle
            pathMap["itemId"] = itemId
            return super.buildPath(pathMap, queryMap)
        }
    }

    object DebugScreen : DebugGraph(arrayOf(), arrayOf(), false) {
        fun buildPath(): String {
            val pathMap = mutableMapOf<String, String>()
            val queryMap = mutableMapOf<String, String?>()
            return super.buildPath(pathMap, queryMap)
        }
    }
}

fun NavGraphBuilder.debugGraph(
    navController: NavHostController,
    onShare: (String) -> Unit
) {

    navigation(
        startDestination = DebugGraph.DebugScreen.route(),
        route = DebugGraph.graphRoute
    ){

        composableVM<DebugState, NoEvents, DebugViewModel>(
            route = DebugGraph.DebugScreen.route()
        ) {

            DebugScreen(
                logs = it.data,
                onDeleteClicked = {
                    deleteItem(it)
                },
                onShareClicked = {
                    getItemPrettyPrint(it){
                        onShare(it)
                    }
                }
            ){
                navController.navigate(
                    DebugGraph.DebugDetailScreen.buildPath(
                        androidAppTitle = it.name,
                        itemId = it.id.toString()
                    )
                )
            }

        }

        composableVM<DebugDetailState, NoEvents, DebugDetailViewModel>(
            route = DebugGraph.DebugDetailScreen.route()
        ){

            val id = it.data.id
            val date = it.data.date
            val qrCode = it.data.qrCode
            val success = it.data.success
            val requested = it.data.requestedPrettyPrint
            val received = it.data.received
            val errorMessage = it.data.errorMessage

            DebugDetailScreen(
                date = date,
                qrCode = qrCode,
                success = success,
                requested = requested,
                received = received,
                errorMessage = errorMessage
            ){
                getItemPrettyPrint(id){
                    onShare(it)
                }
            }

        }

    }

}