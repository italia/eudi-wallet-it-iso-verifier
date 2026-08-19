package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.handlers.NativeUtils
import org.multipaz.securearea.SecureArea


suspend fun generateAssertion(sa: SecureArea, bArray: ByteArray): Any {
    return when {
        NativeUtils.isIos() -> generateAssertionFromClientDataSwift(sa, bArray)
        else -> generateAssertionFromClientDataAndroid(sa, bArray)
    }
}

expect suspend fun generateAssertionFromClientDataAndroid(
    secureArea: SecureArea,
    clientData: ByteArray
): String

expect suspend fun generateAssertionFromClientDataSwift(
    secureArea: SecureArea,
    clientData: ByteArray
): ByteArray