package it.infocert.proximityverification.core.models

import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode

enum class ProtocolFailureReason {
    MISSING_PERMISSION,
    INVALID_PARAMETERS,
    INVALID_ENGAGEMENT_DATA,
    CRYPTO_OPERATION_FAILED,
    STORAGE_ERROR,
    TRANSPORT_TIMEOUT,
    NFC_NOT_SUPPORTED,
    NFC_NOT_ENABLED,
    NFC_TAG_LOST,
    NFC_SCAN_CANCELLED,
    BLUETOOTH_UNAVAILABLE,
    ATTESTATION_FAILED,
    NETWORK_UNAVAILABLE,
    BACKEND_ERROR,
    GENERIC_INITIALIZATION_ERROR,
    GENERIC_VERIFICATION_ERROR,
    /** App selected a transport not present in [EngagementResult.availableTransports]. */
    INVALID_TRANSPORT_SELECTION,
    /** NFC data transfer selected after QR engagement but platformContext was not provided. */
    PLATFORM_CONTEXT_REQUIRED,
    /** mDL signalled session termination before returning a device response (no DeviceResponse payload). */
    SESSION_TERMINATED_WITHOUT_DEVICE_RESPONSE,
    GENERIC_ERROR;

    companion object {
        fun fromCode(code: ProximityVerificationExceptionCode): ProtocolFailureReason {
            return when (code.code) {
                "PVE02" -> MISSING_PERMISSION
                "PVE03", "PVE04" -> CRYPTO_OPERATION_FAILED
                "PVE05" -> INVALID_PARAMETERS
                "PVE06" -> STORAGE_ERROR
                "PVE01" -> GENERIC_INITIALIZATION_ERROR
                "PVE24" -> NFC_NOT_SUPPORTED
                "PVE25" -> NFC_NOT_ENABLED
                "PVE26", "PVE27" -> NFC_TAG_LOST
                "PVE28" -> INVALID_ENGAGEMENT_DATA
                "PVE29" -> NFC_SCAN_CANCELLED
                "PVE30" -> TRANSPORT_TIMEOUT
                "PVE36" -> SESSION_TERMINATED_WITHOUT_DEVICE_RESPONSE
                "PVE09" -> BLUETOOTH_UNAVAILABLE
                "PVE10", "PVE11", "PVE12", "PVE13" -> ATTESTATION_FAILED
                "PVE14", "PVE15" -> NETWORK_UNAVAILABLE
                "PVE08", "PVE16", "PVE17", "PVE18", "PVE19", "PVE20", "PVE21", "PVE22", "PVE23" -> BACKEND_ERROR
                "PVE07" -> GENERIC_VERIFICATION_ERROR
                else -> GENERIC_ERROR
            }
        }
    }
}
