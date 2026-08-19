package it.infocert.proximityverification.core.handlers

import it.infocert.proximityverification.core.models.AppPermission
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways

actual object PermissionManager {
    actual fun checkPermissionsOrThrow(settings: ProximityVerificationSettings) {
        val bleRequired = settings.isBleCentralClientModeEnabled()
            || settings.isBlePeripheralServerModeEnabled()
        if (!bleRequired) return

        // CBCentralManager instantiation reads the BLE authorization state.
        // We instantiate it only when BLE is actually needed (bleRequired = true)
        // to avoid triggering the iOS system BLE permission prompt for NFC-only flows.
        val auth = CBCentralManager(delegate = null, queue = null).authorization
        if (auth != CBManagerAuthorizationAllowedAlways) {
            throw ProximityVerificationException(
                code = ProximityVerificationExceptionCode.PVE02(),
                message = ProximityVerificationExceptionCode.PVE02(AppPermission.BLUETOOTH_SCAN.name).details
            )
        }
    }
}
