package it.infocert.proximityverification.core.handlers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import it.infocert.proximityverification.ContextInitializer

actual class BluetoothChecker {
    actual companion object {
        actual suspend fun getBluetoothAvailability(): Boolean {
            val context = ContextInitializer.applicationContext
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                    as? BluetoothManager ?: return false
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled
        }
    }
}