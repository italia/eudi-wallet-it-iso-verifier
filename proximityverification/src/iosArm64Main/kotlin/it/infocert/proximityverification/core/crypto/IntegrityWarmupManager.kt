package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.trust.encodeBase64UrlNoPadding
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.device.DeviceAttestationResult
import org.multipaz.device.toCbor
import org.multipaz.util.toBase64Url

actual object IntegrityWarmupManager {
    private const val TAG = "IntegrityWarmupManager"
    actual var alreadyWarmedUp: Boolean = false

    actual suspend fun warmUp(data: String): String {
        val mn = "warmUp"
        val keyTagStorage = provideLocalStorage()
        val logicalKey = Constants.IOS_DEVICE_CHECK_KEY_ALIAS

        try {
            val storedKeyId = keyTagStorage.getElement(logicalKey)
            if (storedKeyId != null) {
                Logger.info(TAG) { "$mn - Device already warmed up with keyId: $storedKeyId" }
                alreadyWarmedUp = true
                val storedBlob = keyTagStorage.getElement(Constants.IOS_DEVICE_CHECK_ATTESTATION_OBJ)
                return storedBlob ?: data
            }

            val isDeviceOnline = NativeUtils.isNetworkAvailable()
            if (!isDeviceOnline) {
                throw ProximityVerificationException(
                    "Device attestation requires internet connectivity. No usable internet connection is available. Ensure the device is connected to the internet and try again.",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val nonceBytes =
                Crypto.digest(Algorithm.SHA256, data.encodeToByteString().toByteArray())
            val nonce = nonceBytes.encodeBase64UrlNoPadding()

            val deARes: DeviceAttestationResult = generateDeviceAttestationFromSwift(nonceBytes)
            val attestationObject = deARes.deviceAttestation.toCbor().toBase64Url()

            val payload = AttestationPayload(deARes.deviceAttestationId, attestationObject, nonce)
            val json = Json.encodeToString(payload)
            keyTagStorage.saveElement(logicalKey, deARes.deviceAttestationId)
            keyTagStorage.saveElement(Constants.IOS_DEVICE_CHECK_ATTESTATION_OBJ, json)
            alreadyWarmedUp = true
            return json
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - Error during device check warm up: ${e.message}" }
            throw e
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Error during device check warm up: ${e.message}" }
            throw ProximityVerificationException(
                "Error during device check warm up: $e", e,
                code = ProximityVerificationExceptionCode.PVE12()
            )
        }
    }

    @Serializable
    data class AttestationPayload(
        val keyID: String,
        val attestationObject: String,
        val challengeNonce: String
    )
}
