package it.infocert.proximityverification.offline

import it.infocert.proximityverification.ProximityVerification
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
import it.infocert.proximityverification.status.model.Usability
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for unit-04 (`offline-cache-info-and-crl/04-consumer-offline-cache-snapshot`):
 *
 * - U04-SC-01..03: `CachedStatusList` legacy decode + `StatusListStore.put`/`listAll`
 *   round-trip, covering the write-side context fields.
 * - U04-SC-04..10: `ProximityVerification.buildOfflineCacheSnapshot(...)` projects
 *   both stores, computes `expiresAtEpochSeconds` / `usability` / `isStale`, and
 *   degrades cleanly to an empty snapshot on uninitialized stores.
 *
 * Tests exercise the `internal` helper `buildOfflineCacheSnapshot` so the
 * `provideLocalStorage()` indirection is sidestepped — the public
 * `getOfflineCacheSnapshot()` method is a thin wrapper that only resolves the
 * wall-clock and lazy-inits the stores.
 */
class OfflineCacheSnapshotTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    // ── U04-SC-01 ────────────────────────────────────────────────────────

    @Test
    fun cachedStatusListDeserializesLegacyEnvelopeWithoutNewFields() = runTest {
        // U04-SC-01: a legacy envelope persisted before unit-04's context
        // fields existed must decode cleanly with the new fields at default.
        val legacyJson = """
            {
              "entries": {
                "https://example.com/sl/0": {
                  "uri": "https://example.com/sl/0",
                  "format": "CWT",
                  "payloadBase64": "AQID",
                  "iat": 100,
                  "ttlSeconds": 60,
                  "fetchedAtEpochMillis": 1700000000000
                }
              }
            }
        """.trimIndent()
        val storage = StatusListTestSupport.inMemoryStorage()
        storage.saveElement(StatusListStore.STORAGE_KEY, legacyJson)
        val store = StatusListStore(storage = storage, clock = { 0L })

        val all = store.listAll()

        assertEquals(1, all.size)
        val entry = all.first()
        assertEquals("https://example.com/sl/0", entry.uri)
        assertEquals(CachedFormat.CWT, entry.format)
        assertEquals(100L, entry.iat)
        assertEquals(60L, entry.ttlSeconds)
        assertNull(entry.issuerEntityId)
        assertEquals(emptyList(), entry.credentialTypes)
        assertEquals(emptyList(), entry.docTypes)
    }

    // ── U04-SC-02 ────────────────────────────────────────────────────────

    @Test
    fun statusListStorePutRoundTripsContextFields() = runTest {
        // U04-SC-02: put preserves the three new context fields end-to-end.
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 1_000L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = "application/statuslist+cwt")
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)

        store.put(
            uri = "https://example.com/sl/0",
            payload = payload,
            meta = meta,
            issuerEntityId = "https://issuer.example",
            credentialTypes = listOf("ct.example.dl"),
            docTypes = listOf("org.iso.18013.5.1.mDL")
        )

        val cached = store.get("https://example.com/sl/0")
        assertNotNull(cached)
        assertEquals("https://issuer.example", cached.issuerEntityId)
        assertEquals(listOf("ct.example.dl"), cached.credentialTypes)
        assertEquals(listOf("org.iso.18013.5.1.mDL"), cached.docTypes)
    }

    // ── U04-SC-03 ────────────────────────────────────────────────────────

    @Test
    fun statusListStoreListAllReturnsAllEntries() = runTest {
        val storage = StatusListTestSupport.inMemoryStorage()
        val store = StatusListStore(storage = storage, clock = { 1_000L })
        val payload = StatusListPayload(byteArrayOf(0x01), contentType = null)
        val meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)
        store.put("https://example.com/sl/0", payload, meta)
        store.put("https://example.com/sl/1", payload, meta)
        store.put("https://example.com/sl/2", payload, meta)

        val all = store.listAll()

        assertEquals(3, all.size)
        assertEquals(
            setOf(
                "https://example.com/sl/0",
                "https://example.com/sl/1",
                "https://example.com/sl/2"
            ),
            all.map { it.uri }.toSet()
        )
    }

    @Test
    fun statusListStoreListAllOnEmptyStorageReturnsEmptyList() = runTest {
        val store = StatusListStore(
            storage = StatusListTestSupport.inMemoryStorage(),
            clock = { 0L }
        )

        val all = store.listAll()

        assertTrue(all.isEmpty())
    }

    // ── U04-SC-04 + U04-SC-10 ───────────────────────────────────────────

    @Test
    fun snapshotOnEmptyStoresReturnsDisabledEmpty() = runTest {
        // U04-SC-04 + U04-SC-10: empty stores → offlineEnabled=false, both
        // lists empty, generatedAtEpochMillis > 0. This doubles as the
        // pre-init friendly behavior surrogate.
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 1_700_000_500_000L
        )

        assertFalse(snapshot.offlineEnabled)
        assertEquals(1_700_000_500_000L, snapshot.generatedAtEpochMillis)
        assertTrue(snapshot.statusLists.isEmpty())
        assertTrue(snapshot.crls.isEmpty())
    }

    // ── U04-SC-05 ────────────────────────────────────────────────────────

    @Test
    fun snapshotProjectsBothStoresWhenEnabled() = runTest {
        // U04-SC-05 (unit-test form): when both stores have entries and the
        // status list flag is enabled, the snapshot exposes them.
        val slsStorage = StatusListTestSupport.inMemoryStorage()
        val sls = StatusListStore(storage = slsStorage, clock = { 1_700_000_000_000L })
        sls.setEnabled(true)
        sls.put(
            uri = "https://example.com/sl/0",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 1_700_000_000L, ttlSeconds = 86_400L),
            issuerEntityId = "https://issuer.example",
            credentialTypes = listOf("ct.example.dl"),
            docTypes = listOf("org.iso.18013.5.1.mDL")
        )

        val crlsStorage = CrlTestSupport.inMemoryStorage()
        val crls = CrlStore(storage = crlsStorage, clock = { 1_700_000_000_000L })
        val cert = CrlTestSupport.buildTestCert(
            serial = 0xCAFEBABEL,
            issuer = "CN=Foo CA",
            subject = "CN=Foo Subject"
        )
        crls.put(
            cert = cert,
            crlUri = "https://example.com/crl/1",
            payloadBytes = byteArrayOf(0x10, 0x11),
            meta = CrlMeta(
                thisUpdateEpochSeconds = 1_700_000_000L,
                nextUpdateEpochSeconds = 1_700_086_400L
            )
        )

        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 1_700_000_500_000L
        )

        assertTrue(snapshot.offlineEnabled)
        assertEquals(1, snapshot.statusLists.size)
        val sli = snapshot.statusLists.first()
        assertEquals("https://example.com/sl/0", sli.uri)
        assertEquals(CachedFormat.CWT, sli.format)
        assertEquals("https://issuer.example", sli.issuerEntityId)
        assertEquals(listOf("ct.example.dl"), sli.credentialTypes)
        assertEquals(listOf("org.iso.18013.5.1.mDL"), sli.docTypes)
        assertEquals(1_700_000_000L, sli.iatEpochSeconds)
        assertEquals(86_400L, sli.ttlSeconds)
        assertEquals(1_700_086_400L, sli.expiresAtEpochSeconds)
        assertEquals(Usability.Fresh, sli.usability)

        assertEquals(1, snapshot.crls.size)
        val cli = snapshot.crls.first()
        assertEquals("https://example.com/crl/1", cli.crlUri)
        assertEquals("CN=Foo Subject", cli.subjectDn)
        assertEquals("CN=Foo CA", cli.issuerDn)
        assertEquals(1_700_000_000L, cli.thisUpdateEpochSeconds)
        assertEquals(1_700_086_400L, cli.nextUpdateEpochSeconds)
        // 1_700_000_500 sec < 1_700_086_400 sec → not stale
        assertFalse(cli.isStale)
    }

    // ── U04-SC-06 ────────────────────────────────────────────────────────

    @Test
    fun snapshotComputesExpiresAtFromIatPlusTtl() = runTest {
        val sls = StatusListStore(
            storage = StatusListTestSupport.inMemoryStorage(),
            clock = { 1_000L }
        )
        sls.put(
            uri = "uri",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 1_000L, ttlSeconds = 60L)
        )
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 1_500_000L
        )

        assertEquals(1_060L, snapshot.statusLists.single().expiresAtEpochSeconds)
    }

    @Test
    fun snapshotExpiresAtNullWhenIatOrTtlMissing() = runTest {
        val sls = StatusListStore(
            storage = StatusListTestSupport.inMemoryStorage(),
            clock = { 1_000L }
        )
        sls.put(
            uri = "uri-no-iat",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = 60L)
        )
        sls.put(
            uri = "uri-no-ttl",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 1_000L, ttlSeconds = null)
        )
        sls.put(
            uri = "uri-neither",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = null)
        )
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 1_500_000L
        )

        val byUri = snapshot.statusLists.associateBy { it.uri }
        assertNull(byUri["uri-no-iat"]?.expiresAtEpochSeconds)
        assertNull(byUri["uri-no-ttl"]?.expiresAtEpochSeconds)
        assertNull(byUri["uri-neither"]?.expiresAtEpochSeconds)
    }

    // ── U04-SC-07 ────────────────────────────────────────────────────────

    @Test
    fun snapshotIsStaleTrueWhenNextUpdatePast() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 1_700_000_000_000L })
        val cert = CrlTestSupport.buildTestCert(serial = 1L)
        crls.put(
            cert = cert,
            crlUri = "https://example.com/crl/past",
            payloadBytes = byteArrayOf(0x01),
            meta = CrlMeta(
                thisUpdateEpochSeconds = 1_000L,
                nextUpdateEpochSeconds = 2_000L
            )
        )

        // now = 3000s → 2000 < 3000 → stale
        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 3_000L * 1_000L
        )

        assertTrue(snapshot.crls.single().isStale)
    }

    @Test
    fun snapshotIsStaleFalseWhenNextUpdateFuture() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 1_700_000_000_000L })
        val cert = CrlTestSupport.buildTestCert(serial = 2L)
        crls.put(
            cert = cert,
            crlUri = "https://example.com/crl/future",
            payloadBytes = byteArrayOf(0x01),
            meta = CrlMeta(
                thisUpdateEpochSeconds = 1_000L,
                nextUpdateEpochSeconds = 10_000L
            )
        )

        // now = 5000s → 10000 > 5000 → not stale
        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 5_000L * 1_000L
        )

        assertFalse(snapshot.crls.single().isStale)
    }

    @Test
    fun snapshotIsStaleFalseWhenNextUpdateNull() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 1_700_000_000_000L })
        val cert = CrlTestSupport.buildTestCert(serial = 3L)
        crls.put(
            cert = cert,
            crlUri = "https://example.com/crl/no-next",
            payloadBytes = byteArrayOf(0x01),
            meta = CrlMeta(
                thisUpdateEpochSeconds = 1_000L,
                nextUpdateEpochSeconds = null
            )
        )

        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 99_999_999_999L
        )

        // U04-SC-07: nextUpdate=null → never stale by definition.
        assertFalse(snapshot.crls.single().isStale)
    }

    // ── U04-SC-08 ────────────────────────────────────────────────────────

    @Test
    fun snapshotUsabilityFreshWhenWithinTtl() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        sls.put(
            uri = "uri",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)
        )
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        // now = 130s → 130 ≤ 100+60 → Fresh
        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 130L * 1_000L
        )

        assertEquals(Usability.Fresh, snapshot.statusLists.single().usability)
    }

    @Test
    fun snapshotUsabilityStaleByTtlAfterTtl() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        sls.put(
            uri = "uri",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = 60L)
        )
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        // now = 200s → 200 > 100+60 → StaleByTtl
        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 200L * 1_000L
        )

        assertEquals(Usability.StaleByTtl, snapshot.statusLists.single().usability)
    }

    @Test
    fun snapshotUsabilityFreshWhenIatOrTtlNull() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        sls.put(
            uri = "uri-no-iat",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = null, ttlSeconds = 60L)
        )
        sls.put(
            uri = "uri-no-ttl",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 100L, ttlSeconds = null)
        )
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

        val snapshot = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 99_999_999_999L
        )

        snapshot.statusLists.forEach { entry ->
            assertEquals(Usability.Fresh, entry.usability, "expected Fresh for ${entry.uri}")
        }
    }

    // ── U04-SC-09 ────────────────────────────────────────────────────────

    @Test
    fun snapshotIsConsistentAcrossConsecutiveCalls() = runTest {
        val sls = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 1_000L })
        sls.setEnabled(true)
        sls.put(
            uri = "uri",
            payload = StatusListPayload(byteArrayOf(0x01), contentType = null),
            meta = StatusListMeta(format = CachedFormat.CWT, iat = 1_000L, ttlSeconds = 60L),
            issuerEntityId = "iss",
            credentialTypes = listOf("ct"),
            docTypes = listOf("dt")
        )
        val crls = CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 1_700_000_000_000L })
        val cert = CrlTestSupport.buildTestCert(serial = 0x42L)
        crls.put(
            cert = cert,
            crlUri = "https://example.com/crl/x",
            payloadBytes = byteArrayOf(0x01),
            meta = CrlMeta(
                thisUpdateEpochSeconds = 1_000L,
                nextUpdateEpochSeconds = 10_000L
            )
        )

        val first = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 5_000L * 1_000L
        )
        val second = ProximityVerification.buildOfflineCacheSnapshot(
            sls = sls,
            crls = crls,
            nowMillis = 5_000L * 1_000L + 1L
        )

        // Generated-at differs by 1ms; everything else is equal.
        assertEquals(first.offlineEnabled, second.offlineEnabled)
        assertEquals(first.statusLists, second.statusLists)
        assertEquals(first.crls, second.crls)
    }
}
