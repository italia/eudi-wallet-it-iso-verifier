package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheckType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MsoValidityCheckerTest {
    private val from = Instant.parse("2026-01-01T00:00:00Z")
    private val until = Instant.parse("2027-01-01T00:00:00Z")

    @Test
    fun withinWindowPasses() {
        val check = MsoValidityChecker.check(from, until, now = Instant.parse("2026-06-01T00:00:00Z"))
        assertEquals(VerificationCheckType.MSO_VALIDITY, check.type)
        assertEquals(CheckStatus.PASSED, check.status)
    }

    @Test
    fun afterValidUntilFails() {
        val check = MsoValidityChecker.check(from, until, now = Instant.parse("2027-06-01T00:00:00Z"))
        assertEquals(CheckStatus.FAILED, check.status)
        assertEquals(until, (check.metadata as CheckMetadata.MsoValidity).validUntil)
    }

    @Test
    fun beforeValidFromFails() {
        val check = MsoValidityChecker.check(from, until, now = Instant.parse("2025-06-01T00:00:00Z"))
        assertEquals(CheckStatus.FAILED, check.status)
    }

    @Test
    fun nowEqualToValidFromPasses() {
        // Limite inferiore inclusivo: now == validFrom → PASSED.
        val check = MsoValidityChecker.check(from, until, now = from)
        assertEquals(CheckStatus.PASSED, check.status)
    }

    @Test
    fun nowEqualToValidUntilPasses() {
        // Limite superiore inclusivo: now == validUntil → PASSED.
        val check = MsoValidityChecker.check(from, until, now = until)
        assertEquals(CheckStatus.PASSED, check.status)
    }
}
