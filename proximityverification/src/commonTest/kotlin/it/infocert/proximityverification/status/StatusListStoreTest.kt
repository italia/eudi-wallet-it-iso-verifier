package it.infocert.proximityverification.status

import it.infocert.proximityverification.core.crypto.LocalStorage
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.StatusListMeta
import it.infocert.proximityverification.status.model.StatusListPayload
import it.infocert.proximityverification.status.model.Usability
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusListStoreTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    @Test
    fun putAndGetRoundTrip() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 1_000L })
        val payload = StatusListPayload(byteArrayOf(0x01, 0x02, 0x03), contentType = "application/statuslist+cwt")
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)

        store.put(uri = "https://example.com/sl/0", payload = payload, meta = meta)

        val cached = store.get("https://example.com/sl/0")
        assertNotNull(cached)
        assertEquals(CachedFormat.CWT, cached.format)
        assertEquals(100L, cached.iat)
        assertEquals(60L, cached.ttlSeconds)
        assertEquals(1_000L, cached.fetchedAtEpochMillis)
    }

    @Test
    fun getReturnsNullForUnknownUri() = runTest {
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })

        assertNull(store.get("https://example.com/missing"))
    }

    @Test
    fun usabilityFreshWhenWithinTtl() = runTest {
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = "application/statuslist+cwt")
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)
        store.put("uri", payload, meta)
        val cached = store.get("uri")!!

        val usability = store.usability(cached, nowEpochSeconds = 130L)

        assertEquals(Usability.Fresh, usability)
    }

    @Test
    fun usabilityStaleByTtl() = runTest {
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = "application/statuslist+cwt")
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)
        store.put("uri", payload, meta)
        val cached = store.get("uri")!!

        val usability = store.usability(cached, nowEpochSeconds = 200L)

        assertEquals(Usability.StaleByTtl, usability)
    }

    @Test
    fun usabilityFreshWhenNoIatOrTtl() = runTest {
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.JWT, iat = null, ttlSeconds = null)
        store.put("uri", payload, meta)
        val cached = store.get("uri")!!

        val usability = store.usability(cached, nowEpochSeconds = 999_999L)

        assertEquals(Usability.Fresh, usability)
    }

    @Test
    fun corruptedEnvelopeIsTreatedAsEmptyCache() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        storage.saveElement(StatusListStore.STORAGE_KEY, "not-a-json-object")
        val store = StatusListStore(storage = storage, clock = { 0L })

        assertNull(store.get("uri"))
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
        val store = StatusListStore(storage = throwingStorage, clock = { 0L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = null)

        val ex = assertFailsWith<RuntimeException> {
            store.put("https://example.com/sl/0", payload, meta)
        }
        assertEquals("disk full", ex.message)
    }

    @Test
    fun isEnabledDefaultsToFalseOnFreshStorage() = runTest {
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })

        assertFalse(store.isEnabled())
    }

    @Test
    fun setEnabledThenIsEnabledRoundTrip() = runTest {
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })

        store.setEnabled(true)
        assertTrue(store.isEnabled())

        store.setEnabled(false)
        assertFalse(store.isEnabled())
    }

    @Test
    fun isEnabledReturnsFalseForMalformedValue() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        storage.saveElement(StatusListStore.STORAGE_KEY_ENABLED, "yes-please")
        val store = StatusListStore(storage = storage, clock = { 0L })

        assertFalse(store.isEnabled())
    }

    @Test
    fun isEnabledReturnsFalseOnReadFailure() = runTest {
        val throwingStorage: LocalStorage = object : LocalStorage {
            override suspend fun saveElement(key: String, value: String) = Unit
            override suspend fun getElement(key: String): String? = throw RuntimeException("read fail")
            override suspend fun clear() = Unit
            override suspend fun clearElement(logicalKey: String) = Unit
        }
        val store = StatusListStore(storage = throwingStorage, clock = { 0L })

        assertFalse(store.isEnabled())
    }

    @Test
    fun clearEntriesRemovesEntriesButPreservesFlag() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 1_000L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = null)
        store.put("uri", payload, meta)
        store.setEnabled(true)
        assertNotNull(store.get("uri"))
        assertTrue(store.isEnabled())

        store.clearEntries()

        assertNull(store.get("uri"))
        assertTrue(store.isEnabled())
    }

    @Test
    fun clearEntriesIsBestEffortOnStorageError() = runTest {
        val throwingStorage: LocalStorage = object : LocalStorage {
            override suspend fun saveElement(key: String, value: String) = Unit
            override suspend fun getElement(key: String): String? = null
            override suspend fun clear() = Unit
            override suspend fun clearElement(logicalKey: String) {
                throw RuntimeException("disk fault")
            }
        }
        val store = StatusListStore(storage = throwingStorage, clock = { 0L })

        store.clearEntries()  // must not throw
    }
}
