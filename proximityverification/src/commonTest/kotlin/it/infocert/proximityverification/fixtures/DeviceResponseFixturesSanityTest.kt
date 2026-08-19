package it.infocert.proximityverification.fixtures

import kotlinx.coroutines.test.runTest
import org.multipaz.cbor.Cbor
import org.multipaz.mdoc.response.DeviceResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Sanità delle fixture: verifica che [DeviceResponseFixtures] produca davvero quello che dice,
 * misurato con la verifica **aggregata** di multipaz (`DeviceResponse.verify()`), cioè il
 * comportamento in essere prima dell'intervento per-documento.
 *
 * Senza questi test il rischio è una fixture che passa (o fallisce) per il motivo sbagliato, e i
 * test della verifica per-documento diventerebbero carta straccia.
 */
@OptIn(ExperimentalTime::class)
class DeviceResponseFixturesSanityTest {

    private var loggers: DeviceResponseFixtures.LoggerRestore? = null

    @BeforeTest
    fun silenceLoggers() {
        loggers = DeviceResponseFixtures.silenceLoggers()
    }

    @AfterTest
    fun restoreLoggers() {
        loggers?.restore()
    }

    private suspend fun aggregateVerify(encoded: ByteArray, atTime: Instant) {
        val response = DeviceResponse.fromDataItem(Cbor.decode(encoded))
        response.verify(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            atTime = atTime,
        )
    }

    /**
     * multipaz 0.99.0 incapsula il fallimento del singolo documento in un
     * `IllegalStateException("Error verifying document N in DeviceResponse", cause)`: la causa
     * effettiva sta nella `cause`. Si asserisce sull'intera catena per non dipendere dal livello
     * di annidamento, che non è contratto.
     */
    private fun Throwable.messageChain(): List<String> =
        generateSequence(this as Throwable?) { it.cause }.mapNotNull { it.message }.toList()

    private suspend fun assertVerifyFailsBecause(expected: String, encoded: ByteArray, atTime: Instant) {
        val chain = assertFailsWith(IllegalStateException::class) {
            aggregateVerify(encoded, atTime)
        }.messageChain()
        assertTrue(
            chain.any { it == expected },
            "atteso '$expected' nella catena delle cause, ottenuto: $chain"
        )
    }

    @Test
    fun cleanFixtureVerifies() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow(now)
        val encoded = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(DocSpec(validFrom = validFrom, validUntil = validUntil)),
        )
        aggregateVerify(encoded, now)

        val response = DeviceResponse.fromDataItem(Cbor.decode(encoded))
        response.verify(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            eReaderKey = null,
            atTime = now,
        )
        assertEquals(1, response.documents.size)
        assertEquals(DeviceResponseFixtures.MDL_DOCTYPE, response.documents.first().docType)
        assertEquals(validFrom, response.documents.first().mso.validFrom)
        assertEquals(validUntil, response.documents.first().mso.validUntil)
    }

    /**
     * Il decode/encode CBOR di una response generata da multipaz è byte-identico: è la premessa su
     * cui poggia la ricostruzione della response mono-documento dal `DataItem` grezzo.
     */
    @Test
    fun cborRoundTripIsByteIdentical() = runTest {
        val (validFrom, validUntil) = DeviceResponseFixtures.validWindow()
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
        assertContentEquals(encoded, Cbor.encode(Cbor.decode(encoded)))
    }

    @Test
    fun msoSignatureCorruptionThrows() = runTest {
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
        assertVerifyFailsBecause("Signature on MSO failed to verify", encoded, now)
    }

    @Test
    fun deviceAuthCorruptionThrows() = runTest {
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
        assertVerifyFailsBecause("Device authentication signature failed to verify", encoded, now)
    }

    @Test
    fun docTypeMismatchCorruptionThrows() = runTest {
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
        assertVerifyFailsBecause("Mismatch between docType in document and MSO", encoded, now)
    }

    @Test
    fun digestMismatchCorruptionThrows() = runTest {
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
        val chain = assertFailsWith(IllegalStateException::class) {
            aggregateVerify(encoded, now)
        }.messageChain()
        assertTrue(
            chain.any { it.startsWith("Digest mismatch for data element ") },
            "atteso un digest mismatch nella catena delle cause, ottenuto: $chain"
        )
    }

    /**
     * Le due cause di finestra: è esattamente questo il fallimento che l'intervento per-documento
     * deve smettere di trattare come errore di protocollo.
     */
    @Test
    fun outOfValidityWindowThrowsToday() = runTest {
        val now = DeviceResponseFixtures.nowWholeSeconds()
        val expired = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(validFrom = now - 60.days, validUntil = now - 30.days)
            ),
        )
        assertVerifyFailsBecause("MSO is not valid anymore", expired, now)

        val notYetValid = DeviceResponseFixtures.buildDeviceResponse(
            sessionTranscript = DeviceResponseFixtures.sessionTranscript(),
            documents = listOf(
                DocSpec(validFrom = now + 30.days, validUntil = now + 60.days)
            ),
        )
        assertVerifyFailsBecause("MSO is not yet valid", notYetValid, now)
    }
}
