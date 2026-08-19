package it.infocert.proximityverification

import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheckType
import it.infocert.proximityverification.core.models.document.DocumentData
import it.infocert.proximityverification.core.models.document.DocumentVerificationResult
import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.reconciliation.RequestReconciler
import it.infocert.proximityverification.core.reconciliation.ReturnedDocumentFacts
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.fixtures.Corruption
import it.infocert.proximityverification.fixtures.DeviceResponseFixtures
import it.infocert.proximityverification.fixtures.DocSpec
import kotlinx.coroutines.test.runTest
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.toDataItem
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.response.MdocDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Verifica per-documento della DeviceResponse.
 *
 * Design: `docs/design/2026-07-31_per-document-verification.md`.
 *
 * `DeviceResponse.verify()` di multipaz è tutto-o-niente: solleva `IllegalStateException` sia per
 * fallimenti d'integrità (firma MSO, docType mismatch, digest, `deviceAuth`) sia per un MSO fuori
 * finestra di validità. Il ktm collassava tutti i casi in `PVE04`, rendendo irraggiungibile
 * l'esito outcome-side atteso dal consumer, cioè `MSO_VALIDITY = FAILED` invece di un PVE.
 *
 * I test girano sul seam [verifyDeviceResponseDocuments], che è esattamente il blocco di
 * parsing/verifica usato da `VerificationSession.runVerificationWithResolvedTransport`: quello che
 * qui è un `ProximityVerificationException` con codice `PVE04` diventa un
 * `VerificationOutcome.ProtocolFailed` (vedi `ProtocolFailedCodeTest`), e i documenti restituiti
 * sono quelli che finiscono nel `DocumentReceived`.
 */
@OptIn(ExperimentalTime::class)
class DeviceResponsePerDocumentVerificationTest {

    private val documentTypeRepository = DocumentTypeRepository()

    // I logger sono stato globale di processo: si silenziano per la durata della classe e si
    // ripristinano, senza lasciare muta l'intera JVM di test.
    private var loggers: DeviceResponseFixtures.LoggerRestore? = null

    @BeforeTest
    fun silenceLoggers() {
        loggers = DeviceResponseFixtures.silenceLoggers()
    }

    @AfterTest
    fun restoreLoggers() {
        loggers?.restore()
    }

    private suspend fun verify(
        encoded: ByteArray,
        now: Instant,
        onPerDocumentFallback: () -> Unit = {},
    ): List<MdocDocument> = verifyResponse(encoded, now, onPerDocumentFallback).documents

    private suspend fun verifyResponse(
        encoded: ByteArray,
        now: Instant,
        onPerDocumentFallback: () -> Unit = {},
    ): VerifiedDeviceResponse =
        verifyDeviceResponseDocuments(
            root = Cbor.decode(encoded),
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            now = now,
            onPerDocumentFallback = onPerDocumentFallback,
        )

    private suspend fun toResult(document: MdocDocument): DocumentVerificationResult =
        DocumentData.toDocumentVerificationResult(
            document = document,
            documentTypeRepository = documentTypeRepository,
            issuerTrustManager = null,
        )

    private fun DocumentVerificationResult.check(type: VerificationCheckType): CheckStatus? =
        checks.firstOrNull { it.type == type }?.status

    private suspend fun assertPve04(encoded: ByteArray, now: Instant) {
        val exception = assertFailsWith(ProximityVerificationException::class) { verify(encoded, now) }
        assertEquals("PVE04", exception.code.code)
    }

    // -------------------------------------------------------------------------------------------
    // Finestra di validità: nessun PVE, esito outcome-side
    // -------------------------------------------------------------------------------------------

    @Test
    fun msoExpired_singleDoc_returnsDocumentWithFailedValidity() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now - 60.days, validUntil = now - 30.days)),
        )

        val documents = verify(encoded, now)

        assertEquals(1, documents.size)
        val result = toResult(documents.single())
        assertEquals(CheckStatus.FAILED, result.check(VerificationCheckType.MSO_VALIDITY))
    }

    @Test
    fun msoNotYetValid_singleDoc_returnsDocumentWithFailedValidity() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now + 30.days, validUntil = now + 60.days)),
        )

        val documents = verify(encoded, now)

        assertEquals(1, documents.size)
        val result = toResult(documents.single())
        assertEquals(CheckStatus.FAILED, result.check(VerificationCheckType.MSO_VALIDITY))
    }

    // -------------------------------------------------------------------------------------------
    // Integrità: PVE04, come oggi
    // -------------------------------------------------------------------------------------------

    @Test
    fun msoSignatureBroken_raisesPve04() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    corruption = Corruption.MsoSignature,
                )
            ),
        )
        assertPve04(encoded, now)
    }

    @Test
    fun deviceAuthBroken_raisesPve04() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    corruption = Corruption.DeviceAuth,
                )
            ),
        )
        assertPve04(encoded, now)
    }

    @Test
    fun docTypeMismatch_raisesPve04() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    corruption = Corruption.DocTypeMismatch,
                )
            ),
        )
        assertPve04(encoded, now)
    }

    @Test
    fun digestMismatch_raisesPve04() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    corruption = Corruption.DigestMismatch,
                )
            ),
        )
        assertPve04(encoded, now)
    }

    /**
     * Un MSO con `validFrom > validUntil` è malformato, non scaduto: `coerceIn` solleverebbe, quindi
     * si usa l'orologio reale e la verifica fallisce sulla finestra → PVE04.
     */
    @Test
    fun invertedWindow_raisesPve04() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now + 30.days, validUntil = now - 30.days)),
        )
        assertPve04(encoded, now)
    }

    // -------------------------------------------------------------------------------------------
    // Fail-closed sui documenti non-mdoc
    // -------------------------------------------------------------------------------------------

    /**
     * Il giro per-documento sa isolare solo i documenti mdoc. In una response che contiene anche
     * `otherDocuments` (SD-JWT), `zkDocuments` o `encryptedDocuments`, il fallimento aggregato può
     * venire da lì: verificare i soli mdoc e dichiarare successo trasformerebbe una response con
     * qualcosa di crittograficamente rotto in una response valida.
     *
     * Qui l'mdoc è **fuori finestra** — il caso che senza documenti non-mdoc darebbe
     * `MSO_VALIDITY = FAILED` outcome-side. La presenza dell'SD-JWT lo riporta a PVE04.
     */
    @Test
    fun mixedResponseWithOtherDocuments_raisesPve04InsteadOfIsolatingMdocs() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now - 60.days, validUntil = now - 30.days)),
            otherDocumentFormats = listOf("sd-jwt+kb"),
        )

        val exception = assertFailsWith(ProximityVerificationException::class) { verify(encoded, now) }

        assertEquals("PVE04", exception.code.code)
        assertTrue(
            exception.message.contains("otherDocuments"),
            "il messaggio deve dire perché non si è isolato: ${exception.message}"
        )
    }

    /**
     * Il contrappunto: senza documenti non-mdoc lo stesso mdoc fuori finestra passa e produce l'esito
     * outcome-side. Serve a dimostrare che il fail-closed scatta per la presenza di quei documenti, non
     * per altro.
     */
    @Test
    fun sameExpiredMdocWithoutOtherDocuments_stillReturnsTheDocument() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now - 60.days, validUntil = now - 30.days)),
        )

        assertEquals(1, verify(encoded, now).size)
    }

    // -------------------------------------------------------------------------------------------
    // Response multi-documento: un documento non abbatte gli altri
    // -------------------------------------------------------------------------------------------

    @Test
    fun twoDocs_secondExpired_firstSurvives() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(validFrom = validFrom, validUntil = validUntil),
                DocSpec(
                    docType = DeviceResponseFixtures.PHOTO_ID_DOCTYPE,
                    namespaces = DeviceResponseFixtures.photoIdNamespaces(),
                    validFrom = now - 60.days,
                    validUntil = now - 30.days,
                ),
            ),
        )

        val documents = verify(encoded, now)

        assertEquals(2, documents.size)
        assertEquals(DeviceResponseFixtures.MDL_DOCTYPE, documents[0].docType)
        assertEquals(DeviceResponseFixtures.PHOTO_ID_DOCTYPE, documents[1].docType)
        assertEquals(CheckStatus.PASSED, toResult(documents[0]).check(VerificationCheckType.MSO_VALIDITY))
        assertEquals(CheckStatus.FAILED, toResult(documents[1]).check(VerificationCheckType.MSO_VALIDITY))
    }

    /**
     * Rischio residuo accettato dal design (§4.1): un documento con integrità genuinamente
     * compromessa abbatte la sessione, anche se l'altro documento è valido.
     */
    @Test
    fun twoDocs_secondCorrupted_raisesPve04() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(validFrom = validFrom, validUntil = validUntil),
                DocSpec(
                    docType = DeviceResponseFixtures.PHOTO_ID_DOCTYPE,
                    namespaces = DeviceResponseFixtures.photoIdNamespaces(),
                    validFrom = validFrom,
                    validUntil = validUntil,
                    corruption = Corruption.MsoSignature,
                ),
            ),
        )
        assertPve04(encoded, now)
    }

    @Test
    fun twoDocs_bothValid_noRegression() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(validFrom = validFrom, validUntil = validUntil),
                DocSpec(
                    docType = DeviceResponseFixtures.PHOTO_ID_DOCTYPE,
                    namespaces = DeviceResponseFixtures.photoIdNamespaces(),
                    validFrom = validFrom,
                    validUntil = validUntil,
                ),
            ),
        )

        val documents = verify(encoded, now)

        assertEquals(2, documents.size)
        documents.forEach { document ->
            assertEquals(CheckStatus.PASSED, toResult(document).check(VerificationCheckType.MSO_VALIDITY))
        }
    }

    // -------------------------------------------------------------------------------------------
    // Percorso nominale: fast path, invariato
    // -------------------------------------------------------------------------------------------

    @Test
    fun nominalSingleDoc_unchanged() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = validFrom, validUntil = validUntil)),
        )

        val documents = verify(encoded, now)

        assertEquals(1, documents.size)
        val result = toResult(documents.single())
        assertEquals(DeviceResponseFixtures.MDL_DOCTYPE, result.docType)
        assertEquals(CheckStatus.PASSED, result.check(VerificationCheckType.MSO_VALIDITY))
        assertTrue(result.data.isNotEmpty(), "i data element del documento devono essere presenti")
    }

    /**
     * Il caso nominale non deve nemmeno sfiorare il giro per-documento: se `verify()` aggregata
     * passa, la lista arriva da lì. Lo si misura con un contatore sul fallback.
     */
    @Test
    fun nominalSingleDoc_doesNotEnterPerDocumentFallback() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = validFrom, validUntil = validUntil)),
        )

        var fallbacks = 0
        verify(encoded, now) { fallbacks++ }
        assertEquals(0, fallbacks, "il fast path non deve ricadere sul giro per-documento")
    }

    @Test
    fun msoExpired_singleDoc_entersPerDocumentFallback() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now - 60.days, validUntil = now - 30.days)),
        )

        var fallbacks = 0
        verify(encoded, now) { fallbacks++ }
        assertEquals(1, fallbacks)
    }

    // -------------------------------------------------------------------------------------------
    // Proiezione verso la riconciliazione: solo issuer-signed
    // -------------------------------------------------------------------------------------------

    @Test
    fun facts_carryIssuerSignedElementsAndErrors() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    errors = mapOf(
                        DeviceResponseFixtures.MDL_NAMESPACE to mapOf("portrait" to 0)
                    ),
                )
            ),
        )

        val facts = ReturnedDocumentFacts.from(verify(encoded, now).single())

        assertEquals(DeviceResponseFixtures.MDL_DOCTYPE, facts.docType)
        assertEquals(
            setOf("family_name", "given_name"),
            facts.elements[DeviceResponseFixtures.MDL_NAMESPACE],
        )
        assertEquals(
            0,
            facts.errors[DeviceResponseFixtures.MDL_NAMESPACE]?.get("portrait"),
        )
    }

    @Test
    fun facts_dropDeviceSignedElements() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    deviceNamespaces = mapOf(
                        DeviceResponseFixtures.MDL_NAMESPACE to mapOf(
                            "portrait" to "not-a-real-photo".toDataItem()
                        )
                    ),
                )
            ),
        )
        val document = verify(encoded, now).single()

        // Il documento *contiene* l'elemento device-signed…
        assertEquals(
            setOf("portrait"),
            document.deviceNamespaces.data[DeviceResponseFixtures.MDL_NAMESPACE]?.keys,
        )
        // …ma la proiezione non lo vede, quindi la riconciliazione lo dirà ABSENT.
        val facts = ReturnedDocumentFacts.from(document)
        assertTrue("portrait" !in facts.elements[DeviceResponseFixtures.MDL_NAMESPACE].orEmpty())
    }

    // -------------------------------------------------------------------------------------------
    // documentErrors: tipizzati e presenti su entrambi i rami di verifica
    // -------------------------------------------------------------------------------------------

    @Test
    fun documentErrors_areReturnedOnFastPath() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = validFrom, validUntil = validUntil)),
            documentErrors = listOf(mapOf(DeviceResponseFixtures.PHOTO_ID_DOCTYPE to 0)),
        )

        var fellBack = false
        val verified = verifyResponse(encoded, now) { fellBack = true }

        assertTrue(!fellBack, "il caso nominale non deve ricadere sul giro per-documento")
        assertEquals(1, verified.documents.size)
        assertEquals(
            listOf(mapOf(DeviceResponseFixtures.PHOTO_ID_DOCTYPE to 0)),
            verified.documentErrors,
        )
    }

    @Test
    fun documentErrors_areReturnedOnPerDocumentFallback() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        // MSO scaduto → la verify aggregata fallisce e si entra nel giro per-documento.
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = now - 60.days, validUntil = now - 30.days)),
            documentErrors = listOf(mapOf(DeviceResponseFixtures.PHOTO_ID_DOCTYPE to 0)),
        )

        var fellBack = false
        val verified = verifyResponse(encoded, now) { fellBack = true }

        assertTrue(fellBack, "un MSO fuori finestra deve far ricadere sul giro per-documento")
        assertEquals(1, verified.documents.size)
        assertEquals(
            listOf(mapOf(DeviceResponseFixtures.PHOTO_ID_DOCTYPE to 0)),
            verified.documentErrors,
        )
    }

    // -------------------------------------------------------------------------------------------
    // Integrazione: response firmata → verifica → riconciliazione
    // -------------------------------------------------------------------------------------------

    @Test
    fun signedResponse_isReconciledAgainstTheRequest() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    errors = mapOf(
                        DeviceResponseFixtures.MDL_NAMESPACE to mapOf("portrait" to 0)
                    ),
                )
            ),
            documentErrors = listOf(mapOf(DeviceResponseFixtures.PHOTO_ID_DOCTYPE to 0)),
        )
        val requests = listOf(
            VerificationRequest(
                docType = DeviceResponseFixtures.MDL_DOCTYPE,
                requestedClaims = mapOf(
                    DeviceResponseFixtures.MDL_NAMESPACE to mapOf(
                        "family_name" to false,
                        "portrait" to false,
                        "birth_date" to false,
                    )
                ),
            ),
            VerificationRequest(
                docType = DeviceResponseFixtures.PHOTO_ID_DOCTYPE,
                requestedClaims = mapOf(
                    DeviceResponseFixtures.PHOTO_ID_NAMESPACE to mapOf("family_name" to false)
                ),
            ),
        )

        val verified = verifyResponse(encoded, now)
        val outcome = RequestReconciler.reconcile(
            requests = requests,
            documents = verified.documents.map(ReturnedDocumentFacts::from),
            documentErrors = verified.documentErrors,
        )

        val mdl = outcome.documents.single { it.docType == DeviceResponseFixtures.MDL_DOCTYPE }
        assertEquals(DocumentRequestStatus.RETURNED, mdl.status)
        assertEquals(
            ClaimStatus.RETURNED,
            mdl.claims.single { it.identifier == "family_name" }.status,
        )
        // Dichiarato in errors: negato esplicitamente.
        assertEquals(ClaimStatus.WITHHELD, mdl.claims.single { it.identifier == "portrait" }.status)
        // Non dichiarato: omesso in silenzio, il caso che i soli errors non rileverebbero.
        assertEquals(ClaimStatus.ABSENT, mdl.claims.single { it.identifier == "birth_date" }.status)
        // given_name è nella fixture ma non nella richiesta.
        assertEquals(
            ClaimStatus.UNREQUESTED,
            mdl.claims.single { it.identifier == "given_name" }.status,
        )

        val photoId = outcome.documents.single { it.docType == DeviceResponseFixtures.PHOTO_ID_DOCTYPE }
        assertEquals(DocumentRequestStatus.DENIED, photoId.status)
        assertEquals(0, photoId.errorCode)
    }
}
