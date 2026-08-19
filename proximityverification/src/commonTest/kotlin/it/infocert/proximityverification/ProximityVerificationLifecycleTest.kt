package it.infocert.proximityverification

import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.handlers.DispatcherProvider
import it.infocert.proximityverification.core.models.ProtocolFailureReason
import it.infocert.proximityverification.core.models.VerificationOutcome
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.lifecycle.VerificationStep
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProximityVerificationLifecycleTest {

    private val noOpLoggerDelegate: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    @BeforeTest
    fun setUp(): Unit {
        LoggerConfig.delegateLogger = noOpLoggerDelegate
        ProximityVerification.close()
        ProximityVerification.configureDispatcherProvider(null)
    }

    @AfterTest
    fun tearDown(): Unit {
        ProximityVerification.close()
        ProximityVerification.configureDispatcherProvider(null)
        LoggerConfig.delegateLogger = null
    }

    @Test
    fun closeIsIdempotent(): TestResult = runTest {
        ProximityVerification.close()
        ProximityVerification.close()
        assertNull(ProximityVerification.getSessionScope())
    }

    /**
     * Regressione: il catalogo delle docType è uno stato di processo che deve
     * tornare al baseline builtin quando la sessione viene chiusa/reinizializzata
     * (via `cancelSessionScope`). Altrimenti le docType risolte da un registry
     * (es. ambiente PRE) sopravvivono a un successivo init su un ambiente che non
     * risolve nulla (es. PROD senza federation), facendo apparire un catalogo
     * "risolto" mentre l'offline riporta PVE37.
     */
    @Test
    fun closeResetsCatalogToBuiltInDroppingRegistryDocTypes(): TestResult = runTest {
        val catalog = ProximityVerification.documentTypeCatalog
        val builtInDocType = "org.iso.18013.5.1.mDL"
        val staleDocType = "it.test.stale.registry.doctype"

        // Simula una docType iniettata da una risoluzione registry precedente.
        catalog.registerFromJson("""{ "doctype": "$staleDocType" }""")
        assertTrue(catalog.isRegistered(staleDocType))

        ProximityVerification.close()

        assertTrue(
            catalog.isRegistered(builtInDocType),
            "il builtin mDL deve essere ri-seminato dopo il reset"
        )
        assertFalse(
            catalog.isRegistered(staleDocType),
            "le docType risolte da registry non devono sopravvivere al reset di sessione"
        )
    }

    @Test
    fun closeCancelsSessionScope(): TestResult = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ProximityVerification.configureDispatcherProvider(TestDispatcherProvider(dispatcher))

        ProximityVerification.createSessionScopeForTesting()
        assertNotNull(ProximityVerification.getSessionScope())

        ProximityVerification.close()
        runCurrent()

        assertNull(ProximityVerification.getSessionScope())
    }

    @Test
    fun createSessionScopeForTestingCancelsPreviousScope(): TestResult = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ProximityVerification.configureDispatcherProvider(TestDispatcherProvider(dispatcher))

        val firstSessionJob = ProximityVerification.createSessionScopeForTesting()
        val secondSessionJob = ProximityVerification.createSessionScopeForTesting()
        runCurrent()

        assertTrue(firstSessionJob.isCancelled)
        assertTrue(!secondSessionJob.isCancelled)
        assertNotNull(ProximityVerification.getSessionScope())
    }

    @Test
    fun cancelActiveVerificationCancelsActiveJob(): TestResult = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ProximityVerification.configureDispatcherProvider(TestDispatcherProvider(dispatcher))
        ProximityVerification.createSessionScopeForTesting()

        val sessionScope = ProximityVerification.getSessionScope()
        assertNotNull(sessionScope)

        val activeJob = sessionScope.launch {
            awaitCancellation()
        }
        ProximityVerification.setActiveVerificationJob(activeJob)
        ProximityVerification.cancelActiveVerification()
        runCurrent()

        assertTrue(activeJob.isCancelled)
        assertNull(ProximityVerification.getActiveVerificationGeneration())
    }

    @Test
    fun closeCancelsActiveJobWithoutFailureCallback(): TestResult = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ProximityVerification.configureDispatcherProvider(TestDispatcherProvider(dispatcher))
        ProximityVerification.createSessionScopeForTesting()
        val callback = CallbackSpy()

        val sessionScope = ProximityVerification.getSessionScope()
        assertNotNull(sessionScope)

        val activeJob = sessionScope.launch {
            try {
                awaitCancellation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                callback.onVerificationCompleted(
                    VerificationOutcome.ProtocolFailed(
                        reason = ProtocolFailureReason.GENERIC_VERIFICATION_ERROR,
                        message = exception.message ?: "Unexpected failure"
                    )
                )
            }
        }
        ProximityVerification.setActiveVerificationJob(activeJob)

        ProximityVerification.close()
        runCurrent()

        assertTrue(activeJob.isCancelled)
        assertEquals(0, callback.failedCount)
    }

    @Test
    fun cancelActiveVerificationDoesNotTriggerFailureCallback(): TestResult = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ProximityVerification.configureDispatcherProvider(TestDispatcherProvider(dispatcher))
        ProximityVerification.createSessionScopeForTesting()
        val callback = CallbackSpy()
        val sessionScope = ProximityVerification.getSessionScope()
        assertNotNull(sessionScope)
        val activeJob = sessionScope.launch {
            try {
                awaitCancellation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                callback.onVerificationCompleted(
                    VerificationOutcome.ProtocolFailed(
                        reason = ProtocolFailureReason.GENERIC_VERIFICATION_ERROR,
                        message = exception.message ?: "Unexpected failure"
                    )
                )
            }
        }
        ProximityVerification.setActiveVerificationJob(activeJob)
        ProximityVerification.cancelActiveVerification()
        runCurrent()
        assertTrue(activeJob.isCancelled)
        assertEquals(0, callback.failedCount)
    }

    @Test
    fun startVerificationWithoutInitializeDeliversPve07ViaCallback(): TestResult = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        ProximityVerification.configureDispatcherProvider(TestDispatcherProvider(dispatcher))
        val callback = CallbackSpy()
        val settings = ProximityVerificationSettings(
            requests = listOf(
                VerificationRequest(
                    docType = "org.iso.18013.5.1.mDL",
                    requestedClaims = mapOf("org.iso.18013.5.1" to mapOf("family_name" to true))
                )
            )
        )
        ProximityVerification.startVerification("mdoc:AA", settings, callback)
        runCurrent()
        assertEquals(1, callback.failedCount)
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher
    ) : DispatcherProvider {
        override val defaultDispatcher: CoroutineDispatcher = dispatcher
        override val backgroundDispatcher: CoroutineDispatcher = dispatcher
        override val callbackDispatcher: CoroutineDispatcher = dispatcher
    }

    private class CallbackSpy : VerificationCallback {
        var failedCount: Int = 0
        var lastFailureCode: String? = null
        val steps: MutableList<VerificationStep> = mutableListOf()

        override fun onVerificationUpdate(step: VerificationStep): Unit {
            steps += step
        }

        override fun onVerificationCompleted(outcome: VerificationOutcome): Unit {
            if (outcome is VerificationOutcome.ProtocolFailed) {
                failedCount += 1
                lastFailureCode = when (outcome.reason) {
                    it.infocert.proximityverification.core.models.ProtocolFailureReason.GENERIC_VERIFICATION_ERROR -> ProximityVerificationExceptionCode.PVE07().code
                    else -> null
                }
            }
        }
    }
}
