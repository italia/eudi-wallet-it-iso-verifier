package it.infocert.proximityverification.crl

import it.infocert.proximityverification.crl.model.CrlFailureReason
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import it.infocert.proximityverification.registry.model.ResolvedRegistryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.crypto.X509Cert
import org.multipaz.util.toBase64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrlPrefetcherTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    private fun emptyRegistry(): ResolvedRegistryData =
        ResolvedRegistryData(
            credentials = emptyList(),
            issuerMetadata = emptyList(),
            resolvedAtEpochMillis = 0L
        )

    private fun registryWithChain(certs: List<X509Cert>): ResolvedRegistryData {
        // The extractor now returns every entry from x5c[0..n], so the
        // produced x5c array maps 1:1 with `certs` in original order.
        val x5c: JsonArray = JsonArray(
            certs.map { JsonPrimitive(it.encoded.toByteArray().toBase64()) }
        )
        val key0: JsonObject = JsonObject(mapOf("x5c" to x5c))
        val keys: JsonArray = JsonArray(listOf(key0))
        val jwks: JsonObject = JsonObject(mapOf("keys" to keys))
        val md = IssuerResolvedMetadata(
            entityId = "https://issuer.example",
            statusListAggregationEndpoint = null,
            credentialConfigurations = emptyList(),
            jwks = jwks
        )
        return ResolvedRegistryData(
            credentials = emptyList(),
            issuerMetadata = listOf(md),
            resolvedAtEpochMillis = 0L
        )
    }

    private fun newStore(): CrlStore =
        CrlStore(storage = CrlTestSupport.inMemoryStorage(), clock = { 0L })

    private fun newPrefetcher(
        store: CrlStore,
        scripted: Map<String, CrlFetchResult> = emptyMap(),
        urisFor: Map<String, List<String>> = emptyMap()
    ): Pair<CrlPrefetcher, FakeCrlHttpFetcher> {
        val fake = FakeCrlHttpFetcher(scripted)
        val prefetcher = CrlPrefetcher(
            store = store,
            httpFetcher = fake,
            crlUriLookup = { cert -> urisFor[computeCertKey(cert)] ?: emptyList() },
            clock = { 0L }
        )
        return Pair(prefetcher, fake)
    }

    @Test
    fun warnsAndSkipsCertWithoutCdp() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 1L, issuer = "CN=NoDP")
        val (prefetcher, _) = newPrefetcher(
            store = newStore(),
            urisFor = emptyMap() // no URIs known for any cert
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(1, summary.certsConsidered)
        assertTrue(summary.crlsFetched.isEmpty())
        // Per U01-SC-04: certs without a CDP do NOT appear in crlsFailed.
        assertTrue(summary.crlsFailed.isEmpty())
        assertEquals(0, summary.parseFailureCount)
    }

    @Test
    fun successPersistsToStoreWithMeta() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 2L, issuer = "CN=OK")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=OK")
        val store = newStore()
        val uri = "https://crl.example/ok.crl"
        val (prefetcher, fake) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.Success(crlBytes)),
            urisFor = mapOf(computeCertKey(cert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(1, summary.certsConsidered)
        assertEquals(listOf(uri), summary.crlsFetched)
        assertTrue(summary.crlsFailed.isEmpty())
        val cached = store.listAll()
        assertEquals(1, cached.size)
        val entry = cached.first()
        assertEquals(uri, entry.crlUri)
        // CRL meta from buildTestCrlBytes default: thisUpdate at fixed
        // epoch; nextUpdate at +86400.
        assertNotNull(entry.thisUpdateEpochSeconds)
        assertNotNull(entry.nextUpdateEpochSeconds)
        assertEquals(listOf(uri), fake.calls)
    }

    @Test
    fun http404ProducesCrlHttpError() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 3L, issuer = "CN=404")
        val uri = "https://crl.example/404.crl"
        val store = newStore()
        val (prefetcher, _) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.HttpError(404)),
            urisFor = mapOf(computeCertKey(cert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(emptyList(), summary.crlsFetched)
        assertEquals(
            CrlFailureReason.CRL_HTTP_ERROR,
            summary.crlsFailed[computeCertKey(cert)]
        )
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun unreachableProducesCrlUnreachable() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 4L, issuer = "CN=Down")
        val uri = "https://crl.example/down.crl"
        val store = newStore()
        val (prefetcher, _) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.Unreachable),
            urisFor = mapOf(computeCertKey(cert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(
            CrlFailureReason.CRL_UNREACHABLE,
            summary.crlsFailed[computeCertKey(cert)]
        )
    }

    @Test
    fun malformedBytesProduceCrlMalformedPayload() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 5L, issuer = "CN=Bad")
        val uri = "https://crl.example/bad.crl"
        val store = newStore()
        val (prefetcher, _) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.Success(byteArrayOf(0x00, 0x01, 0x02))),
            urisFor = mapOf(computeCertKey(cert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(
            CrlFailureReason.CRL_MALFORMED_PAYLOAD,
            summary.crlsFailed[computeCertKey(cert)]
        )
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun httpLayerMalformedProducesCrlMalformedPayload() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 6L, issuer = "CN=Empty")
        val uri = "https://crl.example/empty.crl"
        val store = newStore()
        val (prefetcher, _) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.Malformed),
            urisFor = mapOf(computeCertKey(cert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(
            CrlFailureReason.CRL_MALFORMED_PAYLOAD,
            summary.crlsFailed[computeCertKey(cert)]
        )
    }

    @Test
    fun multiUriFallbackPicksFirstSuccess() = runTest {
        val cert = CrlTestSupport.buildTestCert(serial = 7L, issuer = "CN=Multi")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=Multi")
        val store = newStore()
        val brokenUri = "https://broken/dp.crl"
        val okUri = "https://ok/dp.crl"
        val (prefetcher, fake) = newPrefetcher(
            store = store,
            scripted = mapOf(
                brokenUri to CrlFetchResult.HttpError(404),
                okUri to CrlFetchResult.Success(crlBytes)
            ),
            urisFor = mapOf(computeCertKey(cert) to listOf(brokenUri, okUri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(listOf(okUri), summary.crlsFetched)
        assertTrue(summary.crlsFailed.isEmpty())
        // Both URIs were tried in order — the second one provided the success.
        assertEquals(listOf(brokenUri, okUri), fake.calls)
    }

    @Test
    fun prefetchesLeafCrlWhenCdpPresent() = runTest {
        // Per U01-SC-16: with the leaf-inclusion change, a single-cert chain
        // (the leaf) with a CDP must be downloaded and persisted.
        val cert = CrlTestSupport.buildTestCert(serial = 8L, issuer = "CN=Leaf")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=Leaf")
        val store = newStore()
        val uri = "https://crl.example/leaf.crl"
        val (prefetcher, _) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.Success(crlBytes)),
            urisFor = mapOf(computeCertKey(cert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(cert))
        )

        assertEquals(1, summary.certsConsidered)
        assertEquals(listOf(uri), summary.crlsFetched)
        assertTrue(summary.crlsFailed.isEmpty())
        val cached = store.listAll()
        assertEquals(1, cached.size)
        assertEquals(computeCertKey(cert), cached.first().certKey)
    }

    @Test
    fun skipsCertWithoutCdpAndDoesNotFailSummary() = runTest {
        // Per U01-SC-17: a chain mixing a cert with a CDP and one without
        // produces a successful summary for the first and a silent skip
        // for the second — the no-CDP cert never appears in crlsFailed.
        val withCdp = CrlTestSupport.buildTestCert(serial = 9L, issuer = "CN=WithCdp")
        val noCdp = CrlTestSupport.buildTestCert(serial = 10L, issuer = "CN=NoCdp")
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=WithCdp")
        val store = newStore()
        val okUri = "https://crl.example/ok.crl"
        val (prefetcher, _) = newPrefetcher(
            store = store,
            scripted = mapOf(okUri to CrlFetchResult.Success(crlBytes)),
            // Only the first cert has a known URI; the second resolves to
            // an empty list (no CDP).
            urisFor = mapOf(computeCertKey(withCdp) to listOf(okUri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = registryWithChain(listOf(withCdp, noCdp))
        )

        assertEquals(2, summary.certsConsidered)
        assertEquals(listOf(okUri), summary.crlsFetched)
        assertTrue(summary.crlsFailed.isEmpty())
        assertFalse(summary.crlsFailed.containsKey(computeCertKey(noCdp)))
    }

    @Test
    fun dedupesAcrossTrustAnchorsAndX5c() = runTest {
        // Same identity (issuer + serial) appears once in trust anchors
        // (PEM) and once as the chain's leaf-and-only-cert. Both produce
        // the same computeCertKey → exactly one cert is considered.
        val anchorCert = CrlTestSupport.buildTestCert(serial = 99L, issuer = "CN=Shared")
        val pem = anchorCert.toPem()
        // Construct a separate chain cert with the same (issuer, serial).
        // A new ephemeral signing key produces different bytes but the
        // computeCertKey collides.
        val rootCertSameId = CrlTestSupport.buildTestCert(serial = 99L, issuer = "CN=Shared")
        assertEquals(computeCertKey(anchorCert), computeCertKey(rootCertSameId))

        val store = newStore()
        val uri = "https://crl.example/shared.crl"
        val crlBytes = CrlTestSupport.buildTestCrlBytes(issuer = "CN=Shared")
        val (prefetcher, fake) = newPrefetcher(
            store = store,
            scripted = mapOf(uri to CrlFetchResult.Success(crlBytes)),
            // Use the (issuer, serial)-derived key — it is the same for
            // both certs, so a single mapping covers both lookup calls.
            urisFor = mapOf(computeCertKey(anchorCert) to listOf(uri))
        )

        val summary = prefetcher.prefetch(
            trustAnchors = listOf(pem),
            registryData = registryWithChain(listOf(rootCertSameId))
        )

        assertEquals(1, summary.certsConsidered)
        assertEquals(listOf(uri), summary.crlsFetched)
        // Only one fetch performed despite the cert appearing in two
        // sources.
        assertEquals(1, fake.calls.size)
    }

    @Test
    fun emptyTrustAnchorListProducesNonNullSummaryWithZeroCertsConsidered() = runTest {
        val store = newStore()
        val (prefetcher, _) = newPrefetcher(store = store)

        val summary = prefetcher.prefetch(
            trustAnchors = emptyList(),
            registryData = emptyRegistry()
        )

        assertEquals(0, summary.certsConsidered)
        assertEquals(emptyList(), summary.crlsFetched)
        assertTrue(summary.crlsFailed.isEmpty())
        assertEquals(0, summary.parseFailureCount)
    }
}
