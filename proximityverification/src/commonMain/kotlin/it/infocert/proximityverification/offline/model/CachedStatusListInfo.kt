package it.infocert.proximityverification.offline.model

import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.Usability
import kotlinx.serialization.Serializable

/**
 * Consumer-facing projection of a persisted Token Status List entry.
 *
 * Pure data — no payload bytes, no signing keys. Computed fields
 * ([expiresAtEpochSeconds], [usability]) are filled at snapshot construction
 * time so consumers do not need to import internal SDK types.
 *
 * @property uri exact URI matched against `RevocationStatus.StatusList.uri`
 *   at use time.
 * @property format wire format of the cached payload (CWT or JWT).
 * @property issuerEntityId federation entity id of the issuer that published
 *   the URI, when known. `null` for legacy entries persisted before this
 *   field was introduced or for entries created outside the prefetch path.
 * @property credentialTypes best-effort cross-issuer expected credential-type
 *   set known by the prefetcher when persisting the entry. Empty for legacy
 *   entries. See `CachedStatusList.credentialTypes` for the granularity
 *   caveat.
 * @property docTypes best-effort list of mdoc docTypes the issuer publishes
 *   within the requested scope at prefetch time. **Not URI-granular**: a URI
 *   may serve a credential type that maps to a subset of these docTypes.
 *   Empty for legacy entries.
 * @property iatEpochSeconds optional issued-at epoch seconds extracted from
 *   the envelope.
 * @property ttlSeconds optional TTL in seconds extracted from the envelope.
 * @property expiresAtEpochSeconds soft-cache expiry computed as
 *   `iatEpochSeconds + ttlSeconds`. `null` when either is missing.
 * @property fetchedAtEpochMillis wall-clock timestamp at which the entry was
 *   stored.
 * @property usability staleness classification at snapshot time. `Fresh`
 *   when the entry is within the spec-defined `iat + ttl` window (or when
 *   `iat` / `ttl` are not published). `StaleByTtl` past that window. The
 *   `Expired` variant is reserved for the use-time `multipaz.validateXxx`
 *   rejection path and is **not** populated by the snapshot.
 */
@Serializable
data class CachedStatusListInfo(
    val uri: String,
    val format: CachedFormat,
    val issuerEntityId: String? = null,
    val credentialTypes: List<String> = emptyList(),
    val docTypes: List<String> = emptyList(),
    val iatEpochSeconds: Long? = null,
    val ttlSeconds: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
    val fetchedAtEpochMillis: Long,
    val usability: Usability
)
