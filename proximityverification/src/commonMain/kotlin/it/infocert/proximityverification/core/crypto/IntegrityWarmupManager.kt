package it.infocert.proximityverification.core.crypto

expect object IntegrityWarmupManager {
    var alreadyWarmedUp: Boolean
    suspend fun warmUp(data:String):String
}