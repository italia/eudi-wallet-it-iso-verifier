package it.infocert.proximityverification.core.crypto

import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.SecureArea

expect suspend fun generateSelfSignedCert(
    secureArea: SecureArea,
    alias: String,
    subject: X500Name
): X509Cert

expect suspend fun generateCertSignedByRoot(
    secureArea: SecureArea,
    deviceKeyAlias: String,
    subject: X500Name,
    rootKeyAlias: String,
    rootCert: X509Cert
): X509Cert

expect suspend fun buildCertChain(secureArea: SecureArea,deviceCert: X509Cert, rootCert: X509Cert): X509CertChain
