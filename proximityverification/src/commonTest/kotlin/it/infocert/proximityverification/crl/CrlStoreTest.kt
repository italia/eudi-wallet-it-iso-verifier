package it.infocert.proximityverification.crl

import it.infocert.proximityverification.crl.model.CrlMeta
import it.infocert.proximityverification.core.crypto.LocalStorage
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.offline.OfflineModeFlag
import it.infocert.proximityverification.status.StatusListStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class CrlStoreTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    @Test
    fun offlineFlagIsSharedAcrossStores() {
        // U01-SC-01: the StatusListStore companion constant references
        // the new OfflineModeFlag — same string, single source of truth.
        assertEquals(OfflineModeFlag.STORAGE_KEY, StatusListStore.STORAGE_KEY_ENABLED)
        assertEquals("PROX_VER_OFFLINE_ENABLED", OfflineModeFlag.STORAGE_KEY)
    }

    @Test
    fun putAndGetRoundTrip() = runTest {
        // U01-SC-02 + U01-SC-04: put preserves all 9 fields.
        val storage = CrlTestSupport.inMemoryStorage()
        val store = CrlStore(storage = storage, clock = { 1_700_000_500_000L })
        val cert = CrlTestSupport.buildTestCert(
            serial = 0xABC123L,
            issuer = "CN=Foo CA",
            subject = "CN=Foo Subject"
        )
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val meta = CrlMeta(
            thisUpdateEpochSeconds = 1_700_000_000L,
            nextUpdateEpochSeconds = 1_700_086_400L
        )

        store.put(cert, crlUri = "https://example.com/crl/1", payloadBytes = payload, meta = meta)
        val cached = store.get(cert)

        assertNotNull(cached)
        assertEquals(computeCertKey(cert), cached.certKey)
        assertEquals("CN=Foo Subject", cached.subjectDn)
        assertEquals("CN=Foo CA", cached.issuerDn)
        assertEquals("abc123", cached.serialHex)
        assertEquals("https://example.com/crl/1", cached.crlUri)
        assertEquals(1_700_000_000L, cached.thisUpdateEpochSeconds)
        assertEquals(1_700_086_400L, cached.nextUpdateEpochSeconds)
        assertEquals(1_700_000_500_000L, cached.fetchedAtEpochMillis)
        assertContentEquals(payload, store.decodePayload(cached))
    }

    @Test
    fun getReturnsNullForUnknownCert() = runTest {
        // U01-SC-02: empty cache → null.
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })
        val cert = CrlTestSupport.buildTestCert(serial = 0x42L)

        assertNull(store.get(cert))
    }

    @Test
    fun listAllReturnsAllEntries() = runTest {
        // U01-SC-02: two distinct certs persist as two entries.
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })
        val certA = CrlTestSupport.buildTestCert(serial = 0x01L, issuer = "CN=Issuer A")
        val certB = CrlTestSupport.buildTestCert(serial = 0x02L, issuer = "CN=Issuer B")

        store.put(certA, "https://a.example/crl", byteArrayOf(0x0A), CrlMeta())
        store.put(certB, "https://b.example/crl", byteArrayOf(0x0B), CrlMeta())

        val all = store.listAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.crlUri == "https://a.example/crl" })
        assertTrue(all.any { it.crlUri == "https://b.example/crl" })
    }

    @Test
    fun computeCertKeyIsDeterministic() = runTest {
        // U01-SC-03: parse → encode → parse round-trip produces the same key.
        val cert = CrlTestSupport.buildTestCert(serial = 0xDEADBEEFL, issuer = "CN=Det")
        val reparsed = CrlTestSupport.reparse(cert)

        val k1 = computeCertKey(cert)
        val k2 = computeCertKey(reparsed)

        assertEquals(k1, k2)
        assertTrue(k1.contains("|"))
        assertTrue(k1.endsWith("|deadbeef"))
        assertTrue(k1.startsWith("CN=Det"))
    }

    @Test
    fun envelopeJsonIsRoundTrippableViaSecondStore() = runTest {
        // U01-SC-04: a freshly-constructed CrlStore over the same storage
        // sees the same persisted data — proves the JSON envelope is
        // self-describing and stable.
        val storage = CrlTestSupport.inMemoryStorage()
        val storeA = CrlStore(storage = storage, clock = { 1_000L })
        val cert = CrlTestSupport.buildTestCert(serial = 0x9L, issuer = "CN=RT")
        storeA.put(cert, "https://rt.example/crl", byteArrayOf(0xFF.toByte()), CrlMeta(thisUpdateEpochSeconds = 5L))

        val raw: String? = storage.getElement(CrlStore.STORAGE_KEY)
        assertNotNull(raw)
        assertTrue(raw.contains("\"certKey\""))
        assertTrue(raw.contains("\"crlUri\":\"https://rt.example/crl\""))

        val storeB = CrlStore(storage = storage, clock = { 2_000L })
        val cached = storeB.get(cert)
        assertNotNull(cached)
        assertEquals(5L, cached.thisUpdateEpochSeconds)
    }

    @Test
    fun corruptedEnvelopeIsTreatedAsEmptyCache() = runTest {
        // U01-SC-02 + warn-on-read pattern: corrupted JSON → null on get, empty on listAll.
        val storage = CrlTestSupport.inMemoryStorage()
        storage.saveElement(CrlStore.STORAGE_KEY, "this-is-not-json")
        val store = CrlStore(storage = storage, clock = { 0L })
        val cert = CrlTestSupport.buildTestCert(serial = 1L)

        assertNull(store.get(cert))
        assertEquals(emptyList(), store.listAll())
    }

    @Test
    fun isStaleHonoursNextUpdate() = runTest {
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })
        val cert = CrlTestSupport.buildTestCert()
        store.put(cert, "uri", byteArrayOf(0x01), CrlMeta(nextUpdateEpochSeconds = 100L))
        val cached = store.get(cert)!!

        assertFalse(store.isStale(cached, nowEpochSeconds = 100L))   // not yet past nextUpdate
        assertTrue(store.isStale(cached, nowEpochSeconds = 101L))    // strictly after
    }

    @Test
    fun isStaleIsFalseWhenNextUpdateMissing() = runTest {
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })
        val cert = CrlTestSupport.buildTestCert()
        store.put(cert, "uri", byteArrayOf(0x01), CrlMeta(nextUpdateEpochSeconds = null))
        val cached = store.get(cert)!!

        assertFalse(store.isStale(cached, nowEpochSeconds = Long.MAX_VALUE))
    }

    @Test
    fun concurrentReadsDoNotCorrupt() = runTest {
        // U01-SC-06: launch many concurrent listAll() calls; final size still 5.
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })
        repeat(5) { i ->
            val cert = CrlTestSupport.buildTestCert(serial = (10 + i).toLong(), issuer = "CN=I$i")
            store.put(cert, "https://x/$i", byteArrayOf(i.toByte()), CrlMeta())
        }

        coroutineScope {
            val jobs = List(20) {
                async { store.listAll().size }
            }
            val sizes = jobs.awaitAll()
            assertTrue(sizes.all { it == 5 })
        }

        assertEquals(5, store.listAll().size)
    }

    @Test
    fun clearEntriesIsIdempotent() = runTest {
        // U01-SC-07: clearEntries on empty storage does not throw.
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        store.clearEntries()
        store.clearEntries()  // second call — must remain a no-op
    }

    @Test
    fun clearEntriesRemovesAllEntries() = runTest {
        val store = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })
        val cert = CrlTestSupport.buildTestCert(serial = 99L)
        store.put(cert, "uri", byteArrayOf(0x01), CrlMeta())
        assertNotNull(store.get(cert))

        store.clearEntries()

        assertNull(store.get(cert))
        assertEquals(emptyList(), store.listAll())
    }

    @Test
    fun clearEntriesIsBestEffortOnStorageError() = runTest {
        // U01-SC-07: a throwing clearElement must NOT propagate.
        val throwingStorage: LocalStorage = object : LocalStorage {
            override suspend fun saveElement(key: String, value: String) = Unit
            override suspend fun getElement(key: String): String? = null
            override suspend fun clear() = Unit
            override suspend fun clearElement(logicalKey: String) {
                throw RuntimeException("disk fault")
            }
        }
        val store = CrlStore(storage = throwingStorage, clock = { 0L })

        store.clearEntries() // must not throw
    }

    @Test
    fun putPropagatesStorageErrors() = runTest {
        val throwingStorage: LocalStorage = object : LocalStorage {
            override suspend fun saveElement(key: String, value: String) {
                throw RuntimeException("disk full")
            }
            override suspend fun getElement(key: String): String? = null
            override suspend fun clear() = Unit
            override suspend fun clearElement(logicalKey: String) = Unit
        }
        val store = CrlStore(storage = throwingStorage, clock = { 0L })
        val cert = CrlTestSupport.buildTestCert()

        val ex = assertFailsWith<RuntimeException> {
            store.put(cert, "uri", byteArrayOf(0x01), CrlMeta())
        }
        assertEquals("disk full", ex.message)
    }

    @Test
    fun cancellationIsRespectedOnReadPath() = runTest {
        // U01-SC-08: a CancellationException raised by the underlying
        // storage propagates through CrlStore.get rather than being
        // swallowed by the Throwable catch.
        val cancellingStorage: LocalStorage = object : LocalStorage {
            override suspend fun saveElement(key: String, value: String) = Unit
            override suspend fun getElement(key: String): String? {
                throw CancellationException("simulated cancel")
            }
            override suspend fun clear() = Unit
            override suspend fun clearElement(logicalKey: String) = Unit
        }
        val store = CrlStore(storage = cancellingStorage, clock = { 0L })
        val cert = CrlTestSupport.buildTestCert()

        assertFailsWith<CancellationException> {
            store.get(cert)
        }
    }

    @Test
    fun timeoutCancellationIsRespected() = runTest {
        // U01-SC-08: withTimeout cancellation surfaces as
        // TimeoutCancellationException (a CancellationException subtype).
        val slowStorage: LocalStorage = object : LocalStorage {
            override suspend fun saveElement(key: String, value: String) = Unit
            override suspend fun getElement(key: String): String? {
                kotlinx.coroutines.delay(10_000L)
                return null
            }
            override suspend fun clear() = Unit
            override suspend fun clearElement(logicalKey: String) = Unit
        }
        val store = CrlStore(storage = slowStorage, clock = { 0L })
        val cert = CrlTestSupport.buildTestCert()

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1.milliseconds) {
                store.get(cert)
            }
        }
    }
}
