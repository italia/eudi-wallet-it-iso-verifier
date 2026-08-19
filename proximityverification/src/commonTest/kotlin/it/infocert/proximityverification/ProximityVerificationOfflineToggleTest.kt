package it.infocert.proximityverification

import it.infocert.proximityverification.crl.CrlStore
import it.infocert.proximityverification.crl.CrlTestSupport
import it.infocert.proximityverification.crl.model.CrlMeta
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.status.StatusListStore
import it.infocert.proximityverification.status.StatusListTestSupport
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.StatusListMeta
import it.infocert.proximityverification.status.model.StatusListPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProximityVerificationOfflineToggleTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    @Test
    fun freshStorageReportsDisabled() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 0L })

        assertFalse(store.isEnabled())
    }

    @Test
    fun setEnabledTrueIsObservable() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 0L })

        store.setEnabled(true)

        assertTrue(store.isEnabled())
        assertEquals("true", storage.getElement(StatusListStore.STORAGE_KEY_ENABLED))
    }

    @Test
    fun disableTransitionResetsFlagAndClearsEntries() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 0L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = null)
        store.put("https://example.com/sl", payload, meta)
        store.setEnabled(true)

        // simulate the body of disableStatusListsForOfflineUse
        store.setEnabled(false)
        store.clearEntries()

        assertFalse(store.isEnabled())
        assertNull(store.get("https://example.com/sl"))
    }

    @Test
    fun storageWipeEquivalentToFreshInstall() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 0L })
        store.setEnabled(true)
        val payload = StatusListPayload(byteArrayOf(0x02), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = null)
        store.put("https://example.com/sl", payload, meta)

        // simulate the relevant lines of clearLibraryLocalStorage
        storage.clear()

        assertFalse(store.isEnabled())
        assertNull(store.get("https://example.com/sl"))
    }

    // ── unit-02 (CRL prefetch lifecycle) ────────────────────────────────
    // Pure-storage simulations of the enable / disable / wipe interactions
    // on the CrlStore, mirroring the existing StatusListStore pattern. They
    // exercise the same primitives that ProximityVerification.* invokes,
    // without going through the full initialize-relying-party setup.

    @Test
    fun crlStoreFreshIsEmpty() = runTest {
        val storage = CrlTestSupport.inMemoryStorage()
        val store = CrlStore(storage = storage, clock = { 0L })

        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun crlStoreClearAfterEnableSimulation() = runTest {
        val storage = CrlTestSupport.inMemoryStorage()
        val store = CrlStore(storage = storage, clock = { 0L })
        val cert = CrlTestSupport.buildTestCert(serial = 1L, issuer = "CN=CrlIssuer")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=CrlIssuer")
        store.put(
            cert = cert,
            crlUri = "https://crl.example/test.crl",
            payloadBytes = crlBytes,
            meta = CrlMeta(thisUpdateEpochSeconds = 1L, nextUpdateEpochSeconds = 2L)
        )
        assertEquals(1, store.listAll().size)

        // simulate the body of disableStatusListsForOfflineUse for CRL
        store.clearEntries()

        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun clearLibraryLocalStorageWipesCrlStore() = runTest {
        val storage = CrlTestSupport.inMemoryStorage()
        val store = CrlStore(storage = storage, clock = { 0L })
        val cert = CrlTestSupport.buildTestCert(serial = 2L, issuer = "CN=Wiped")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=Wiped")
        store.put(
            cert = cert,
            crlUri = "https://crl.example/wiped.crl",
            payloadBytes = crlBytes,
            meta = CrlMeta()
        )
        // Pre-condition: the cache key is populated.
        assertNotNull(storage.getElement(CrlStore.STORAGE_KEY))

        // simulate the relevant lines of clearLibraryLocalStorage:
        // best-effort wipe of CrlStore.STORAGE_KEY.
        storage.clearElement(CrlStore.STORAGE_KEY)

        assertNull(storage.getElement(CrlStore.STORAGE_KEY))
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun combinedFailuresIndependence() = runTest {
        // U02-SC-11 partial coverage: status list and CRL stores share no
        // global state — a failure simulation in one must remain
        // observable side-by-side with success in the other.
        val statusListStorage = StatusListTestSupport.inMemoryStorage()
        val crlStorage = CrlTestSupport.inMemoryStorage()
        val statusListStore = StatusListStore(storage = statusListStorage, clock = { 0L })
        val crlStore = CrlStore(storage = crlStorage, clock = { 0L })

        // status list: success path (put + read back).
        val payload = StatusListPayload(byteArrayOf(0x03), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = null)
        statusListStore.put("https://example.com/sl", payload, meta)

        // CRL: success path (put + listAll).
        val cert = CrlTestSupport.buildTestCert(serial = 3L, issuer = "CN=Indep")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=Indep")
        crlStore.put(
            cert = cert,
            crlUri = "https://crl.example/indep.crl",
            payloadBytes = crlBytes,
            meta = CrlMeta()
        )

        // Both stores observable side by side.
        assertNotNull(statusListStore.get("https://example.com/sl"))
        assertEquals(1, crlStore.listAll().size)

        // Clearing one does NOT clear the other.
        statusListStore.clearEntries()
        assertNull(statusListStore.get("https://example.com/sl"))
        assertEquals(1, crlStore.listAll().size)
    }
}
