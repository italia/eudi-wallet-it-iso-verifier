package it.infocert.proximityverification

import it.infocert.proximityverification.core.handlers.DefaultDispatcherProvider
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentTypeRepository

/**
 * Verifies the connectivity pre-check (`PVE40`) introduced in
 * `ProximityVerification.enableStatusListsForOfflineUse`. The guard must
 * fire **after** the init guard (`PVE01`) and **before** the registry
 * guard (`PVE37`) so that:
 *   - Calling the API without `initializeRelyingPartyInstance` still
 *     throws `PVE01` (not `PVE40`), regardless of network state.
 *   - With an initialized session and the device offline, the API throws
 *     `PVE40` *before* any HTTP / store I/O.
 *   - With an initialized session and the device online, the guard is
 *     transparent and the next pre-check (`PVE37` registry) fires.
 */
class ProximityVerificationConnectivityGuardTest {

    private val noOpLoggerDelegate: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    @BeforeTest
    fun setUp(): Unit {
        // Install the no-op logger BEFORE touching `ProximityVerification` so
        // its first-time static initializer (which logs catalog registration)
        // does not call `android.util.Log` from the JVM unit-test runner.
        LoggerConfig.delegateLogger = noOpLoggerDelegate
        ProximityVerification.close()
        ProximityVerification.configureDispatcherProvider(null)
    }

    @AfterTest
    fun tearDown(): Unit {
        // Restore the production probe (reads the platform's NativeUtils) so
        // sibling tests that may rely on the default behaviour are not
        // contaminated by a leftover `{ false }` / `{ true }` override.
        ProximityVerification.networkProbe = { NativeUtils.isNetworkAvailable() }
        ProximityVerification.setVerificationSessionForTesting(null)
        ProximityVerification.close()
        ProximityVerification.configureDispatcherProvider(null)
        LoggerConfig.delegateLogger = null
    }

    @Test
    fun offlineWithoutInit_throwsPve01_notPve40(): TestResult = runTest {
        // Init has not been called → verificationSession is null. Even with
        // the device "offline", the PVE01 guard must fire first.
        ProximityVerification.networkProbe = { false }

        val exception = assertFailsWith<ProximityVerificationException> {
            ProximityVerification.enableStatusListsForOfflineUse()
        }
        assertEquals("PVE01", exception.code.code)
    }

    @Test
    fun offlineAfterInit_throwsPve40(): TestResult = runTest {
        // Simulate an initialized session, then signal offline. The guard
        // must throw PVE40 before reaching the registry pre-check (PVE37).
        ProximityVerification.setVerificationSessionForTesting(
            createMinimalVerificationSession()
        )
        ProximityVerification.networkProbe = { false }

        val exception = assertFailsWith<ProximityVerificationException> {
            ProximityVerification.enableStatusListsForOfflineUse()
        }
        assertEquals("PVE40", exception.code.code)
    }

    @Test
    fun onlineAfterInit_passesGuard_failsAtRegistry(): TestResult = runTest {
        // With the device online, the connectivity guard is transparent
        // and the next pre-check (registry → PVE37) is what fails, since
        // resolvedRegistryData is null in this minimal harness.
        ProximityVerification.setVerificationSessionForTesting(
            createMinimalVerificationSession()
        )
        ProximityVerification.networkProbe = { true }

        val exception = assertFailsWith<ProximityVerificationException> {
            ProximityVerification.enableStatusListsForOfflineUse()
        }
        assertEquals("PVE37", exception.code.code)
    }

    private fun createMinimalVerificationSession(): VerificationSession {
        return VerificationSession(
            trustManager = null,
            documentTypeRepository = DocumentTypeRepository(),
            certChain = X509CertChain(emptyList()),
            dispatcherProvider = DefaultDispatcherProvider
        )
    }
}
