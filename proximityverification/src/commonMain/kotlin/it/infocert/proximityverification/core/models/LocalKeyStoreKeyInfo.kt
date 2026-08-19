package it.infocert.proximityverification.core.models

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.KeyInfo
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class LocalKeystoreKeyInfo(
    alias: String,
    algorithm: Algorithm,
    publicKey: EcPublicKey,
    attestation: KeyAttestation,
    val attestKeyAlias: String?,
    val isUserAuthenticationRequired: Boolean,
    val userAuthenticationTimeoutMillis: Long,
    val isStrongBoxBacked: Boolean,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val certChain: List<X509Cert>?
) : KeyInfo(
    alias = alias,
    algorithm = algorithm,
    publicKey = publicKey,
    attestation = attestation
)