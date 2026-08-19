package it.infocert.proximityverification.core.models.document

import it.infocert.proximityverification.core.crypto.CrlCheckStatus
import it.infocert.proximityverification.core.crypto.DocumentExpiryChecker
import it.infocert.proximityverification.core.crypto.MsoRevocationChecker
import it.infocert.proximityverification.core.crypto.MsoValidityChecker
import it.infocert.proximityverification.core.crypto.ValidationMode
import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.RevocationState
import it.infocert.proximityverification.core.models.VerificationCheck
import it.infocert.proximityverification.core.models.VerificationCheckType
import it.infocert.proximityverification.status.model.Usability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class DocumentDataChecksTest {

    @Test
    fun issuerCertRevocationCarriesCrlMetadata() {
        val checks = DocumentData.buildVerificationChecks(
            issuerTrustStatus = CheckStatus.PASSED,
            issuerCertRevocationStatus = CheckStatus.PASSED,
            crlCheckStatus = CrlCheckStatus.STALE_CACHE,
            msoRevocation = MsoRevocationChecker.MsoRevocationOutcome(CheckStatus.PASSED, null),
            msoValidity = VerificationCheck(VerificationCheckType.MSO_VALIDITY, CheckStatus.PASSED),
            documentExpiry = null,
        )
        val crlCheck = checks.first { it.type == VerificationCheckType.ISSUER_CERT_REVOCATION }
        assertEquals(
            CheckMetadata.CrlRevocation(CrlCheckStatus.STALE_CACHE),
            crlCheck.metadata,
        )
    }

    @Test
    fun assemblesAllFiveChecksWithMetadataForNominalCase() {
        val now = Instant.parse("2026-06-01T00:00:00Z")
        val msoValidity = MsoValidityChecker.check(
            validFrom = Instant.parse("2026-01-01T00:00:00Z"),
            validUntil = Instant.parse("2027-01-01T00:00:00Z"),
            now = now,
        )
        val documentExpiry = DocumentExpiryChecker.check(expiryDateRaw = "2030-12-31", now = now)
        assertNotNull(documentExpiry)

        val checks = DocumentData.buildVerificationChecks(
            issuerTrustStatus = CheckStatus.PASSED,
            issuerCertRevocationStatus = CheckStatus.PASSED,
            crlCheckStatus = CrlCheckStatus.VERIFIED,
            msoRevocation = MsoRevocationChecker.MsoRevocationOutcome(
                CheckStatus.PASSED,
                CheckMetadata.StatusListRevocation(
                    mode = ValidationMode.ONLINE_LIVE,
                    usability = Usability.Fresh,
                    state = RevocationState.VALID,
                ),
            ),
            msoValidity = msoValidity,
            documentExpiry = documentExpiry,
        )

        // (1) exact set + order + count
        assertEquals(
            listOf(
                VerificationCheckType.ISSUER_TRUST,
                VerificationCheckType.ISSUER_CERT_REVOCATION,
                VerificationCheckType.MSO_REVOCATION,
                VerificationCheckType.MSO_VALIDITY,
                VerificationCheckType.DOCUMENT_EXPIRY,
            ),
            checks.map { it.type },
        )

        // (2) ISSUER_CERT_REVOCATION carries CRL metadata
        val issuerCertRevocation = checks.first { it.type == VerificationCheckType.ISSUER_CERT_REVOCATION }
        assertEquals(
            CheckMetadata.CrlRevocation(CrlCheckStatus.VERIFIED),
            issuerCertRevocation.metadata,
        )

        // (3) MSO_REVOCATION carries StatusListRevocation with state VALID, status PASSED
        val msoRevocationCheck = checks.first { it.type == VerificationCheckType.MSO_REVOCATION }
        assertEquals(CheckStatus.PASSED, msoRevocationCheck.status)
        val statusListMeta = assertIs<CheckMetadata.StatusListRevocation>(msoRevocationCheck.metadata)
        assertEquals(RevocationState.VALID, statusListMeta.state)

        // (4) MSO_VALIDITY PASSED with MsoValidity metadata
        val msoValidityCheck = checks.first { it.type == VerificationCheckType.MSO_VALIDITY }
        assertEquals(CheckStatus.PASSED, msoValidityCheck.status)
        assertIs<CheckMetadata.MsoValidity>(msoValidityCheck.metadata)

        // (5) DOCUMENT_EXPIRY PASSED with DocumentExpiry metadata
        val documentExpiryCheck = checks.first { it.type == VerificationCheckType.DOCUMENT_EXPIRY }
        assertEquals(CheckStatus.PASSED, documentExpiryCheck.status)
        assertIs<CheckMetadata.DocumentExpiry>(documentExpiryCheck.metadata)
    }

    @Test
    fun omitsDocumentExpiryWhenNotApplicable() {
        val checks = DocumentData.buildVerificationChecks(
            issuerTrustStatus = CheckStatus.PASSED,
            issuerCertRevocationStatus = CheckStatus.PASSED,
            crlCheckStatus = CrlCheckStatus.VERIFIED,
            msoRevocation = MsoRevocationChecker.MsoRevocationOutcome(CheckStatus.PASSED, null),
            msoValidity = VerificationCheck(VerificationCheckType.MSO_VALIDITY, CheckStatus.PASSED),
            documentExpiry = null,
        )

        assertEquals(4, checks.size)
        assertFalse(checks.any { it.type == VerificationCheckType.DOCUMENT_EXPIRY })
    }
}
