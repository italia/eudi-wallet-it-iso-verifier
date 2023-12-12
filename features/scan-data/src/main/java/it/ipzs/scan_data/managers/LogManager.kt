package it.ipzs.scan_data.managers

import android.util.Log

private val tag = "TRANSFER"

interface LogManager{

    fun startEngagement(qrDeviceEngagement: String) {
        Log.d(tag, "engagement started: $qrDeviceEngagement")
    }

    fun logRequest(request: String){
        Log.d(tag, "requested data: $request")
    }

    fun logReceived(received: String){
        Log.d(tag, "received:\n\n$received")
    }

    fun logError(e: Throwable){
        Log.e(tag, "an error is occurred", e)
    }

    fun logResponse(){
        Log.d(tag, "response received")
    }

    fun logDeviceConnected(){
        Log.d(tag, "device connected")
    }

    fun logDeviceDisconnected(transportSpecificTermination: Boolean){
        Log.d(tag, "device disconnected: $transportSpecificTermination")
    }

    fun end(){
        Log.d(tag, "connection closed")
    }

    fun canSetName() = false

    fun setCustomName(customName: String){
        //do nothing here, only in the impl
    }

}