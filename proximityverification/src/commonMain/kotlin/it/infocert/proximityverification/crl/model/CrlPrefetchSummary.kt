package it.infocert.proximityverification.crl.model

/**
 * Outcome of [it.infocert.proximityverification.crl.CrlPrefetcher.prefetch].
 *
 * Surfaced (additively) on `StatusListPrefetchSummary.crlSummary` and
 * consumed by the offline-cache snapshot API in unit-04.
 *
 * @property certsConsidered number of unique certificates evaluated for CRL
 *   prefetch after deduplication by `(issuerDN, serialHex)` (see
 *   [it.infocert.proximityverification.crl.computeCertKey]).
 * @property crlsFetched URIs of CRLs that were successfully downloaded and
 *   persisted in [it.infocert.proximityverification.crl.CrlStore].
 * @property crlsFailed map keyed by the cert's `certKey` carrying the *last*
 *   [CrlFailureReason] observed for that cert (across multi-URI fallback).
 *   Certs without a CRL Distribution Point are logged at WARN by the
 *   prefetcher and **do not appear in this map** — the absence of a CDP is
 *   not treated as a failure.
 * @property parseFailureCount cumulative count of `x5c` entries that failed
 *   to Base64-decode or parse as `X509Cert` across all issuers (mapped onto
 *   [CrlFailureReason.X5C_PARSE_ERROR]). Surfaced here rather than in
 *   [crlsFailed] because parse-failed entries never produce a `certKey`.
 * @property persistedAtEpochMillis wall-clock timestamp at the end of the
 *   prefetch run.
 */
data class CrlPrefetchSummary(
    val certsConsidered: Int,
    val crlsFetched: List<String>,
    val crlsFailed: Map<String, CrlFailureReason>,
    val parseFailureCount: Int,
    val persistedAtEpochMillis: Long
)
