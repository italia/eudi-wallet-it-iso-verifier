package it.ipzs.utils.navigation

import java.net.URLEncoder

abstract class BaseDestination(
    private val paths: List<String>,
    private val queryParams: List<String>,
    val dynamicTitle: Boolean
) {

    protected val name: String = this::class.simpleName ?: throw IllegalStateException()

    fun route(): String {
        val route = StringBuilder(name)
        if(dynamicTitle) {
            route.append("/{$ANDROID_TITLE}")
        }
        if(paths.isNotEmpty()) {
            val endPath = paths.joinToString("}/{")
            route.append("/{")
            route.append(endPath)
            route.append("}")
        }
        if(queryParams.isNotEmpty()) {
            route.append("?")
            queryParams.forEach{ query ->
                route.append("$query={$query}&")
            }
            route.deleteCharAt(route.length -1)
        }
        return route.toString()
    }

    fun buildPath(pathMap: Map<String, String> = mapOf(), queryMap: Map<String, String?> = mapOf()): String {
        val pathToBuild = StringBuilder()
        pathToBuild.append(name)
        if(pathMap.containsKey(ANDROID_TITLE)) {
            pathToBuild.append("/")
            val encodedTitle = URLEncoder.encode(pathMap[ANDROID_TITLE], "UTF-8")
            pathToBuild.append(encodedTitle)
        }
        paths.forEach{
            if(!pathMap.containsKey(it)) {
                throw IllegalArgumentException("$it is not in the map")
            }
            val encodedPath = URLEncoder.encode(pathMap[it], "UTF-8")
            pathToBuild.append("/$encodedPath")
        }
        if(queryMap.isNotEmpty()) {
            pathToBuild.append("?")
            queryMap.forEach{(key, value) ->
                val encodedValue = URLEncoder.encode(value, "UTF-8")
                pathToBuild.append("$key=$encodedValue&")
            }
            pathToBuild.deleteCharAt(pathToBuild.length -1)
        }

        return pathToBuild.toString()
    }

    companion object{
        const val ANDROID_TITLE: String = "androidAppTitle"
    }

}