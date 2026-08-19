package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.models.CheckStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DocumentExpiryCheckerTest {
    private val now = Instant.parse("2026-06-01T00:00:00Z")

    @Test
    fun absentClaimReturnsNull() {
        val check = DocumentExpiryChecker.check(expiryDateRaw = null, now = now)
        assertNull(check)
    }

    @Test
    fun futureExpiryPasses() {
        val check = DocumentExpiryChecker.check(expiryDateRaw = "2030-12-31", now = now)
        assertEquals(CheckStatus.PASSED, check?.status)
    }

    @Test
    fun pastExpiryFails() {
        val check = DocumentExpiryChecker.check(expiryDateRaw = "2024-01-01", now = now)
        assertEquals(CheckStatus.FAILED, check?.status)
    }

    @Test
    fun unparsableDateReturnsNull() {
        val check = DocumentExpiryChecker.check(expiryDateRaw = "not-a-date", now = now)
        assertNull(check)
    }

    @Test
    fun nowEqualToExpiryPasses() {
        // Limite inclusivo: now == expiry → PASSED (la full-date "2026-06-01" è ancorata a 23:59:59Z).
        val check = DocumentExpiryChecker.check(
            expiryDateRaw = "2026-06-01",
            now = Instant.parse("2026-06-01T23:59:59Z"),
        )
        assertEquals(CheckStatus.PASSED, check?.status)
    }
}
