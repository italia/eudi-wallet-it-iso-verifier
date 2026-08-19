package it.infocert.proximityverification

import it.infocert.proximityverification.core.models.request.ClaimOutcome
import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestOutcome
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.models.request.RequestOutcome
import it.infocert.proximityverification.fixtures.DeviceResponseFixtures
import it.infocert.proximityverification.fixtures.DocSpec
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.test.runTest
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.toDataItem
import org.multipaz.mdoc.response.MdocDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Log di forma della sessione.
 *
 * L'SDK logga **fatti**: cosa si è chiesto, cosa è arrivato, cosa il wallet ha negato. Nessuna
 * inferenza su feature dei consumer — quelle si loggano dove vivono. Questi test presidiano che i
 * fatti ci siano sempre e siano precisi: sono l'unica base su cui un consumer può diagnosticare un
 * flusso, e non devono mai degradare in silenzio.
 */
@OptIn(ExperimentalTime::class)
class VerificationSessionLogTest {

    private val captured = mutableListOf<Pair<LogLevel, String>>()

    private val capturingDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) {
            captured.add(level to message)
        }
    }

    private var loggers: DeviceResponseFixtures.LoggerRestore? = null

    @BeforeTest
    fun setUp() {
        captured.clear()
        LoggerConfig.delegateLogger = capturingDelegate
        // Silenzia multipaz (che logga incondizionatamente in parsing) senza toccare il delegate KTM
        // appena installato: i log del KTM devono restare catturabili.
        loggers = DeviceResponseFixtures.silenceLoggers()
        Logger.setLogLevel(LogLevel.INFO)
    }

    @AfterTest
    fun tearDown() {
        loggers?.restore()
        LoggerConfig.delegateLogger = null
        Logger.setLogLevel(LogLevel.INFO)
    }

    private fun lineContaining(fragment: String): String? =
        captured.map { it.second }.firstOrNull { it.contains(fragment) }

    private fun request(
        docType: String,
        claims: Map<String, Map<String, Boolean>>,
    ) = VerificationRequest(docType = docType, requestedClaims = claims)

    private val mDlPortraitOnly = request(
        DeviceResponseFixtures.MDL_DOCTYPE,
        mapOf(DeviceResponseFixtures.MDL_NAMESPACE to mapOf("portrait" to false)),
    )

    private val educationDegree = request(
        EDUCATION_DEGREE_DOCTYPE,
        mapOf(
            EDUCATION_DEGREE_NAMESPACE to mapOf(
                "tax_id_code" to false,
                "issuing_authority" to false,
                "issuing_country" to false,
                "verification" to false,
            )
        ),
    )

    // ---------------------------------------------------------------------------------------------
    // Richiesta
    // ---------------------------------------------------------------------------------------------

    @Test
    fun logRequestsReportsEveryDocTypeWithItsClaimCount() = runTest {
        VerificationSessionLog.logRequests(listOf(educationDegree, mDlPortraitOnly))

        val summary = lineContaining("Requesting 2 document(s)")
        assertTrue(summary != null, "riepilogo della richiesta assente: ${captured.map { it.second }}")
        assertTrue(summary!!.contains("[0] $EDUCATION_DEGREE_DOCTYPE — 4 claim(s)"), summary)
        assertTrue(
            summary.contains("[1] ${DeviceResponseFixtures.MDL_DOCTYPE} — 1 claim(s) (portrait)"),
            summary
        )
    }

    /**
     * L'SDK non deve dire nulla su cosa *significhi* una richiesta: nessun verdetto, nessuna "forma"
     * riconosciuta. È il confine di layer, e va presidiato da un test perché è facile reintrodurlo.
     */
    @Test
    fun logsCarryNoProductVerdict() = runTest {
        VerificationSessionLog.logRequests(listOf(educationDegree, mDlPortraitOnly))

        assertNull(lineContaining("corroboration"))
        assertNull(lineContaining("SATISFIED"))
    }

    @Test
    fun claimIdentifiersAreInlinedOnlyWhenFew() = runTest {
        VerificationSessionLog.logRequests(listOf(educationDegree, mDlPortraitOnly))

        val summary = lineContaining("Requesting 2 document(s)")!!
        // 4 claim: solo il conteggio. 1 claim: anche l'identificatore.
        assertTrue(!summary.contains("(tax_id_code"), summary)
        assertTrue(summary.contains("(portrait)"), summary)
    }

    // ---------------------------------------------------------------------------------------------
    // Risposta
    // ---------------------------------------------------------------------------------------------

    /**
     * Gli elementi immagine sono elencati per nome, classificati come li classifica l'SDK: è il fatto
     * che rende diagnosticabile qualunque flusso costruito sulle foto, senza inferenze.
     */
    @Test
    fun logResponseListsImageElementsPerDocument() = runTest {
        val documents = twoDocumentResponse(mdlCarriesPortrait = true)

        VerificationSessionLog.logResponse(documentErrors = emptyList(), documents = documents)

        val received = lineContaining("Received 2 document(s)")!!
        assertTrue(received.contains("$EDUCATION_DEGREE_DOCTYPE — 1 element(s), images=[]"), received)
        assertTrue(
            received.contains("${DeviceResponseFixtures.MDL_DOCTYPE} — 1 element(s), images=[portrait]"),
            received
        )
    }

    @Test
    fun logResponseReportsNoImageWhenThePortraitIsAbsent() = runTest {
        val documents = twoDocumentResponse(mdlCarriesPortrait = false)

        VerificationSessionLog.logResponse(documentErrors = emptyList(), documents = documents)

        val received = lineContaining("Received 2 document(s)")!!
        assertTrue(
            received.contains("${DeviceResponseFixtures.MDL_DOCTYPE} — 1 element(s), images=[]"),
            received
        )
    }

    /**
     * `signature_usual_mark` è un'immagine ma non è un ritratto: il log elenca le immagini per nome
     * proprio perché "c'è un'immagine" non equivale a "c'è la foto del titolare".
     */
    @Test
    fun logResponseNamesImagesInsteadOfCountingThem() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    docType = DeviceResponseFixtures.MDL_DOCTYPE,
                    namespaces = mapOf(
                        DeviceResponseFixtures.MDL_NAMESPACE to mapOf(
                            "signature_usual_mark" to Bstr(byteArrayOf(1, 2)),
                            "family_name" to "Rossi".toDataItem(),
                        )
                    ),
                    validFrom = validFrom,
                    validUntil = validUntil,
                )
            ),
        )
        val documents = verifyDeviceResponseDocuments(
            root = Cbor.decode(encoded),
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            now = now,
        ).documents

        VerificationSessionLog.logResponse(documentErrors = emptyList(), documents = documents)

        val received = lineContaining("Received 1 document(s)")!!
        assertTrue(received.contains("images=[signature_usual_mark]"), received)
    }

    @Test
    fun logResponseReportsDocTypesTheWalletDenied() = runTest {
        val documents = singleDocumentResponse()

        VerificationSessionLog.logResponse(
            documentErrors = listOf(mapOf(DeviceResponseFixtures.MDL_DOCTYPE to 0)),
            documents = documents,
        )

        val denied = lineContaining("Wallet returned no document for")
        assertTrue(denied != null, "i documentErrors devono essere loggati")
        assertTrue(denied!!.contains("${DeviceResponseFixtures.MDL_DOCTYPE} (error 0)"), denied)
    }

    @Test
    fun logResponseIsSilentWhenThereAreNoDocumentErrors() = runTest {
        val documents = singleDocumentResponse()

        VerificationSessionLog.logResponse(documentErrors = emptyList(), documents = documents)

        assertNull(lineContaining("Wallet returned no document for"))
    }

    /**
     * Un data element device-signed non è esposto dall'SDK e la riconciliazione lo conta assente:
     * senza questo `WARN` un claim arrivato per quella via sarebbe indistinguibile da uno mai arrivato.
     */
    @Test
    fun deviceSignedElements_produceAWarning() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    validFrom = validFrom,
                    validUntil = validUntil,
                    deviceNamespaces = mapOf(
                        DeviceResponseFixtures.MDL_NAMESPACE to mapOf("portrait" to "x".toDataItem())
                    ),
                )
            ),
        )
        val documents = verifyDeviceResponseDocuments(
            root = Cbor.decode(encoded),
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            now = now,
        ).documents

        VerificationSessionLog.logResponse(documentErrors = emptyList(), documents = documents)

        val warning = captured.single { it.first == LogLevel.WARN }.second
        assertTrue(warning.contains("device-signed"), warning)
        assertTrue(warning.contains(DeviceResponseFixtures.MDL_DOCTYPE), warning)
    }

    // ---------------------------------------------------------------------------------------------
    // Esito della riconciliazione
    // ---------------------------------------------------------------------------------------------

    @Test
    fun requestOutcome_summaryReportsCountsAtInfo() {
        val outcome = RequestOutcome(
            documents = listOf(
                DocumentRequestOutcome(
                    docType = DeviceResponseFixtures.MDL_DOCTYPE,
                    status = DocumentRequestStatus.RETURNED,
                    errorCode = null,
                    claims = listOf(
                        ClaimOutcome(
                            DeviceResponseFixtures.MDL_NAMESPACE,
                            "family_name",
                            ClaimStatus.RETURNED,
                            null,
                        ),
                        ClaimOutcome(
                            DeviceResponseFixtures.MDL_NAMESPACE,
                            "portrait",
                            ClaimStatus.WITHHELD,
                            0,
                        ),
                        ClaimOutcome(
                            DeviceResponseFixtures.MDL_NAMESPACE,
                            "sex",
                            ClaimStatus.ABSENT,
                            null,
                        ),
                        ClaimOutcome(
                            DeviceResponseFixtures.MDL_NAMESPACE,
                            "birth_date",
                            ClaimStatus.UNREQUESTED,
                            null,
                        ),
                    ),
                ),
                DocumentRequestOutcome(
                    docType = DeviceResponseFixtures.PHOTO_ID_DOCTYPE,
                    status = DocumentRequestStatus.DENIED,
                    errorCode = 0,
                    claims = emptyList(),
                ),
            )
        )

        VerificationSessionLog.logRequestOutcome(outcome)

        val summary = captured.single { it.first == LogLevel.INFO }.second
        assertTrue(summary.contains("2 document(s) requested"), summary)
        assertTrue(summary.contains("1 returned"), summary)
        assertTrue(summary.contains("1 denied"), summary)
        assertTrue(summary.contains("0 absent"), summary)
        assertTrue(summary.contains("1 returned, 1 withheld, 1 absent, 1 unrequested"), summary)
    }

    @Test
    fun requestOutcome_detailNamesWithheldAndAbsentClaimsAtDebug() {
        Logger.setLogLevel(LogLevel.DEBUG)
        val outcome = RequestOutcome(
            documents = listOf(
                DocumentRequestOutcome(
                    docType = DeviceResponseFixtures.MDL_DOCTYPE,
                    status = DocumentRequestStatus.RETURNED,
                    errorCode = null,
                    claims = listOf(
                        ClaimOutcome(
                            DeviceResponseFixtures.MDL_NAMESPACE,
                            "portrait",
                            ClaimStatus.WITHHELD,
                            0,
                        ),
                        ClaimOutcome(
                            DeviceResponseFixtures.MDL_NAMESPACE,
                            "sex",
                            ClaimStatus.ABSENT,
                            null,
                        ),
                    ),
                )
            )
        )

        VerificationSessionLog.logRequestOutcome(outcome)

        val detail = captured.single { it.first == LogLevel.DEBUG }.second
        assertTrue(detail.contains("${DeviceResponseFixtures.MDL_NAMESPACE}/portrait (0)"), detail)
        assertTrue(detail.contains("${DeviceResponseFixtures.MDL_NAMESPACE}/sex"), detail)
    }

    // ---------------------------------------------------------------------------------------------
    // Supporto
    // ---------------------------------------------------------------------------------------------

    private suspend fun twoDocumentResponse(mdlCarriesPortrait: Boolean): List<MdocDocument> {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    docType = EDUCATION_DEGREE_DOCTYPE,
                    namespaces = mapOf(
                        EDUCATION_DEGREE_NAMESPACE to mapOf("tax_id_code" to "RSSMRA80A01H501U".toDataItem())
                    ),
                    validFrom = validFrom,
                    validUntil = validUntil,
                ),
                DocSpec(
                    docType = DeviceResponseFixtures.MDL_DOCTYPE,
                    namespaces = mapOf(
                        DeviceResponseFixtures.MDL_NAMESPACE to
                            if (mdlCarriesPortrait) {
                                mapOf("portrait" to Bstr(byteArrayOf(0xff.toByte(), 0xd8.toByte())))
                            } else {
                                mapOf("family_name" to "Rossi".toDataItem())
                            }
                    ),
                    validFrom = validFrom,
                    validUntil = validUntil,
                ),
            ),
        )
        return verifyDeviceResponseDocuments(
            root = Cbor.decode(encoded),
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            now = now,
        ).documents
    }

    private suspend fun singleDocumentResponse(): List<MdocDocument> {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(
                    docType = EDUCATION_DEGREE_DOCTYPE,
                    namespaces = mapOf(
                        EDUCATION_DEGREE_NAMESPACE to mapOf("tax_id_code" to "RSSMRA80A01H501U".toDataItem())
                    ),
                    validFrom = validFrom,
                    validUntil = validUntil,
                )
            ),
        )
        return verifyDeviceResponseDocuments(
            root = Cbor.decode(encoded),
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            now = now,
        ).documents
    }

    private companion object {
        const val EDUCATION_DEGREE_DOCTYPE = "it.ipzs.wallet.ta.education_degree.1"
        const val EDUCATION_DEGREE_NAMESPACE = "it.ipzs.wallet.ta.education_degree.1"
    }
}
