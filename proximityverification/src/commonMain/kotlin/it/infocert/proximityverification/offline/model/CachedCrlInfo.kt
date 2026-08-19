package it.infocert.proximityverification.offline.model

import kotlinx.serialization.Serializable

/**
 * Consumer-facing projection of a persisted X.509 CRL cache entry.
 *
 * Pure data — no DER payload, no signing keys. The [isStale] field is
 * computed at snapshot construction time mirroring `CrlStore.isStale`:
 * `true` iff the entry has a `nextUpdateEpochSeconds` and the snapshot
 * wall-clock is past it. Entries without a `nextUpdate` are never reported
 * as stale (caller decides separately whether to refresh).
 *
 * @property crlUri CRL Distribution Point URI from which the payload was
 *   fetched.
 * @property subjectDn RFC 4514 / RFC 2253 encoded subject DN of the cert this
 *   CRL applies to.
 * @property issuerDn RFC 4514 / RFC 2253 encoded issuer DN of the cert this
 *   CRL applies to.
 * @property serialHex lowercase hex-encoded serial number of the cert
 *   (sign-padding byte stripped).
 * @property thisUpdateEpochSeconds optional `thisUpdate` timestamp extracted
 *   from the CRL.
 * @property nextUpdateEpochSeconds optional `nextUpdate` timestamp extracted
 *   from the CRL — drives the [isStale] check.
 * @property fetchedAtEpochMillis wall-clock timestamp at which the entry was
 *   stored.
 * @property isStale `true` iff `nextUpdateEpochSeconds` is set AND the
 *   snapshot wall-clock is past it; `false` when missing or in the future.
 */
@Serializable
data class CachedCrlInfo(
    val crlUri: String,
    val subjectDn: String,
    val issuerDn: String,
    val serialHex: String,
    val thisUpdateEpochSeconds: Long? = null,
    val nextUpdateEpochSeconds: Long? = null,
    val fetchedAtEpochMillis: Long,
    val isStale: Boolean
)
