package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.darwin.NSObject

actual class BluetoothChecker {
    actual companion object {
        actual suspend fun getBluetoothAvailability(): Boolean = suspendCancellableCoroutine { cont ->
            val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
                override fun centralManagerDidUpdateState(central: CBCentralManager) {
                    val available = central.state == CBManagerStatePoweredOn
                    cont.resumeWith(Result.success(available))
                }
            }
            // CBCentralManagerOptionShowPowerAlertKey = false suppresses the iOS system dialog
            // "Would you like to turn on Bluetooth?" during the availability check.
            // This is a diagnostic probe only — actual BLE transport init (if needed) happens later.
            CBCentralManager(delegate, null, mapOf(CBCentralManagerOptionShowPowerAlertKey to false))
        }
    }
}