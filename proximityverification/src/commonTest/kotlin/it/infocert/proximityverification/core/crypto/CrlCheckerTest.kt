package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.crl.CrlFetchResult
import it.infocert.proximityverification.crl.CrlMetaExtractor
import it.infocert.proximityverification.crl.CrlStore
import it.infocert.proximityverification.crl.CrlTestSupport
import it.infocert.proximityverification.crl.FakeCrlHttpFetcher
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.test.runTest
import org.multipaz.crypto.X509Cert
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrlCheckerTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    private fun newStore(clock: () -> Long = { 0L }): CrlStore =
        CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = clock)

    private fun checker(
        store: CrlStore? = null,
        isOfflineEnabled: Boolean = false,
        isOnline: Boolean = true,
        scripted: Map<String, CrlFetchResult> = emptyMap(),
        urisFor: Map<X509Cert, List<String>> = emptyMap(),
        clock: () -> Long = { 0L }
    ): Pair<CrlChecker, FakeCrlHttpFetcher> {
        val fake = FakeCrlHttpFetcher(scripted)
        val checker = CrlChecker(
            store = store,
            isOfflineEnabled = { isOfflineEnabled },
            isOnline = { isOnline },
            httpFetcher = fake,
            crlUriLookup = { cert -> urisFor[cert] ?: emptyList() },
            clock = clock
        )
        return Pair(checker, fake)
    }

    // ── U03-SC-01 ──────────────────────────────────────────────────────────

    @Test
    fun onlineNoCacheFetchSuccessReturnsVerified() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 1L, issuer = "CN=A")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=A")
        val uri = "https://crl.example/a.crl"
        val store = newStore()
        val (c, fake) = checker(
            store = store,
            isOnline = true,
            scripted = mapOf(uri to CrlFetchResult.Success(crlBytes)),
            urisFor = mapOf(cert to listOf(uri))
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.VERIFIED, outcome.status)
        // Side-effect: store now holds the freshly fetched CRL.
        assertEquals(1, store.listAll().size)
        assertEquals(listOf(uri), fake.calls)
    }

    // ── U03-SC-02 ──────────────────────────────────────────────────────────

    @Test
    fun onlineCacheStaleRefreshSuccessReturnsRefreshed_storeUpdated() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 2L, issuer = "CN=B")
        val staleCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=B",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(2_000L)
        )
        val freshCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=B",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(3_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(4_000L)
        )
        val uri = "https://crl.example/b.crl"
        // clock = 2_500 sec → millis 2_500_000. The cached entry is stale (nextUpdate=2000).
        val nowMillis = 2_500_000L
        val store = newStore(clock = { nowMillis })
        store.put(cert, uri, staleCrl, CrlMetaExtractor.extract(staleCrl))

        val (c, fake) = checker(
            store = store,
            isOnline = true,
            scripted = mapOf(uri to CrlFetchResult.Success(freshCrl)),
            urisFor = mapOf(cert to listOf(uri)),
            clock = { nowMillis }
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.REFRESHED, outcome.status)
        // Store updated: nextUpdate now reflects the fresh CRL.
        val updated = store.listAll().single()
        assertEquals(4_000L, updated.nextUpdateEpochSeconds)
        assertEquals(listOf(uri), fake.calls)
    }

    // ── U03-SC-03 ──────────────────────────────────────────────────────────

    @Test
    fun onlineCacheStaleRefreshFailReturnsStaleCache_storeUntouched() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 3L, issuer = "CN=C")
        val staleCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=C",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(2_000L)
        )
        val uri = "https://crl.example/c.crl"
        val nowMillis = 2_500_000L
        val store = newStore(clock = { nowMillis })
        store.put(cert, uri, staleCrl, CrlMetaExtractor.extract(staleCrl))
        val originalNextUpdate = store.listAll().single().nextUpdateEpochSeconds

        val (c, fake) = checker(
            store = store,
            isOnline = true,
            scripted = mapOf(uri to CrlFetchResult.Unreachable),
            urisFor = mapOf(cert to listOf(uri)),
            clock = { nowMillis }
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.STALE_CACHE, outcome.status)
        // Store untouched.
        assertEquals(originalNextUpdate, store.listAll().single().nextUpdateEpochSeconds)
        assertEquals(listOf(uri), fake.calls)
    }

    // ── U03-SC-04 ──────────────────────────────────────────────────────────

    @Test
    fun offlineEnabledCacheFreshReturnsVerified_noHttpCall() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 4L, issuer = "CN=D")
        val crl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=D",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(10_000L)
        )
        val nowMillis = 5_000_000L
        val store = newStore(clock = { nowMillis })
        store.put(cert, "uri", crl, CrlMetaExtractor.extract(crl))

        val (c, fake) = checker(
            store = store,
            isOnline = false,
            isOfflineEnabled = true,
            urisFor = mapOf(cert to listOf("uri")),
            clock = { nowMillis }
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.VERIFIED, outcome.status)
        assertTrue(fake.calls.isEmpty(), "no HTTP call expected when offline + fresh cache")
    }

    // ── U03-SC-05 ──────────────────────────────────────────────────────────

    @Test
    fun offlineEnabledCacheStaleReturnsStaleCache_noHttpCall() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 5L, issuer = "CN=E")
        val crl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=E",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(2_000L)
        )
        val nowMillis = 5_000_000L  // way past nextUpdate
        val store = newStore(clock = { nowMillis })
        store.put(cert, "uri", crl, CrlMetaExtractor.extract(crl))

        val (c, fake) = checker(
            store = store,
            isOnline = false,
            isOfflineEnabled = true,
            urisFor = mapOf(cert to listOf("uri")),
            clock = { nowMillis }
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.STALE_CACHE, outcome.status)
        assertTrue(fake.calls.isEmpty(), "no HTTP call expected in offline mode")
    }

    // ── U03-SC-06 ──────────────────────────────────────────────────────────

    @Test
    fun offlineEnabledCacheMissReturnsOfflineNoData_isRevokedFalse() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 6L, issuer = "CN=F")
        val store = newStore()
        val (c, fake) = checker(
            store = store,
            isOnline = false,
            isOfflineEnabled = true
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.OFFLINE_NO_DATA, outcome.status)
        assertTrue(fake.calls.isEmpty())
    }

    // ── U03-SC-07 ──────────────────────────────────────────────────────────

    @Test
    fun offlineDisabledCacheMissOnlineFetchFailReturnsFetchFailed() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 7L, issuer = "CN=G")
        val uri = "https://crl.example/g.crl"
        val store = newStore()
        val (c, _) = checker(
            store = store,
            isOnline = true,
            isOfflineEnabled = false,
            scripted = mapOf(uri to CrlFetchResult.Unreachable),
            urisFor = mapOf(cert to listOf(uri))
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.FETCH_FAILED, outcome.status)
    }

    // ── Decision matrix row 10 — online + no DP → CACHE_MISS. ──────────────

    @Test
    fun onlineNoCacheNoDistributionPointReturnsCacheMiss() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 10L, issuer = "CN=H")
        val store = newStore()
        val (c, fake) = checker(
            store = store,
            isOnline = true,
            urisFor = emptyMap()
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.CACHE_MISS, outcome.status)
        assertTrue(fake.calls.isEmpty())
    }

    // ── Offline + offline-disabled (legacy) — also FETCH_FAILED. ───────────

    @Test
    fun offlineOfflineDisabledReturnsFetchFailed() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 11L, issuer = "CN=I")
        val (c, fake) = checker(
            store = null,
            isOnline = false,
            isOfflineEnabled = false
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.FETCH_FAILED, outcome.status)
        assertTrue(fake.calls.isEmpty())
    }

    // ── U03-SC-11 — timeout-induced Unreachable produces STALE_CACHE. ──────

    @Test
    fun refreshHttpTimeoutPropagatesAsStaleCache() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 20L, issuer = "CN=T")
        val crl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=T",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(2_000L)
        )
        val uri = "https://crl.example/timeout.crl"
        val nowMillis = 9_999_999L
        val store = newStore(clock = { nowMillis })
        store.put(cert, uri, crl, CrlMetaExtractor.extract(crl))

        val (c, _) = checker(
            store = store,
            isOnline = true,
            // The DefaultCrlHttpFetcher converts an HttpRequestTimeoutException
            // into CrlFetchResult.Unreachable. We simulate that exact code path.
            scripted = mapOf(uri to CrlFetchResult.Unreachable),
            urisFor = mapOf(cert to listOf(uri)),
            clock = { nowMillis }
        )

        val outcome = c.checkCert(cert)

        assertFalse(outcome.isRevoked)
        assertEquals(CrlCheckStatus.STALE_CACHE, outcome.status)
    }

    // ── Per-cert revoked outcome (component for U03-SC-09). ────────────────

    @Test
    fun revokedCertCachedReturnsCertCrlOutcomeIsRevokedTrue() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 0xDEADL, issuer = "CN=R")
        val crl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=R",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(10_000L),
            revokedSerials = listOf(0xDEADL)
        )
        val nowMillis = 5_000_000L
        val store = newStore(clock = { nowMillis })
        store.put(cert, "uri", crl, CrlMetaExtractor.extract(crl))

        val (c, _) = checker(
            store = store,
            isOnline = true,
            urisFor = mapOf(cert to listOf("uri")),
            clock = { nowMillis }
        )

        val outcome = c.checkCert(cert)

        assertTrue(outcome.isRevoked)
        assertEquals(CrlCheckStatus.VERIFIED, outcome.status)
    }

    // ── U03-SC-08 — chain aggregation to worst severity. ───────────────────

    @Test
    fun chainOfThreeAggregatesToWorseStatus_offlineNoData() = runTest {
        // Three certs with distinct outcomes:
        //   cert1 → fresh-cached, VERIFIED.
        //   cert2 → stale-cached, STALE_CACHE.
        //   cert3 → no cache, OFFLINE_NO_DATA.
        val cert1 = CrlTestSupport.buildTestCert(serial = 100L, issuer = "CN=Chain1")
        val cert2 = CrlTestSupport.buildTestCert(serial = 200L, issuer = "CN=Chain2")
        val cert3 = CrlTestSupport.buildTestCert(serial = 300L, issuer = "CN=Chain3")

        val freshCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=Chain1",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(20_000L)
        )
        val staleCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=Chain2",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(2_000L)
        )

        val nowMillis = 5_000_000L
        val store = newStore(clock = { nowMillis })
        store.put(cert1, "uri1", freshCrl, CrlMetaExtractor.extract(freshCrl))
        store.put(cert2, "uri2", staleCrl, CrlMetaExtractor.extract(staleCrl))

        val crlChecker = CrlChecker(
            store = store,
            isOfflineEnabled = { true },
            isOnline = { false },
            httpFetcher = FakeCrlHttpFetcher(emptyMap()),
            crlUriLookup = { cert ->
                when (cert.serialNumber.value.toList()) {
                    cert1.serialNumber.value.toList() -> listOf("uri1")
                    cert2.serialNumber.value.toList() -> listOf("uri2")
                    cert3.serialNumber.value.toList() -> emptyList()
                    else -> emptyList()
                }
            },
            clock = { nowMillis }
        )

        val result = TrustUtils.verifyNoneOfCertificateIsRevoked(
            chain = listOf(cert1, cert2, cert3),
            crlChecker = crlChecker
        )

        assertFalse(result.isRevoked)
        assertEquals(CrlCheckStatus.OFFLINE_NO_DATA, result.crlCheckStatus)
    }

    // ── U03-SC-09 — revoked stops chain, status is worst-so-far. ───────────

    @Test
    fun chainStopsOnRevoked_returnsAggregatedStatusUpToThatCert() = runTest {
        val cert1 = CrlTestSupport.buildTestCert(serial = 1_000L, issuer = "CN=Stop1")
        val cert2 = CrlTestSupport.buildTestCert(serial = 0xBADL, issuer = "CN=Stop2")
        val cert3 = CrlTestSupport.buildTestCert(serial = 3_000L, issuer = "CN=Stop3")

        val staleCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=Stop1",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(2_000L)
        )
        val revokingCrl = CrlTestSupport.buildTestCrlBytes(
            issuer = "CN=Stop2",
            thisUpdate = kotlin.time.Instant.fromEpochSeconds(1_000L),
            nextUpdate = kotlin.time.Instant.fromEpochSeconds(20_000L),
            revokedSerials = listOf(0xBADL)
        )

        val nowMillis = 5_000_000L
        val store = newStore(clock = { nowMillis })
        store.put(cert1, "uri1", staleCrl, CrlMetaExtractor.extract(staleCrl))
        store.put(cert2, "uri2", revokingCrl, CrlMetaExtractor.extract(revokingCrl))

        val crlChecker = CrlChecker(
            store = store,
            isOfflineEnabled = { true },
            isOnline = { false },
            httpFetcher = FakeCrlHttpFetcher(emptyMap()),
            crlUriLookup = { cert ->
                when (cert.serialNumber.value.toList()) {
                    cert1.serialNumber.value.toList() -> listOf("uri1")
                    cert2.serialNumber.value.toList() -> listOf("uri2")
                    else -> emptyList()
                }
            },
            clock = { nowMillis }
        )

        val result = TrustUtils.verifyNoneOfCertificateIsRevoked(
            chain = listOf(cert1, cert2, cert3),
            crlChecker = crlChecker
        )

        assertTrue(result.isRevoked)
        // cert1 → STALE_CACHE; cert2 → VERIFIED + revoked. worse(STALE_CACHE, VERIFIED) == STALE_CACHE.
        // cert3 NOT visited (early return on revoked).
        assertEquals(CrlCheckStatus.STALE_CACHE, result.crlCheckStatus)
    }

    // ── U03-SC-10 — back-compat 3-arg constructor still compiles. ──────────

    @Test
    fun legacyCallSiteCompiles_threeFieldDestructure() {
        val r = RevocationResult(
            isRevoked = false,
            trustChain = null,
            error = null
        )
        // Legacy three-field reads must keep working.
        val isRevoked: Boolean = r.isRevoked
        val tc = r.trustChain
        val err: Throwable? = r.error
        // New defaulted field reads as VERIFIED.
        assertFalse(isRevoked)
        assertEquals(null, tc)
        assertEquals(null, err)
        assertEquals(CrlCheckStatus.VERIFIED, r.crlCheckStatus)
    }

    // ── worse(a, b) pairwise truth table — locks severity order. ───────────

    @Test
    fun worseIsCommutativeMaxBySeverity() {
        val all = CrlCheckStatus.values()
        for (a in all) {
            for (b in all) {
                val ab = worse(a, b)
                val ba = worse(b, a)
                // Commutative.
                assertEquals(ab, ba, "worse not commutative for ($a, $b)")
                // Result is the higher-ordinal of the two inputs.
                val expected = if (a.ordinal >= b.ordinal) a else b
                assertEquals(expected, ab, "worse($a, $b) != ${expected}")
            }
        }
        // Reflexive identity.
        for (s in all) {
            assertEquals(s, worse(s, s))
        }
        // Severity order anchor: pin specific extremes.
        assertEquals(CrlCheckStatus.OFFLINE_NO_DATA, worse(CrlCheckStatus.VERIFIED, CrlCheckStatus.OFFLINE_NO_DATA))
        assertEquals(CrlCheckStatus.FETCH_FAILED, worse(CrlCheckStatus.STALE_CACHE, CrlCheckStatus.FETCH_FAILED))
        assertEquals(CrlCheckStatus.CACHE_MISS, worse(CrlCheckStatus.REFRESHED, CrlCheckStatus.CACHE_MISS))
        // Severity ordinal exact values (catches accidental reorderings).
        assertEquals(0, CrlCheckStatus.VERIFIED.ordinal)
        assertEquals(1, CrlCheckStatus.REFRESHED.ordinal)
        assertEquals(2, CrlCheckStatus.STALE_CACHE.ordinal)
        assertEquals(3, CrlCheckStatus.CACHE_MISS.ordinal)
        assertEquals(4, CrlCheckStatus.FETCH_FAILED.ordinal)
        assertEquals(5, CrlCheckStatus.OFFLINE_NO_DATA.ordinal)
    }
}
