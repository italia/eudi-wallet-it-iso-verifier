package it.infocert.proximityverification

import it.infocert.proximityverification.core.models.ProtocolFailureReason
import it.infocert.proximityverification.core.models.VerificationOutcome
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolFailedCodeTest {

    private val noOpLoggerDelegate: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    @BeforeTest
    fun setUp(): Unit {
        LoggerConfig.delegateLogger = noOpLoggerDelegate
    }

    @AfterTest
    fun tearDown(): Unit {
        LoggerConfig.delegateLogger = null
    }

    @Test
    fun mapExceptionToOutcomeAttachesRawPveCode() {
        val exception = ProximityVerificationException(
            message = "device response integrity verification failed",
            code = ProximityVerificationExceptionCode.PVE04()
        )

        val outcome = ProximityVerification.mapExceptionToOutcome(exception)

        assertEquals(ProtocolFailureReason.CRYPTO_OPERATION_FAILED, outcome.reason)
        assertEquals("PVE04", outcome.code)
    }

    @Test
    fun mapExceptionToOutcomePreservesRawCodeWhenReasonCollapsesToGenericError() {
        // PVE00 is not matched by any explicit arm of ProtocolFailureReason.fromCode,
        // so reason collapses to the GENERIC_ERROR fall-through. The raw code is the
        // only thing that preserves the precise failure identity in that case.
        val exception = ProximityVerificationException(
            message = "generic exception",
            code = ProximityVerificationExceptionCode.PVE00()
        )

        val outcome = ProximityVerification.mapExceptionToOutcome(exception)

        assertEquals(ProtocolFailureReason.GENERIC_ERROR, outcome.reason)
        assertEquals("PVE00", outcome.code)
    }

    @Test
    fun protocolFailedDefaultsCodeToNullWhenConstructedWithoutException() {
        val outcome = VerificationOutcome.ProtocolFailed(
            reason = ProtocolFailureReason.PLATFORM_CONTEXT_REQUIRED,
            message = "NFC data transfer selected after QR engagement but platformContext was not provided"
        )

        assertNull(outcome.code)
    }
}
