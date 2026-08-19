package it.infocert.proximityverification.core.models

import it.infocert.proximityverification.core.crypto.CrlCheckStatus
import it.infocert.proximityverification.core.crypto.ValidationMode
import it.infocert.proximityverification.status.model.Usability
import kotlin.time.Instant

data class VerificationCheck(
    val type: VerificationCheckType,
    val status: CheckStatus,
    val metadata: CheckMetadata? = null,
)

enum class CheckStatus {
    PASSED,
    FAILED,
    NOT_DETERMINED,
}

enum class VerificationCheckType {
    ISSUER_TRUST,
    ISSUER_CERT_REVOCATION,
    MSO_REVOCATION,
    MSO_VALIDITY,      // strutturale: validFrom/validUntil firmati nell'MSO
    DOCUMENT_EXPIRY,   // contenuto: claim expiry_date (doctype-aware)
}

/** Stato di revoca derivato dai bit della token-status-list. */
enum class RevocationState { VALID, REVOKED, SUSPENDED, UNKNOWN }

/** Metadata opzionale, additivo, allegato a un [VerificationCheck]. */
sealed interface CheckMetadata {
    data class StatusListRevocation(
        val mode: ValidationMode,
        val usability: Usability,
        val state: RevocationState,
    ) : CheckMetadata

    data class CrlRevocation(
        val crlCheckStatus: CrlCheckStatus,
    ) : CheckMetadata

    data class MsoValidity(
        val validFrom: Instant,
        val validUntil: Instant,
        val evaluatedAt: Instant,
    ) : CheckMetadata

    data class DocumentExpiry(
        val expiryDate: String?,   // valore grezzo del claim, null se assente
        val evaluatedAt: Instant,
    ) : CheckMetadata
}
