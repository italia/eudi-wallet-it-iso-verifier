package it.infocert.proximityverification

import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.models.request.RequestOutcome
import it.infocert.proximityverification.core.reconciliation.RequestReconciler
import it.infocert.proximityverification.core.reconciliation.ReturnedDocumentFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Riconciliazione fra richiesta e risposta.
 *
 * Design: `docs/design/2026-08-03_request-outcome-reconciliation.md`.
 *
 * ISO 18013-5 non permette di imporre un claim: la risposta può essere legittimamente parziale, e il
 * consumatore decide da sé se un'assenza è fatale. Questi test presidiano l'unica cosa che l'SDK
 * promette — l'inventario esatto di ciò che è stato chiesto e di ciò che è tornato — perché è la base
 * su cui quella decisione viene presa. In particolare presidiano che l'omissione **silenziosa** sia
 * rilevata: dichiarare `errors` è facoltativo per il wallet, quindi un'API che si fidasse solo di
 * quella mappa funzionerebbe solo contro i wallet ben educati.
 */
class RequestReconcilerTest {

    private companion object {
        const val MDL = "org.iso.18013.5.1.mDL"
        const val NS = "org.iso.18013.5.1"
        const val PID = "org.iso.23220.photoid.1"
        const val PID_NS = "org.iso.23220.1"
    }

    private fun request(docType: String, vararg identifiers: String) = VerificationRequest(
        docType = docType,
        requestedClaims = mapOf(NS to identifiers.associateWith { false }),
    )

    private fun facts(
        docType: String = MDL,
        namespace: String = NS,
        present: Set<String> = emptySet(),
        errors: Map<String, Int> = emptyMap(),
    ) = ReturnedDocumentFacts(
        docType = docType,
        elements = if (present.isEmpty()) emptyMap() else mapOf(namespace to present),
        errors = if (errors.isEmpty()) emptyMap() else mapOf(namespace to errors),
    )

    private fun statusOf(
        outcome: RequestOutcome,
        docType: String,
        identifier: String,
    ): ClaimStatus? = outcome.documents
        .firstOrNull { it.docType == docType }
        ?.claims
        ?.firstOrNull { it.identifier == identifier }
        ?.status

    @Test
    fun requestedAndPresent_isReturned() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name", "given_name")),
            documents = listOf(facts(present = setOf("family_name", "given_name"))),
            documentErrors = emptyList(),
        )

        val document = outcome.documents.single()
        assertEquals(MDL, document.docType)
        assertEquals(DocumentRequestStatus.RETURNED, document.status)
        assertNull(document.errorCode)
        assertEquals(2, document.claims.size)
        assertTrue(document.claims.all { it.status == ClaimStatus.RETURNED })
        assertTrue(document.claims.all { it.errorCode == null })
    }

    @Test
    fun requestedAndDeclaredInErrors_isWithheldWithCode() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name", "portrait")),
            documents = listOf(
                facts(present = setOf("family_name"), errors = mapOf("portrait" to 0))
            ),
            documentErrors = emptyList(),
        )

        val portrait = outcome.documents.single().claims.single { it.identifier == "portrait" }
        assertEquals(ClaimStatus.WITHHELD, portrait.status)
        assertEquals(0, portrait.errorCode)
        assertEquals(NS, portrait.nameSpace)
    }

    @Test
    fun requestedAndSilentlyOmitted_isAbsent() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name", "portrait")),
            documents = listOf(facts(present = setOf("family_name"))),
            documentErrors = emptyList(),
        )

        val portrait = outcome.documents.single().claims.single { it.identifier == "portrait" }
        assertEquals(ClaimStatus.ABSENT, portrait.status)
        assertNull(portrait.errorCode)
    }

    @Test
    fun presentButNotRequested_isUnrequested() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = listOf(facts(present = setOf("family_name", "birth_date"))),
            documentErrors = emptyList(),
        )

        assertEquals(ClaimStatus.RETURNED, statusOf(outcome, MDL, "family_name"))
        assertEquals(ClaimStatus.UNREQUESTED, statusOf(outcome, MDL, "birth_date"))
    }

    @Test
    fun errorForClaimNeitherRequestedNorPresent_isIgnored() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = listOf(
                facts(present = setOf("family_name"), errors = mapOf("nickname" to 0))
            ),
            documentErrors = emptyList(),
        )

        assertEquals(1, outcome.documents.single().claims.size)
        assertNull(statusOf(outcome, MDL, "nickname"))
    }

    @Test
    fun docTypeDeclaredInDocumentErrors_isDenied() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = emptyList(),
            documentErrors = listOf(mapOf(MDL to 0)),
        )

        val document = outcome.documents.single()
        assertEquals(DocumentRequestStatus.DENIED, document.status)
        assertEquals(0, document.errorCode)
        assertTrue(document.claims.isEmpty())
    }

    @Test
    fun docTypeRequestedAndNeverReturned_isAbsent() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = emptyList(),
            documentErrors = emptyList(),
        )

        val document = outcome.documents.single()
        assertEquals(DocumentRequestStatus.ABSENT, document.status)
        assertNull(document.errorCode)
        assertTrue(document.claims.isEmpty())
    }

    @Test
    fun emptyResponse_marksEveryRequestedDocTypeAbsent() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name"), request(PID, "family_name")),
            documents = emptyList(),
            documentErrors = emptyList(),
        )

        assertEquals(2, outcome.documents.size)
        assertTrue(outcome.documents.all { it.status == DocumentRequestStatus.ABSENT })
    }

    @Test
    fun sameDocTypeRequestedTwice_producesOneEntryWithUnionOfClaims() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name"), request(MDL, "portrait")),
            documents = listOf(facts(present = setOf("family_name"))),
            documentErrors = emptyList(),
        )

        val document = outcome.documents.single()
        assertEquals(2, document.claims.size)
        assertEquals(ClaimStatus.RETURNED, statusOf(outcome, MDL, "family_name"))
        assertEquals(ClaimStatus.ABSENT, statusOf(outcome, MDL, "portrait"))
    }

    @Test
    fun twoDocumentsOfSameDocType_reconcileOnUnionOfElements() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name", "portrait")),
            documents = listOf(
                facts(present = setOf("family_name")),
                facts(present = setOf("portrait")),
            ),
            documentErrors = emptyList(),
        )

        val document = outcome.documents.single()
        assertEquals(DocumentRequestStatus.RETURNED, document.status)
        assertEquals(ClaimStatus.RETURNED, statusOf(outcome, MDL, "family_name"))
        assertEquals(ClaimStatus.RETURNED, statusOf(outcome, MDL, "portrait"))
    }

    @Test
    fun docTypeBothDeniedAndReturned_isReturnedAndKeepsTheCode() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = listOf(facts(present = setOf("family_name"))),
            documentErrors = listOf(mapOf(MDL to 0)),
        )

        val document = outcome.documents.single()
        assertEquals(DocumentRequestStatus.RETURNED, document.status)
        assertEquals(0, document.errorCode)
    }

    @Test
    fun duplicateDocumentErrorsForSameDocType_firstCodeWins() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = emptyList(),
            documentErrors = listOf(mapOf(MDL to 7), mapOf(MDL to 9)),
        )

        assertEquals(7, outcome.documents.single().errorCode)
    }

    @Test
    fun returnedDocTypeNeverRequested_doesNotAppear() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(request(MDL, "family_name")),
            documents = listOf(
                facts(present = setOf("family_name")),
                facts(docType = PID, namespace = PID_NS, present = setOf("family_name")),
            ),
            documentErrors = listOf(mapOf("org.iso.18013.5.1.unknown" to 0)),
        )

        assertEquals(listOf(MDL), outcome.documents.map { it.docType })
    }

    @Test
    fun claimsOfDifferentNamespacesAreKeptDistinct() {
        val outcome = RequestReconciler.reconcile(
            requests = listOf(
                VerificationRequest(
                    docType = MDL,
                    requestedClaims = mapOf(
                        NS to mapOf("family_name" to false),
                        PID_NS to mapOf("family_name" to false),
                    ),
                )
            ),
            documents = listOf(facts(present = setOf("family_name"))),
            documentErrors = emptyList(),
        )

        val claims = outcome.documents.single().claims
        assertEquals(2, claims.size)
        assertEquals(ClaimStatus.RETURNED, claims.single { it.nameSpace == NS }.status)
        assertEquals(ClaimStatus.ABSENT, claims.single { it.nameSpace == PID_NS }.status)
    }
}
