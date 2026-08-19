package it.infocert.proximityverification.core.crypto

/**
 * Classification of the path through which an MSO `StatusList` revocation
 * decision was reached. Used for diagnostic logging and as a hook for
 * `VerificationOutcome` enrichment (exposed via `CheckMetadata`).
 *
 * @property isStale `true` when the entry was used past its `iat + ttl` soft window
 *   (still parseable, kept as fallback). Drives the log severity (`warn` vs `info`).
 */
enum class ValidationMode(val isStale: Boolean) {
    ONLINE_LIVE(isStale = false),
    ONLINE_FALLBACK_CACHE_FRESH(isStale = false),
    ONLINE_FALLBACK_CACHE_STALE(isStale = true),
    OFFLINE_CACHE_FRESH(isStale = false),
    OFFLINE_CACHE_STALE(isStale = true),
    OFFLINE_DISABLED_NOT_DETERMINED(isStale = false)
}
