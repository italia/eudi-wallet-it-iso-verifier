package it.infocert.proximityverification.status

import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import it.infocert.proximityverification.registry.model.ResolvedRegistryData
import it.infocert.proximityverification.registry.model.SchemaDefinition
import it.infocert.proximityverification.status.model.FailureReason
import it.infocert.proximityverification.status.model.StatusListAggregationResponse
import it.infocert.proximityverification.status.model.StatusListPayload
import kotlinx.coroutines.test.runTest
import org.multipaz.crypto.X509Cert
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.trustmanagement.TrustResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatusListPrefetcherTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    private fun config(docType: String): CredentialConfiguration =
        CredentialConfiguration(docType = docType, display = emptyList(), claims = emptyList())

    private fun registryDataWith(
        issuerToConfigs: Map<String, Pair<List<String>, String?>>,
        schemas: List<SchemaDefinition> = emptyList()
    ): ResolvedRegistryData {
        val issuerMetadata = issuerToConfigs.map { (entityId, value) ->
            val (docTypes, aggregationEndpoint) = value
            IssuerResolvedMetadata(
                entityId = entityId,
                statusListAggregationEndpoint = aggregationEndpoint,
                credentialConfigurations = docTypes.map { config(it) }
            )
        }
        return ResolvedRegistryData(
            credentials = emptyList(),
            issuerMetadata = issuerMetadata,
            schemas = schemas,
            resolvedAtEpochMillis = 0L
        )
    }

    private fun mdocSchema(credentialType: String, docType: String): SchemaDefinition =
        SchemaDefinition(
            id = "$credentialType+mso_mdoc",
            credentialType = credentialType,
            format = "mso_mdoc",
            docType = docType
        )

    private class FakeHttp(
        val aggregations: Map<String, FetchResult<StatusListAggregationResponse>>,
        val payloads: Map<String, FetchResult<StatusListPayload>>
    ) : StatusListHttp() {
        override suspend fun fetchAggregation(uri: String): FetchResult<StatusListAggregationResponse> =
            aggregations[uri] ?: FetchResult.Unreachable
        override suspend fun fetchStatusListPayload(uri: String): FetchResult<StatusListPayload> =
            payloads[uri] ?: FetchResult.Unreachable
    }

    /**
     * No-op trust manager: rejects every chain. Sufficient for tests that do
     * not exercise the parse path successfully (most prefetcher tests stop at
     * fetch outcomes). Tests that need an Ok parse outcome would have to wire a
     * real trust anchor + a real signed JWT and are out of scope here.
     */
    private class RejectAllTrust : TrustManagerInterface {
        override val identifier: String = "test-reject-all"
        override suspend fun getTrustPoints(): List<TrustPoint> = emptyList()
        override suspend fun verify(chain: List<X509Cert>, atTime: Instant): TrustResult =
            TrustResult(isTrusted = false)
    }

    @Test
    fun emptyDocTypesExpandsToAllResolvedDocTypes() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL", "EHIC") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://a/aggregation" to FetchResult.Success(StatusListAggregationResponse(emptyList()))
            ),
            payloads = emptyMap()
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = emptyList(), registryData = data, store = store)

        assertEquals(listOf("mDL", "EHIC").sorted(), summary.requestedDocTypes.sorted())
    }

    @Test
    fun unknownDocTypeThrowsPVE38() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = FakeHttp(emptyMap(), emptyMap()))

        val ex = assertFailsWith<ProximityVerificationException> {
            prefetcher.prefetch(docTypes = listOf("UNKNOWN"), registryData = data, store = store)
        }
        assertTrue(ex.code is ProximityVerificationExceptionCode.PVE38)
    }

    @Test
    fun noAggregationEndpointThrowsPVE37() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to null))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = FakeHttp(emptyMap(), emptyMap()))

        val ex = assertFailsWith<ProximityVerificationException> {
            prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)
        }
        assertTrue(ex.code is ProximityVerificationExceptionCode.PVE37)
    }

    @Test
    fun aggregationUnreachableConfluisceInFailedIssuers() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf("https://a/aggregation" to FetchResult.Unreachable),
            payloads = emptyMap()
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.AGGREGATION_UNREACHABLE, summary.failedIssuers["issuerA"])
        assertTrue(summary.fetchedLists.isEmpty())
    }

    @Test
    fun aggregationHttpErrorMapsToAggregationHttpError() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf("https://a/aggregation" to FetchResult.HttpError(500)),
            payloads = emptyMap()
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.AGGREGATION_HTTP_ERROR, summary.failedIssuers["issuerA"])
    }

    @Test
    fun aggregationMalformedBodyMapsToAggregationMalformed() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf("https://a/aggregation" to FetchResult.Malformed),
            payloads = emptyMap()
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.AGGREGATION_MALFORMED, summary.failedIssuers["issuerA"])
    }

    @Test
    fun perListFetchUnreachableMapsToFetchUnreachable() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://a/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf("https://a/sl/0"))
                )
            ),
            payloads = mapOf("https://a/sl/0" to FetchResult.Unreachable)
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.FETCH_UNREACHABLE, summary.failedLists["https://a/sl/0"])
    }

    @Test
    fun perListFetchHttpErrorMapsToFetchHttpError() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://a/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf("https://a/sl/0"))
                )
            ),
            payloads = mapOf("https://a/sl/0" to FetchResult.HttpError(404))
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.FETCH_HTTP_ERROR, summary.failedLists["https://a/sl/0"])
    }

    @Test
    fun nonAbsoluteUriInAggregationIsMarkedMalformed() = runTest {
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://a/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf("/relative/path"))
                )
            ),
            payloads = emptyMap()
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.AGGREGATION_MALFORMED, summary.failedLists["/relative/path"])
    }

    @Test
    fun payloadWithoutSigningChainIsMarkedUntrusted() = runTest {
        // Garbage bytes with no recognisable JWT/CWT envelope: no x5c / x5chain
        // header is extractable, so resolveSigningKey returns null and the
        // payload is rejected with UNTRUSTED_SIGNATURE before any signature
        // check is attempted by multipaz.
        val data = registryDataWith(
            mapOf("issuerA" to (listOf("mDL") to "https://a/aggregation"))
        )
        val payload = StatusListPayload(byteArrayOf(0x00, 0x01, 0x02, 0x03), contentType = null)
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://a/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf("https://a/sl/0"))
                )
            ),
            payloads = mapOf("https://a/sl/0" to FetchResult.Success(payload))
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(FailureReason.UNTRUSTED_SIGNATURE, summary.failedLists["https://a/sl/0"])
        assertTrue(summary.fetchedLists.isEmpty())
    }

    @Test
    fun ipzsAggregationFiltersByCredentialType_onlyRelevantUriIsAttempted() = runTest {
        val data = registryDataWith(
            issuerToConfigs = mapOf(
                "issuerIPZS" to (listOf("org.iso.18013.5.1.mDL") to "https://pre.issuer.wallet.ipzs.it/aggregation")
            ),
            schemas = listOf(
                mdocSchema(credentialType = "mDL", docType = "org.iso.18013.5.1.mDL")
            )
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val ipzsAggregationBody = StatusListAggregationResponse(
            statusLists = listOf(
                "https://pre.issuer.wallet.ipzs.it/statuslists/EuropeanDisabilityCard/0",
                "https://pre.issuer.wallet.ipzs.it/statuslists/EuropeanHealthInsuranceCard/0",
                "https://pre.issuer.wallet.ipzs.it/statuslists/education_degree/0",
                "https://pre.issuer.wallet.ipzs.it/statuslists/education_enrollment/0",
                "https://pre.issuer.wallet.ipzs.it/statuslists/mDL/0",
                "https://pre.issuer.wallet.ipzs.it/statuslists/pid/0",
                "https://pre.issuer.wallet.ipzs.it/statuslists/residency/0"
            )
        )
        val http = FakeHttp(
            aggregations = mapOf(
                "https://pre.issuer.wallet.ipzs.it/aggregation" to FetchResult.Success(ipzsAggregationBody)
            ),
            payloads = mapOf(
                "https://pre.issuer.wallet.ipzs.it/statuslists/mDL/0" to FetchResult.Unreachable
            )
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(
            docTypes = listOf("org.iso.18013.5.1.mDL"),
            registryData = data,
            store = store
        )

        assertEquals(1, summary.failedLists.size, "summary.failedLists=${summary.failedLists}")
        assertEquals(
            FailureReason.FETCH_UNREACHABLE,
            summary.failedLists["https://pre.issuer.wallet.ipzs.it/statuslists/mDL/0"]
        )
        assertTrue(summary.fetchedLists.isEmpty())
    }

    @Test
    fun emptySchemasMeansFilterNoOp_allUrisAttempted() = runTest {
        val data = registryDataWith(
            issuerToConfigs = mapOf(
                "issuerA" to (listOf("mDL") to "https://a/aggregation")
            ),
            schemas = emptyList()
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://a/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf(
                        "https://a/statuslists/mDL/0",
                        "https://a/statuslists/pid/0",
                        "https://a/statuslists/residency/0"
                    ))
                )
            ),
            payloads = mapOf(
                "https://a/statuslists/mDL/0" to FetchResult.Unreachable,
                "https://a/statuslists/pid/0" to FetchResult.Unreachable,
                "https://a/statuslists/residency/0" to FetchResult.Unreachable
            )
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(docTypes = listOf("mDL"), registryData = data, store = store)

        assertEquals(3, summary.failedLists.size)
    }

    @Test
    fun mixOfPatternAndNonPatternUris_keepsNonPatternAndAllowsRelevantPattern() = runTest {
        val data = registryDataWith(
            issuerToConfigs = mapOf(
                "issuerB" to (listOf("org.iso.18013.5.1.mDL") to "https://b/aggregation")
            ),
            schemas = listOf(
                mdocSchema(credentialType = "mDL", docType = "org.iso.18013.5.1.mDL")
            )
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://b/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf(
                        "https://b/statuslists/mDL/0",
                        "https://b/statuslists/pid/0",
                        "https://b/api/v1/sl?id=42",
                        "https://b/legacy-sl/foo/0"
                    ))
                )
            ),
            payloads = mapOf(
                "https://b/statuslists/mDL/0" to FetchResult.Unreachable,
                "https://b/api/v1/sl?id=42" to FetchResult.Unreachable,
                "https://b/legacy-sl/foo/0" to FetchResult.Unreachable
            )
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(
            docTypes = listOf("org.iso.18013.5.1.mDL"),
            registryData = data,
            store = store
        )

        assertEquals(3, summary.failedLists.size, "summary.failedLists=${summary.failedLists}")
        assertEquals(FailureReason.FETCH_UNREACHABLE, summary.failedLists["https://b/statuslists/mDL/0"])
        assertEquals(FailureReason.FETCH_UNREACHABLE, summary.failedLists["https://b/api/v1/sl?id=42"])
        assertEquals(FailureReason.FETCH_UNREACHABLE, summary.failedLists["https://b/legacy-sl/foo/0"])
        assertTrue(summary.failedLists["https://b/statuslists/pid/0"] == null)
    }

    @Test
    fun issuerWithAllUrisFiltered_doesNotProduceAnyFailureOrFetched() = runTest {
        val data = registryDataWith(
            issuerToConfigs = mapOf(
                "issuerC" to (listOf("org.iso.18013.5.1.mDL") to "https://c/aggregation")
            ),
            schemas = listOf(
                mdocSchema(credentialType = "mDL", docType = "org.iso.18013.5.1.mDL")
            )
        )
        val store = StatusListStore(storage = StatusListTestSupport.inMemoryStorage(), clock = { 0L })
        val http = FakeHttp(
            aggregations = mapOf(
                "https://c/aggregation" to FetchResult.Success(
                    StatusListAggregationResponse(listOf(
                        "https://c/statuslists/EHIC/0",
                        "https://c/statuslists/pid/0"
                    ))
                )
            ),
            payloads = emptyMap()
        )
        val prefetcher = StatusListPrefetcher(trustManager = RejectAllTrust(), http = http)

        val summary = prefetcher.prefetch(
            docTypes = listOf("org.iso.18013.5.1.mDL"),
            registryData = data,
            store = store
        )

        assertTrue(summary.fetchedLists.isEmpty())
        assertTrue(summary.failedLists.isEmpty())
        assertTrue(summary.failedIssuers.isEmpty())
        assertTrue(summary.resolvedIssuers.contains("issuerC"))
    }
}
