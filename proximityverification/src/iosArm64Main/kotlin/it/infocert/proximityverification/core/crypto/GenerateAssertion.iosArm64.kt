package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import org.multipaz.securearea.SecureArea

private const val TAG = "GenerateAssertion"

actual suspend fun generateAssertionFromClientDataSwift(
    secureArea: SecureArea,
    clientData: ByteArray
): ByteArray {
    val mn = "generateAssertionFromClientData"

    Logger.debug(TAG) { "$mn - Start" }

    val local = secureArea as? LocalSecureArea
        ?: throw ProximityVerificationException(
            "Expected LocalSecureArea, got ${secureArea::class.simpleName}",
            code = ProximityVerificationExceptionCode.PVE03()
        )
    val keyTagStorage = provideLocalStorage()
    val logicalKey = Constants.IOS_DEVICE_CHECK_KEY_ALIAS

    val storedKeyId = keyTagStorage.getElement(logicalKey)

    Logger.debug(TAG) { "$mn - storedKeyId: $storedKeyId" }

    if (storedKeyId == null) {
        throw ProximityVerificationException(
            "Expected iOS keyId, got null",
            code = ProximityVerificationExceptionCode.PVE12()
        )
    }

    if (storedKeyId.isBlank() || storedKeyId.isEmpty()) {
        throw ProximityVerificationException(
            "Exception, storedKeyId is blank or empty",
            code = ProximityVerificationExceptionCode.PVE12()
        )
    }

    return local.generateAssertionFromClientDataSwift(storedKeyId, clientData)
}

actual suspend fun generateAssertionFromClientDataAndroid(
    secureArea: SecureArea,
    clientData: ByteArray
): String {
    TODO("Not yet implemented")
}