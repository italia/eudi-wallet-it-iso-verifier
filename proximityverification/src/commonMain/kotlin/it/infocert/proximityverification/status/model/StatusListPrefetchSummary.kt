package it.infocert.proximityverification.status.model

import it.infocert.proximityverification.crl.model.CrlPrefetchSummary

/**
 * Outcome of [it.infocert.proximityverification.status.StatusListPrefetcher.prefetch].
 *
 * @property requestedDocTypes docTypes effectively requested (after empty-list
 *   expansion to "all docTypes resolved by the registry").
 * @property resolvedIssuers issuer entity IDs that were considered for prefetch
 *   (those whose published configurations include any of the requested docTypes
 *   AND which expose a non-null `statusListAggregationEndpoint`).
 * @property fetchedLists Status List URIs successfully downloaded and persisted.
 * @property failedLists URIs that failed at any stage (fetch, parse, freshness).
 * @property failedIssuers issuer entity IDs whose aggregation endpoint failed.
 * @property persistedAtEpochMillis wall-clock timestamp at the end of the run.
 * @property crlSummary outcome of the offline-mode CRL prefetch performed
 *   alongside the status list prefetch by `enableStatusListsForOfflineUse`.
 *   `null` when the call did not run a CRL prefetch (e.g. callers that
 *   invoke `StatusListPrefetcher.prefetch` directly, or legacy entry
 *   points that have not yet been wired up). The field is purely
 *   additive: existing Kotlin source positional / named constructions
 *   compile unchanged thanks to the default value, and the class is not
 *   `@Serializable` so there is no envelope-shape concern.
 */
data class StatusListPrefetchSummary(
    val requestedDocTypes: List<String>,
    val resolvedIssuers: List<String>,
    val fetchedLists: List<String>,
    val failedLists: Map<String, FailureReason>,
    val failedIssuers: Map<String, FailureReason>,
    val persistedAtEpochMillis: Long,
    val crlSummary: CrlPrefetchSummary? = null
)
