package it.infocert.etuitus.sampleandroid

import it.infocert.proximityverification.core.models.request.ClaimOutcome
import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestOutcome
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.models.request.RequestOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Policy di obbligatorietà del sample.
 *
 * ISO 18013-5 non permette di imporre un claim: questa è una regola di prodotto applicata a
 * posteriori sui fatti che l'SDK riporta. Il sample sceglie la regola più severa possibile — tutto
 * ciò che ho chiesto mi deve tornare — perché è quella che rende visibile ogni assenza.
 *
 * Il verdetto poggia su `RETURNED` e non sulla differenza fra `WITHHELD` e `ABSENT`: quelle due
 * mappe non sono coperte da firma.
 */
class RequiredClaimsTest {

    private companion object {
        const val MDL = "org.iso.18013.5.1.mDL"
        const val NS = "org.iso.18013.5.1"
    }

    private fun claim(identifier: String, status: ClaimStatus, code: Int? = null) =
        ClaimOutcome(NS, identifier, status, code)

    private fun outcome(vararg documents: DocumentRequestOutcome) =
        RequestOutcome(documents = documents.toList())

    @Test
    fun allRequestedClaimsReturned_isComplete() {
        val verdict = RequiredClaims.evaluate(
            outcome(
                DocumentRequestOutcome(
                    MDL, DocumentRequestStatus.RETURNED, null,
                    listOf(
                        claim("family_name", ClaimStatus.RETURNED),
                        claim("given_name", ClaimStatus.RETURNED),
                    )
                )
            )
        )

        assertEquals(RequiredClaims.Verdict.Complete, verdict)
    }

    @Test
    fun unrequestedClaimsDoNotBreakCompleteness() {
        val verdict = RequiredClaims.evaluate(
            outcome(
                DocumentRequestOutcome(
                    MDL, DocumentRequestStatus.RETURNED, null,
                    listOf(
                        claim("family_name", ClaimStatus.RETURNED),
                        claim("birth_date", ClaimStatus.UNREQUESTED),
                    )
                )
            )
        )

        assertEquals(RequiredClaims.Verdict.Complete, verdict)
    }

    @Test
    fun withheldAndAbsentClaimsAreBothMissing() {
        val verdict = RequiredClaims.evaluate(
            outcome(
                DocumentRequestOutcome(
                    MDL, DocumentRequestStatus.RETURNED, null,
                    listOf(
                        claim("family_name", ClaimStatus.RETURNED),
                        claim("portrait", ClaimStatus.WITHHELD, 0),
                        claim("sex", ClaimStatus.ABSENT),
                    )
                )
            )
        ) as RequiredClaims.Verdict.Incomplete

        assertEquals(2, verdict.missingClaims.size)
        assertTrue(verdict.missingDocuments.isEmpty())
        assertEquals(
            listOf("portrait", "sex"),
            verdict.missingClaims.map { it.identifier },
        )
        assertEquals(ClaimStatus.WITHHELD, verdict.missingClaims.first().status)
    }

    @Test
    fun deniedAndAbsentDocumentsAreReportedSeparately() {
        val verdict = RequiredClaims.evaluate(
            outcome(
                DocumentRequestOutcome(MDL, DocumentRequestStatus.DENIED, 0, emptyList()),
                DocumentRequestOutcome(
                    "org.iso.23220.photoid.1", DocumentRequestStatus.ABSENT, null, emptyList()
                ),
            )
        ) as RequiredClaims.Verdict.Incomplete

        assertEquals(2, verdict.missingDocuments.size)
        assertTrue(verdict.missingClaims.isEmpty())
        assertEquals(DocumentRequestStatus.DENIED, verdict.missingDocuments.first().status)
        assertEquals(0, verdict.missingDocuments.first().errorCode)
    }

    @Test
    fun emptyOutcome_isComplete() {
        assertEquals(RequiredClaims.Verdict.Complete, RequiredClaims.evaluate(outcome()))
    }
}
