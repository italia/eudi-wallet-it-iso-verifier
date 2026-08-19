package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheck
import it.infocert.proximityverification.core.models.VerificationCheckType
import kotlin.time.Clock
import kotlin.time.Instant

object MsoValidityChecker {
    fun check(validFrom: Instant, validUntil: Instant, now: Instant = Clock.System.now()): VerificationCheck {
        val status = if (now < validFrom || now > validUntil) CheckStatus.FAILED else CheckStatus.PASSED
        return VerificationCheck(
            type = VerificationCheckType.MSO_VALIDITY,
            status = status,
            metadata = CheckMetadata.MsoValidity(validFrom = validFrom, validUntil = validUntil, evaluatedAt = now),
        )
    }
}
