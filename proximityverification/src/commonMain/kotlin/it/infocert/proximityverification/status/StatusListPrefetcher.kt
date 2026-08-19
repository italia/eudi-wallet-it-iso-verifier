package it.infocert.proximityverification.status

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.ResolvedRegistryData
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.FailureReason
import it.infocert.proximityverification.status.model.StatusListPayload
import it.infocert.proximityverification.status.model.StatusListPrefetchSummary
import kotlinx.coroutines.CancellationException
import org.multipaz.revocation.StatusList
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.trustmanagement.TrustManagerInterface
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Orchestrates the offline Status List prefetch.
 *
 * Flow per design `2026-04-28_offline-status-list-prefetch.md` §5.3, with the
 * concurrency model revised by `2026-05-04_offline-status-list-concurrency-fixes.md`:
 *
 * 1. Validate `docTypes`. Empty list ⇒ all docTypes resolved by the registry.
 * 2. Resolve issuer set: each issuer that publishes any of the requested docTypes
 *    AND has a non-null `statusListAggregationEndpoint`.
 * 3. If no issuer qualifies → throw [ProximityVerificationExceptionCode.PVE37].
 * 4. **Sequential** loop over issuers and over each issuer's Status List URIs.
 * 5. For each URI: fetch payload → multipaz parse with `publicKey = null` to
 *    catch `exp`/format errors → extract `iat`/`ttl` via [FreshnessExtractor]
 *    → `store.put`. Any throw from `store.put` is converted to
 *    `failedLists[uri] = STORAGE_ERROR`; the URI is **not** added to `fetched`.
 * 6. Errors confluiscono in the summary; the prefetch never aborts.
 *
 * `CancellationException` is always re-thrown.
 */
internal class StatusListPrefetcher(
    private val trustManager: TrustManagerInterface,
    private val http: StatusListHttp = StatusListHttp(),
    private val clock: () -> Long = { defaultEpochMillis() }
) {
    private val tag: String = "StatusListPrefetcher"

    suspend fun prefetch(
        docTypes: List<String>,
        registryData: ResolvedRegistryData,
        store: StatusListStore
    ): StatusListPrefetchSummary {
        val knownDocTypes: Set<String> = registryData.issuerMetadata
            .flatMap { it.credentialConfigurations }
            .map { it.docType }
            .toSet()

        val effectiveDocTypes: List<String> = if (docTypes.isEmpty()) {
            knownDocTypes.toList()
        } else {
            val unknown: List<String> = docTypes.filterNot { it in knownDocTypes }
            if (unknown.isNotEmpty()) {
                throw ProximityVerificationException(
                    "Unknown docType(s) for status list prefetch: $unknown",
                    code = ProximityVerificationExceptionCode.PVE38()
                )
            }
            docTypes
        }

        val effectiveSet: Set<String> = effectiveDocTypes.toSet()
        val expectedCredentialTypes: Set<String> = registryData.schemas
            .filter { it.format == "mso_mdoc" && it.docType != null && it.docType in effectiveSet }
            .map { it.credentialType }
            .toSet()
        val candidateIssuers: List<Pair<String, String?>> = registryData.issuerMetadata
            .filter { im -> im.credentialConfigurations.any { it.docType in effectiveSet } }
            .map { it.entityId to it.statusListAggregationEndpoint }
            .distinctBy { it.first }

        val resolvableIssuers: List<Pair<String, String>> = candidateIssuers
            .mapNotNull { (id, ep) -> if (ep != null) id to ep else null }

        if (resolvableIssuers.isEmpty()) {
            throw ProximityVerificationException(
                "No issuer in the resolved registry exposes a status_list_aggregation_endpoint " +
                    "for the requested docTypes",
                code = ProximityVerificationExceptionCode.PVE37()
            )
        }

        // Best-effort, per-issuer docType list scoped to the current request
        // (the aggregation response groups status lists by `credential_type`,
        // not `docType`, so we cannot derive a URI-granular mapping here).
        val issuerDocTypesByEntityId: Map<String, List<String>> = registryData.issuerMetadata
            .associate { im ->
                im.entityId to im.credentialConfigurations
                    .map { it.docType }
                    .filter { it in effectiveSet }
                    .distinct()
            }

        val fetched: MutableList<String> = mutableListOf()
        val failedLists: MutableMap<String, FailureReason> = mutableMapOf()
        val failedIssuers: MutableMap<String, FailureReason> = mutableMapOf()

        for ((issuerId, aggregationUri) in resolvableIssuers) {
            prefetchIssuer(
                issuerId = issuerId,
                aggregationUri = aggregationUri,
                expectedCredentialTypes = expectedCredentialTypes,
                issuerDocTypes = issuerDocTypesByEntityId[issuerId].orEmpty(),
                store = store,
                fetched = fetched,
                failedLists = failedLists,
                failedIssuers = failedIssuers
            )
        }

        return StatusListPrefetchSummary(
            requestedDocTypes = effectiveDocTypes,
            resolvedIssuers = resolvableIssuers.map { it.first },
            fetchedLists = fetched.toList(),
            failedLists = failedLists.toMap(),
            failedIssuers = failedIssuers.toMap(),
            persistedAtEpochMillis = clock()
        )
    }

    private suspend fun prefetchIssuer(
        issuerId: String,
        aggregationUri: String,
        expectedCredentialTypes: Set<String>,
        issuerDocTypes: List<String>,
        store: StatusListStore,
        fetched: MutableList<String>,
        failedLists: MutableMap<String, FailureReason>,
        failedIssuers: MutableMap<String, FailureReason>
    ) {
        val response = when (val r = http.fetchAggregation(aggregationUri)) {
            is FetchResult.Success -> r.value
            is FetchResult.Unreachable -> {
                Logger.warn(tag) { "Issuer $issuerId aggregation unreachable ($aggregationUri)" }
                failedIssuers[issuerId] = FailureReason.AGGREGATION_UNREACHABLE
                return
            }
            is FetchResult.HttpError -> {
                Logger.warn(tag) {
                    "Issuer $issuerId aggregation HTTP ${r.statusCode} ($aggregationUri)"
                }
                failedIssuers[issuerId] = FailureReason.AGGREGATION_HTTP_ERROR
                return
            }
            is FetchResult.Malformed -> {
                Logger.warn(tag) { "Issuer $issuerId aggregation malformed body ($aggregationUri)" }
                failedIssuers[issuerId] = FailureReason.AGGREGATION_MALFORMED
                return
            }
        }
        val outcome: FilterOutcome = if (expectedCredentialTypes.isEmpty()) {
            FilterOutcome(kept = response.statusLists, droppedAsIrrelevant = emptyList())
        } else {
            StatusListUriFilter.retain(response.statusLists, expectedCredentialTypes)
        }
        if (outcome.droppedAsIrrelevant.isNotEmpty()) {
            Logger.info(tag) {
                "Issuer $issuerId filter: kept=${outcome.kept.size}, " +
                    "droppedAsIrrelevant=${outcome.droppedAsIrrelevant.size}"
            }
        }
        for (uri in outcome.kept) {
            if (!isAbsoluteUri(uri)) {
                Logger.warn(tag) { "Issuer $issuerId published non-absolute uri: $uri" }
                failedLists[uri] = FailureReason.AGGREGATION_MALFORMED
                continue
            }
            val payload: StatusListPayload = when (val r = http.fetchStatusListPayload(uri)) {
                is FetchResult.Success -> r.value
                is FetchResult.Unreachable -> {
                    failedLists[uri] = FailureReason.FETCH_UNREACHABLE
                    continue
                }
                is FetchResult.HttpError -> {
                    Logger.warn(tag) { "Status list $uri responded HTTP ${r.statusCode}" }
                    failedLists[uri] = FailureReason.FETCH_HTTP_ERROR
                    continue
                }
                is FetchResult.Malformed -> {
                    failedLists[uri] = FailureReason.MALFORMED_PAYLOAD
                    continue
                }
            }
            val meta = FreshnessExtractor.extract(payload)
            val parseOutcome: ParseOutcome = parseAndValidate(payload = payload, format = meta.format)
            when (parseOutcome) {
                ParseOutcome.Ok -> {
                    try {
                        store.put(
                            uri = uri,
                            payload = payload,
                            meta = meta,
                            issuerEntityId = issuerId,
                            credentialTypes = expectedCredentialTypes.toList(),
                            docTypes = issuerDocTypes
                        )
                        fetched += uri
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Logger.warn(tag) { "store.put failed for $uri: ${e.message}" }
                        failedLists[uri] = FailureReason.STORAGE_ERROR
                    }
                }
                ParseOutcome.Expired -> failedLists[uri] = FailureReason.EXPIRED_AT_DOWNLOAD
                ParseOutcome.Unsupported -> failedLists[uri] = FailureReason.UNSUPPORTED_FORMAT
                ParseOutcome.Malformed -> failedLists[uri] = FailureReason.MALFORMED_PAYLOAD
                ParseOutcome.Untrusted -> failedLists[uri] = FailureReason.UNTRUSTED_SIGNATURE
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun parseAndValidate(
        payload: StatusListPayload,
        format: CachedFormat
    ): ParseOutcome {
        // Resolve the issuer signing key by anchoring the token's x5c / x5chain
        // to a known trust point. multipaz's StatusList.fromJwt/fromCwt require
        // a non-null publicKey, otherwise validateJwt's
        // require(publicKey != null || caValidated) fails.
        val now: Instant = Instant.fromEpochMilliseconds(clock())
        val signingKey = StatusListTrust.resolveSigningKey(
            payload = payload,
            format = format,
            trustManager = trustManager,
            atTime = now
        ) ?: return ParseOutcome.Untrusted

        return try {
            when (format) {
                CachedFormat.CWT -> StatusList.fromCwt(cwt = payload.bytes, publicKey = signingKey)
                CachedFormat.JWT -> StatusList.fromJwt(jwt = payload.bytes.decodeToString(), publicKey = signingKey)
            }
            ParseOutcome.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidRequestException) {
            ParseOutcome.Expired
        } catch (e: IllegalArgumentException) {
            ParseOutcome.Malformed
        } catch (e: Throwable) {
            ParseOutcome.Malformed
        }
    }

    private fun isAbsoluteUri(uri: String): Boolean {
        return uri.startsWith("http://") || uri.startsWith("https://")
    }

    private enum class ParseOutcome { Ok, Expired, Unsupported, Malformed, Untrusted }

    companion object {
        @OptIn(ExperimentalTime::class)
        private fun defaultEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
