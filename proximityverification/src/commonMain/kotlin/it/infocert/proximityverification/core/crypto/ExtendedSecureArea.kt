package it.infocert.proximityverification.core.crypto

import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.SecureArea

interface ExtendedSecureArea : SecureArea {
    suspend fun setKeyCertChain(alias: String, certChain: X509CertChain)
}