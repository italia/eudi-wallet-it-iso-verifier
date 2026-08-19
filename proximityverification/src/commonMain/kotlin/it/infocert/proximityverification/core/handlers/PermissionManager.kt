package it.infocert.proximityverification.core.handlers

import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings

expect object PermissionManager {
    fun checkPermissionsOrThrow(settings: ProximityVerificationSettings)
}