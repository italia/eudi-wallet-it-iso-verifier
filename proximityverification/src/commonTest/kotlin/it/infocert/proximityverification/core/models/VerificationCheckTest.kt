package it.infocert.proximityverification.core.models

import it.infocert.proximityverification.core.crypto.ValidationMode
import it.infocert.proximityverification.status.model.Usability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerificationCheckTest {

    @Test
    fun checkDefaultsToNullMetadata() {
        val check = VerificationCheck(VerificationCheckType.ISSUER_TRUST, CheckStatus.PASSED)
        assertNull(check.metadata)
    }

    @Test
    fun checkCarriesStatusListMetadata() {
        val meta = CheckMetadata.StatusListRevocation(
            mode = ValidationMode.OFFLINE_CACHE_STALE,
            usability = Usability.StaleByTtl,
            state = RevocationState.SUSPENDED,
        )
        val check = VerificationCheck(VerificationCheckType.MSO_REVOCATION, CheckStatus.FAILED, meta)
        assertEquals(meta, check.metadata)
    }

    @Test
    fun newCheckTypesExist() {
        assertEquals("MSO_VALIDITY", VerificationCheckType.MSO_VALIDITY.name)
        assertEquals("DOCUMENT_EXPIRY", VerificationCheckType.DOCUMENT_EXPIRY.name)
    }
}
