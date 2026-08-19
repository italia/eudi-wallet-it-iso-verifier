package it.infocert.proximityverification.core.handlers

expect class BluetoothChecker {
    companion object {
        suspend fun getBluetoothAvailability(): Boolean
    }
}