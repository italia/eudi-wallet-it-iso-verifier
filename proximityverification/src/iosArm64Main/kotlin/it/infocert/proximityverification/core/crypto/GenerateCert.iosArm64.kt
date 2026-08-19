package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.SecureArea

actual suspend fun generateSelfSignedCert(
    secureArea: SecureArea,
    alias: String,
    subject: X500Name
): X509Cert {
    val local = secureArea as? LocalSecureArea
        ?: throw ProximityVerificationException("Expected LocalSecureArea, got ${secureArea::class.simpleName}",
            code = ProximityVerificationExceptionCode.PVE03())
    return local.generateSelfSignedCert(alias, subject)
}

actual suspend fun generateCertSignedByRoot(
    secureArea: SecureArea,
    deviceKeyAlias: String,
    subject: X500Name,
    rootKeyAlias: String,
    rootCert: X509Cert
): X509Cert {
    val local = secureArea as? LocalSecureArea
        ?: throw ProximityVerificationException("Expected LocalSecureArea, got ${secureArea::class.simpleName}",
            code = ProximityVerificationExceptionCode.PVE03())
    return local.generateCertSignedByRoot(deviceKeyAlias, subject, rootKeyAlias, rootCert)
}

actual suspend fun buildCertChain(
    secureArea: SecureArea,
    deviceCert: X509Cert,
    rootCert: X509Cert
): X509CertChain {
    val local = secureArea as? LocalSecureArea
        ?: throw ProximityVerificationException("Expected LocalSecureArea, got ${secureArea::class.simpleName}",
            code = ProximityVerificationExceptionCode.PVE03())
    return local.buildCertChain(deviceCert, rootCert)
}