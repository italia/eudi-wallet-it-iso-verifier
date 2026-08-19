package it.infocert.proximityverification.core.crypto

/**
 * Outcome of a single CRL revocation check, surfaced via
 * [RevocationResult.crlCheckStatus] after chain aggregation.
 *
 * Severity order (least → most severe) is encoded by enum declaration
 * order (== `ordinal`). Aggregation across a certificate chain takes
 * the worst per-cert outcome via [worse].
 *
 * Spec: `OFFLINE_NO_DATA > FETCH_FAILED > CACHE_MISS > STALE_CACHE > REFRESHED > VERIFIED`.
 *
 * DO NOT reorder without updating the `worseIsCommutativeMaxBySeverity`
 * test that pins this severity ladder.
 */
enum class CrlCheckStatus {
    /** CRL fetch live succeeded (online path, no cache involvement) or fresh-cached entry was used. */
    VERIFIED,

    /** Cache entry was stale; refresh inline replaced it with a fresh CRL. */
    REFRESHED,

    /** Cache entry used past its `nextUpdate` (refresh failed or device offline). */
    STALE_CACHE,

    /** Cert has CRL distribution points but no cache entry / no fetch was possible. */
    CACHE_MISS,

    /** Online but fetch failed and no cached entry available. Non-revoked, warn logged. */
    FETCH_FAILED,

    /** Offline + cache miss — nothing to verify against. Non-revoked, warn logged. */
    OFFLINE_NO_DATA
}

/**
 * Returns the worse of two [CrlCheckStatus] values per the documented severity order.
 * Used for chain aggregation: the chain's `crlCheckStatus` is the worst of any cert's outcome.
 */
internal fun worse(a: CrlCheckStatus, b: CrlCheckStatus): CrlCheckStatus =
    if (a.ordinal >= b.ordinal) a else b
