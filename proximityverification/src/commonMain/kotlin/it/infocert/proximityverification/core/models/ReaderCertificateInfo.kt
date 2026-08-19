package it.infocert.proximityverification.core.models

import kotlin.time.Clock
import kotlin.time.Instant
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.X509Cert

/**
 * Dettagli del reader/access certificate emesso dal verifier backend e usato
 * dalla libreria come reader auth. Model di sola lettura, uso in-process.
 */
data class ReaderCertificateInfo(
    val subjectCommonName: String?,
    val subjectDn: String,
    val issuerCommonName: String?,
    val issuerDn: String,
    val notBefore: Instant,
    val notAfter: Instant,
    /** true se now è fuori dall'intervallo di validità (non ancora valido oppure scaduto). */
    val isExpired: Boolean,
    val serialNumber: String,
    val sha256Fingerprint: String,
    val pem: String,
) {
    companion object {
        /** Costruisce il model da un X509Cert. Assume un certificato ben formato; i getter di X509Cert non validano oltre il parsing top-level. */
        @OptIn(ExperimentalStdlibApi::class)
        suspend fun from(cert: X509Cert): ReaderCertificateInfo {
            val der = cert.encoded.toByteArray()
            val now = Clock.System.now()
            val notBefore = cert.validityNotBefore
            val notAfter = cert.validityNotAfter
            return ReaderCertificateInfo(
                subjectCommonName = extractCommonName(cert.subject.name),
                subjectDn = cert.subject.name,
                issuerCommonName = extractCommonName(cert.issuer.name),
                issuerDn = cert.issuer.name,
                notBefore = notBefore,
                notAfter = notAfter,
                isExpired = now < notBefore || now > notAfter,
                serialNumber = cert.serialNumber.value.toHexString(),
                sha256Fingerprint = Crypto.digest(Algorithm.SHA256, der).toHexString(),
                pem = cert.toPem(),
            )
        }

        /** Estrae la componente CN da un DN in formato "CN=...,O=...". Limite noto: non gestisce virgole escaped nei valori, coerente con la codifica non-escaped di X500Name.name. */
        private fun extractCommonName(dn: String): String? =
            dn.split(",")
                .map { it.trim() }
                .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
                ?.substring(3)
                ?.takeIf { it.isNotEmpty() }
    }
}
