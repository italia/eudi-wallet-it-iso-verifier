package it.infocert.proximityverification.logging

import org.multipaz.util.Logger as MultipazLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultipazLogBridgeTest {

    private data class Entry(val level: LogLevel, val tag: String, val message: String)

    private val captured = mutableListOf<Entry>()

    private val capturingDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) {
            captured.add(Entry(level, tag, message))
        }
    }

    @BeforeTest
    fun setUp() {
        captured.clear()
        LoggerConfig.delegateLogger = capturingDelegate
        installMultipazLogBridge()
    }

    @AfterTest
    fun tearDown() {
        LoggerConfig.delegateLogger = null
        MultipazLogger.logPrinter = null
        Logger.setLogLevel(LogLevel.INFO)
    }

    @Test
    fun debugLogReachesDelegateWhenLevelIsDebug() {
        Logger.setLogLevel(LogLevel.DEBUG)

        MultipazLogger.d("NfcDialogObserver", "NFC reader mode enabled successfully")

        assertEquals(1, captured.size)
        assertEquals(LogLevel.DEBUG, captured[0].level)
        assertEquals("NfcDialogObserver", captured[0].tag)
        assertEquals("NFC reader mode enabled successfully", captured[0].message)
    }

    @Test
    fun debugLogIsGatedAtInfoButInfoReachesDelegate() {
        Logger.setLogLevel(LogLevel.INFO)

        MultipazLogger.d("NfcDialogObserver", "onTagDiscovered")
        MultipazLogger.i("NfcPromptBinding", "engagement started")

        assertEquals(1, captured.size)
        assertEquals(LogLevel.INFO, captured[0].level)
        assertEquals("NfcPromptBinding", captured[0].tag)
        assertEquals("engagement started", captured[0].message)
    }

    @Test
    fun warningMapsToWarnAndThrowableIsAppended() {
        Logger.setLogLevel(LogLevel.DEBUG)
        val boom = IllegalStateException("tag-lost")

        MultipazLogger.w("NfcDialogObserver", "handover failed", boom)

        assertEquals(1, captured.size)
        assertEquals(LogLevel.WARN, captured[0].level)
        assertTrue(captured[0].message.startsWith("handover failed"))
        assertTrue(captured[0].message.contains(boom.toString()))
    }

    @Test
    fun internalMultipazTagIsNotForwarded() {
        Logger.setLogLevel(LogLevel.DEBUG)

        MultipazLogger.d("Logger", "internal housekeeping")

        assertTrue(captured.isEmpty())
    }

    @Test
    fun syncMultipazDebugFlagTracksLevel() {
        Logger.setLogLevel(LogLevel.TRACE)
        assertTrue(MultipazLogger.isDebugEnabled)

        Logger.setLogLevel(LogLevel.DEBUG)
        assertTrue(MultipazLogger.isDebugEnabled)

        Logger.setLogLevel(LogLevel.INFO)
        assertFalse(MultipazLogger.isDebugEnabled)

        Logger.setLogLevel(LogLevel.ERROR)
        assertFalse(MultipazLogger.isDebugEnabled)
    }
}
