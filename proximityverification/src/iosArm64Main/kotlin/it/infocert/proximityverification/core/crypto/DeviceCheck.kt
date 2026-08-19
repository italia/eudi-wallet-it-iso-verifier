package it.infocert.proximityverification.core.crypto

import io.ktor.util.encodeBase64
import it.infocert.proximityverification.ProximityVerification
import it.infocert.proximityverification.core.trust.encodeBase64UrlNoPadding
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.bytestring.toByteString
import org.multipaz.SwiftBridge
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.device.DeviceAttestationIos
import org.multipaz.device.DeviceAttestationResult
import org.multipaz.device.toCbor
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    return ByteArray(length.toInt()).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}

@OptIn(ExperimentalForeignApi::class)
suspend fun generateDeviceAssertionFromSwift(
    keyId: String,
    clientDataHash: ByteArray
): ByteArray = suspendCoroutine { continuation ->
    SwiftBridge.generateDeviceAssertion(
        keyId,
        clientDataHash.toNSData()
    ) { data, error ->
        when {
            error != null -> continuation.resumeWithException(
                ProximityVerificationException(
                    error.localizedDescription,
                    code = ProximityVerificationExceptionCode.PVE12.default()
                )
            )

            data != null -> continuation.resume(data.toByteArray())

            else -> continuation.resumeWithException(
                ProximityVerificationException(
                    "Assertion Data is null but no error",
                    code = ProximityVerificationExceptionCode.PVE12.default()
                )
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
suspend fun generateDeviceAttestationFromSwift(nonce: ByteArray): DeviceAttestationResult {
    val mn= "generateDeviceAttestationFromSwift"
    return suspendCoroutine { continuation ->
        SwiftBridge.generateDeviceAttestation(nonce.toNSData()) { keyId, blob, err ->
            if (err != null) {
                var exception = ProximityVerificationException("Exception on $mn",
                    code = ProximityVerificationExceptionCode.PVE00.default())
                if (err.code.toInt() == 1) {
                    exception = ProximityVerificationException(
                        err.localizedDescription,
                        code = ProximityVerificationExceptionCode.PVE10.default()
                    )
                }
                if (err.code.toInt() == 2) {
                    exception = ProximityVerificationException(
                        err.localizedDescription,
                        code = ProximityVerificationExceptionCode.PVE11.default()
                    )
                }
                continuation.resumeWithException(exception)
            } else {
                continuation.resume(
                    DeviceAttestationResult(
                        keyId!!,
                        DeviceAttestationIos(blob!!.toByteString())
                    )
                )
            }
        }
    }
}