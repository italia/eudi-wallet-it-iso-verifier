package it.infocert.proximityverification.crl

import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CrlMetaExtractorTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    @Test
    @OptIn(ExperimentalTime::class)
    fun extractReturnsMetaForValidCrl() = runTest {
        // U01-SC-05 / U01-SC-10: a valid CRL → both epoch fields populated.
        val thisUpdate = Instant.fromEpochSeconds(1_700_000_000L)
        val nextUpdate = Instant.fromEpochSeconds(1_700_000_000L + 86_400L)
        val bytes: ByteArray = CrlTestSupport.buildTestCrlBytes(
            thisUpdate = thisUpdate,
            nextUpdate = nextUpdate
        )

        val meta = CrlMetaExtractor.extract(bytes)

        assertEquals(thisUpdate.epochSeconds, meta.thisUpdateEpochSeconds)
        assertEquals(nextUpdate.epochSeconds, meta.nextUpdateEpochSeconds)
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun extractReturnsMetaWithNullNextUpdateWhenAbsent() = runTest {
        // U01-SC-05: nextUpdate is optional in RFC 5280 — extractor must
        // surface that as null without throwing.
        val thisUpdate = Instant.fromEpochSeconds(1_650_000_000L)
        val bytes: ByteArray = CrlTestSupport.buildTestCrlBytes(
            thisUpdate = thisUpdate,
            nextUpdate = null
        )

        val meta = CrlMetaExtractor.extract(bytes)

        assertEquals(thisUpdate.epochSeconds, meta.thisUpdateEpochSeconds)
        assertNull(meta.nextUpdateEpochSeconds)
    }

    @Test
    fun extractReturnsEmptyMetaForMalformedBytes() {
        // U01-SC-05: garbage bytes → CrlMeta() with both fields null, no throw.
        val meta = CrlMetaExtractor.extract(byteArrayOf(0x01, 0x02, 0x03))

        assertNull(meta.thisUpdateEpochSeconds)
        assertNull(meta.nextUpdateEpochSeconds)
    }

    @Test
    fun extractReturnsEmptyMetaForEmptyBytes() {
        // U01-SC-05: empty input → CrlMeta() with both fields null, no throw.
        val meta = CrlMetaExtractor.extract(ByteArray(0))

        assertNull(meta.thisUpdateEpochSeconds)
        assertNull(meta.nextUpdateEpochSeconds)
    }
}
