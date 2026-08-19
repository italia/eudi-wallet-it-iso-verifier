package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import org.multipaz.securearea.SecureArea

actual suspend fun generateAssertionFromClientDataAndroid(
    secureArea: SecureArea,
    clientData: ByteArray
): String {
    val local = secureArea as? LocalSecureArea
        ?: throw ProximityVerificationException(
            "Expected LocalSecureArea, got ${secureArea::class.simpleName}",
            code = ProximityVerificationExceptionCode.PVE03()
        )
    val keyTagStorage = provideLocalStorage()
    val androidCloudProjectNumber = keyTagStorage.getElement(Constants.ANDROID_DEVICE_CHECK_KEY_ALIAS)
        ?: throw ProximityVerificationException(
            "Expected androidCloudProjectNumber, got null",
            code = ProximityVerificationExceptionCode.PVE12())

    if (androidCloudProjectNumber.isBlank() || androidCloudProjectNumber.isEmpty()){
        throw ProximityVerificationException(
            "Exception, androidCloudProjectNumber is blank or empty",
            code = ProximityVerificationExceptionCode.PVE12())
    }

    return local.generateAssertionFromClientData(
        androidCloudProjectNumber = androidCloudProjectNumber,
        clientData = clientData
    )
}

actual suspend fun generateAssertionFromClientDataSwift(
    secureArea: SecureArea,
    clientData: ByteArray
): ByteArray {
    TODO("Not yet implemented")
}