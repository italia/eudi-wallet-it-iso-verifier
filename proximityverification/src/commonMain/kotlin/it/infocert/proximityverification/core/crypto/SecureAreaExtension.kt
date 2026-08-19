package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.SecureArea

suspend fun SecureArea.withCertChain(alias: String, certChain: X509CertChain) {
    (this as? ExtendedSecureArea)
        ?.setKeyCertChain(alias, certChain)
        ?: throw ProximityVerificationException("Current SecureArea does not support certificate chains",
            code = ProximityVerificationExceptionCode.PVE03())
}