package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheck
import it.infocert.proximityverification.core.models.VerificationCheckType
import kotlin.time.Clock
import kotlin.time.Instant

object DocumentExpiryChecker {
    /**
     * Check di contenuto: confronta il claim `expiry_date` (scadenza amministrativa del documento)
     * con la data corrente. Claim assente o non parsabile → null (N/A, non blocca).
     * Distinto da MSO_VALIDITY (validità tecnica/crittografica della credenziale).
     */
    fun check(expiryDateRaw: String?, now: Instant = Clock.System.now()): VerificationCheck? {
        if (expiryDateRaw.isNullOrBlank()) return null
        val expiry = parseDate(expiryDateRaw) ?: return null
        val status = if (now > expiry) CheckStatus.FAILED else CheckStatus.PASSED
        return VerificationCheck(
            type = VerificationCheckType.DOCUMENT_EXPIRY,
            status = status,
            metadata = CheckMetadata.DocumentExpiry(expiryDate = expiryDateRaw, evaluatedAt = now),
        )
    }

    // Accetta full-date "YYYY-MM-DD" e date-time ISO-8601; ritorna null se non parsabile.
    private fun parseDate(raw: String): Instant? {
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { Instant.parse("${raw}T23:59:59Z") }.getOrNull()
    }
}
