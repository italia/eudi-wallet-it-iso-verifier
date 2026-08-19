package it.infocert.proximityverification.core.handlers

import it.infocert.proximityverification.core.models.AppPermission
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import it.infocert.proximityverification.ContextInitializer
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode

actual object PermissionManager {
    actual fun checkPermissionsOrThrow(settings: ProximityVerificationSettings) {
        val bleRequired = settings.isBleCentralClientModeEnabled()
            || settings.isBlePeripheralServerModeEnabled()
        if (!bleRequired) return

        // On Android 12+ (API 31+), if BLUETOOTH_SCAN is declared with neverForLocation flag,
        // ACCESS_FINE_LOCATION is not required. Only check it on Android 11 and lower.
        val defaultPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                AppPermission.BLUETOOTH_SCAN,
                AppPermission.BLUETOOTH_CONNECT,
                AppPermission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                AppPermission.LOCATION,
                AppPermission.BLUETOOTH_SCAN,
                AppPermission.BLUETOOTH_CONNECT,
                AppPermission.BLUETOOTH_ADVERTISE
            )
        }

        defaultPermissions.forEach {
            val androidPermission = it.toAndroidPermission()
            val granted = ContextInitializer.applicationContext
                .checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                throw ProximityVerificationException(
                    code = ProximityVerificationExceptionCode.PVE02(),
                    message = ProximityVerificationExceptionCode.PVE02(androidPermission).details
                )
            }
        }
    }

    private fun AppPermission.toAndroidPermission(): String = when (this) {
        AppPermission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        AppPermission.BLUETOOTH_SCAN -> (Manifest.permission.BLUETOOTH_SCAN)
            .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            ?: Manifest.permission.BLUETOOTH
        AppPermission.BLUETOOTH_CONNECT -> (Manifest.permission.BLUETOOTH_CONNECT)
            .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            ?: Manifest.permission.BLUETOOTH
        AppPermission.BLUETOOTH_ADVERTISE -> (Manifest.permission.BLUETOOTH_ADVERTISE)
            .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            ?: Manifest.permission.BLUETOOTH
    }
}
