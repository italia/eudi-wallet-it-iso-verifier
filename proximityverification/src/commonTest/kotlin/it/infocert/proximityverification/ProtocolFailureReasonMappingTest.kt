package it.infocert.proximityverification

import it.infocert.proximityverification.core.models.ProtocolFailureReason
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolFailureReasonMappingTest {

    @Test
    fun pve01MapsToGenericInitializationError() {
        assertEquals(
            ProtocolFailureReason.GENERIC_INITIALIZATION_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE01())
        )
    }

    @Test
    fun pve02MapsToMissingPermission() {
        assertEquals(
            ProtocolFailureReason.MISSING_PERMISSION,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE02())
        )
    }

    @Test
    fun pve03MapsToCryptoOperationFailed() {
        assertEquals(
            ProtocolFailureReason.CRYPTO_OPERATION_FAILED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE03())
        )
    }

    @Test
    fun pve04MapsToCryptoOperationFailed() {
        assertEquals(
            ProtocolFailureReason.CRYPTO_OPERATION_FAILED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE04())
        )
    }

    @Test
    fun pve05MapsToInvalidParameters() {
        assertEquals(
            ProtocolFailureReason.INVALID_PARAMETERS,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE05())
        )
    }

    @Test
    fun pve06MapsToStorageError() {
        assertEquals(
            ProtocolFailureReason.STORAGE_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE06())
        )
    }

    @Test
    fun pve07MapsToGenericVerificationError() {
        assertEquals(
            ProtocolFailureReason.GENERIC_VERIFICATION_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE07())
        )
    }

    @Test
    fun pve08MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE08())
        )
    }

    @Test
    fun pve09MapsToBluetoothUnavailable() {
        assertEquals(
            ProtocolFailureReason.BLUETOOTH_UNAVAILABLE,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE09())
        )
    }

    @Test
    fun pve10MapsToAttestationFailed() {
        assertEquals(
            ProtocolFailureReason.ATTESTATION_FAILED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE10())
        )
    }

    @Test
    fun pve11MapsToAttestationFailed() {
        assertEquals(
            ProtocolFailureReason.ATTESTATION_FAILED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE11())
        )
    }

    @Test
    fun pve12MapsToAttestationFailed() {
        assertEquals(
            ProtocolFailureReason.ATTESTATION_FAILED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE12())
        )
    }

    @Test
    fun pve13MapsToAttestationFailed() {
        assertEquals(
            ProtocolFailureReason.ATTESTATION_FAILED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE13())
        )
    }

    @Test
    fun pve14MapsToNetworkUnavailable() {
        assertEquals(
            ProtocolFailureReason.NETWORK_UNAVAILABLE,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE14())
        )
    }

    @Test
    fun pve15MapsToNetworkUnavailable() {
        assertEquals(
            ProtocolFailureReason.NETWORK_UNAVAILABLE,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE15())
        )
    }

    @Test
    fun pve16MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE16())
        )
    }

    @Test
    fun pve17MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE17())
        )
    }

    @Test
    fun pve18MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE18())
        )
    }

    @Test
    fun pve19MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE19())
        )
    }

    @Test
    fun pve20MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE20())
        )
    }

    @Test
    fun pve21MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE21())
        )
    }

    @Test
    fun pve22MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE22())
        )
    }

    @Test
    fun pve23MapsToBackendError() {
        assertEquals(
            ProtocolFailureReason.BACKEND_ERROR,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE23())
        )
    }

    @Test
    fun pve24MapsToNfcNotSupported() {
        assertEquals(
            ProtocolFailureReason.NFC_NOT_SUPPORTED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE24())
        )
    }

    @Test
    fun pve25MapsToNfcNotEnabled() {
        assertEquals(
            ProtocolFailureReason.NFC_NOT_ENABLED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE25())
        )
    }

    @Test
    fun pve26MapsToNfcTagLost() {
        assertEquals(
            ProtocolFailureReason.NFC_TAG_LOST,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE26())
        )
    }

    @Test
    fun pve27MapsToNfcTagLost() {
        assertEquals(
            ProtocolFailureReason.NFC_TAG_LOST,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE27())
        )
    }

    @Test
    fun pve28MapsToInvalidEngagementData() {
        assertEquals(
            ProtocolFailureReason.INVALID_ENGAGEMENT_DATA,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE28())
        )
    }

    @Test
    fun pve29MapsToNfcScanCancelled() {
        assertEquals(
            ProtocolFailureReason.NFC_SCAN_CANCELLED,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE29())
        )
    }

    @Test
    fun pve30MapsToTransportTimeout() {
        assertEquals(
            ProtocolFailureReason.TRANSPORT_TIMEOUT,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE30())
        )
    }

    @Test
    fun pve36MapsToSessionTerminatedWithoutDeviceResponse() {
        assertEquals(
            ProtocolFailureReason.SESSION_TERMINATED_WITHOUT_DEVICE_RESPONSE,
            ProtocolFailureReason.fromCode(ProximityVerificationExceptionCode.PVE36())
        )
    }

    @Test
    fun unknownCodeMapsToGenericError() {
        val inputUnknownCode = ProximityVerificationExceptionCode.PVE00()
        assertEquals(
            ProtocolFailureReason.GENERIC_ERROR,
            ProtocolFailureReason.fromCode(inputUnknownCode)
        )
    }
}
