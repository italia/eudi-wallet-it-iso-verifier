package it.infocert.proximityverification.core.handlers

expect object NativeUtils {
    fun isIos(): Boolean
    fun isAndroid(): Boolean
    fun isNetworkAvailable(): Boolean
}