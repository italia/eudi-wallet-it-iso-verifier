package it.infocert.proximityverification.core.crypto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import it.infocert.proximityverification.logging.Logger
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1OctetString
import org.multipaz.asn1.ASN1Sequence
import org.multipaz.asn1.ASN1String
import org.multipaz.asn1.ASN1TaggedObject
import org.multipaz.asn1.OID
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509Crl

object TrustUtils {
    private const val TAG = "TrustUtils"

    internal fun extractCrlDistributionPoint(cert: X509Cert): List<String> {
        val extBytes = cert.getExtensionValue(OID.X509_EXTENSION_CRL_DISTRIBUTION_POINTS.oid)
            ?: return emptyList()
        val uris = mutableListOf<String>()
        val octetString = ASN1OctetString.parse(extBytes)
        val distributionPoints = ASN1Sequence.parse(octetString.value)
        for (dp in distributionPoints.elements) {
            val dpOuterSeq = dp as? ASN1Sequence ?: continue
            val dpInnerSeq = dpOuterSeq.elements.firstOrNull() as? ASN1Sequence ?: continue
            for (dpElem in dpInnerSeq.elements) {
                if (dpElem is ASN1TaggedObject && dpElem.tag == 0) {
                    val dpNameSeq = ASN1Sequence.parse(dpElem.content)
                    for (dpNameElem in dpNameSeq.elements) {
                        if (dpNameElem is ASN1TaggedObject && dpNameElem.tag == 0) {
                            val generalNames = ASN1Sequence.parse(dpNameElem.content)
                            for (gn in generalNames.elements) {
                                if (gn is ASN1TaggedObject && gn.tag == 6) {
                                    val uri = ASN1String.parse(gn.content, tag = 6).value
                                    uris.add(uri)
                                }
                            }
                        }
                    }
                }
            }
        }
        return uris
    }

    suspend fun fetchCrlBytes(uri: String): ByteArray = fetchBytes(uri)

    suspend fun fetchRevocationPayload(uri: String): ByteArray = fetchBytes(uri)

    private suspend fun fetchBytes(uri: String): ByteArray {
        val client = HttpClient()
        return try {
            client.get(uri).body()
        } finally {
            client.close()
        }
    }

    fun loadPemCert(pem: String): X509Cert {
        return X509Cert.fromPem(pem.trimIndent())
    }

    @Deprecated(
        "Use CrlChecker.checkCert(cert) for cache- and offline-aware CRL checks. " +
            "This online-only path is preserved for back-compat with existing test fixtures.",
        ReplaceWith(
            "CrlChecker().checkCert(cert).isRevoked",
            "it.infocert.proximityverification.core.crypto.CrlChecker"
        )
    )
    suspend fun isCertRevoked(cert: X509Cert): Boolean {
        val crlUris = extractCrlDistributionPoint(cert)
        for (uri in crlUris) {
            try {
                val crlBytes = fetchCrlBytes(uri)
                val crl = X509Crl(ByteString(crlBytes))
                if (crl.revokedSerials.any { it == cert.serialNumber }) {
                    return true
                }
            } catch (e: Exception) {
                Logger.warn(TAG) { "Failed CRL check for $uri: ${e.message}" }
            }
        }
        return false
    }

    /**
     * Cache- and offline-aware revocation check. Walks [chain] via
     * [crlChecker], aggregating per-cert outcomes into the worst-severity
     * [CrlCheckStatus] (see [worse]). Returns early on the first revoked
     * cert; the returned `crlCheckStatus` reflects the aggregated severity
     * up to (and including) that cert.
     */
    internal suspend fun verifyNoneOfCertificateIsRevoked(
        chain: List<X509Cert>,
        crlChecker: CrlChecker
    ): RevocationResult {
        var aggregated: CrlCheckStatus = CrlCheckStatus.VERIFIED
        for (cert in chain) {
            val outcome = crlChecker.checkCert(cert)
            aggregated = worse(aggregated, outcome.status)
            if (outcome.isRevoked) {
                return RevocationResult(
                    isRevoked = true,
                    trustChain = X509CertChain(chain),
                    error = outcome.error
                        ?: Exception("Certificate revoked: ${cert.subject.name}"),
                    crlCheckStatus = aggregated
                )
            }
        }
        return RevocationResult(
            isRevoked = false,
            trustChain = X509CertChain(chain),
            crlCheckStatus = aggregated
        )
    }

    @Deprecated(
        "Use the (chain, CrlChecker) overload for cache- and offline-aware revocation. " +
            "This online-only path is preserved for back-compat.",
        ReplaceWith(
            "verifyNoneOfCertificateIsRevoked(chain, CrlChecker())",
            "it.infocert.proximityverification.core.crypto.CrlChecker"
        )
    )
    suspend fun verifyNoneOfCertificateIsRevoked(
        chain: List<X509Cert>
    ): RevocationResult {
        for (cert in chain) {
            @Suppress("DEPRECATION")
            if (isCertRevoked(cert)) {
                return RevocationResult(
                    isRevoked = true,
                    error = Exception("Certificate revoked: ${cert.subject.name}"),
                    trustChain = X509CertChain(chain)
                )
            }
        }
        return RevocationResult(
            isRevoked = false,
            trustChain = X509CertChain(chain)
        )
    }
}

data class RevocationResult(
    val isRevoked: Boolean,
    val trustChain: X509CertChain? = null,
    val error: Throwable? = null,
    val crlCheckStatus: CrlCheckStatus = CrlCheckStatus.VERIFIED
)

