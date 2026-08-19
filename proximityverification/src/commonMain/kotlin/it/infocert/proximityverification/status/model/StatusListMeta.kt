package it.infocert.proximityverification.status.model

/**
 * Freshness metadata extracted from a Status List envelope at prefetch time.
 *
 * @property format wire format detected from `Content-Type`.
 * @property iat optional issued-at epoch seconds (JWT `iat` claim or CWT claim 6).
 * @property ttlSeconds optional TTL in seconds (JWT `ttl` claim or CWT claim 65534).
 */
data class StatusListMeta(
    val format: CachedFormat,
    val iat: Long? = null,
    val ttlSeconds: Long? = null
)
