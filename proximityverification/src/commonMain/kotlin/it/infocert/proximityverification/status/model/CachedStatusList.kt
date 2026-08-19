package it.infocert.proximityverification.status.model

import kotlinx.serialization.Serializable

/**
 * Persisted entry in the Status List cache. Raw payload is preserved (Base64
 * encoded for JSON-friendly storage) and signature is validated at use time
 * using the MSO `status.certificate`.
 *
 * @property uri exact URI matched against `RevocationStatus.StatusList.uri`.
 * @property format wire format (CWT or JWT).
 * @property payloadBase64 raw payload bytes, Base64-encoded. CWT = raw COSE_Sign1
 *   bytes; JWT = the compact JWT string encoded as UTF-8 bytes then Base64.
 * @property iat optional issued-at epoch seconds extracted from the envelope.
 * @property ttlSeconds optional TTL in seconds extracted from the envelope.
 * @property fetchedAtEpochMillis wall-clock timestamp at which the entry was stored.
 * @property issuerEntityId optional federation entity id of the issuer that
 *   published the URI, resolved at prefetch time. `null` for entries that
 *   predate provenance tracking (legacy envelopes) or for entries persisted
 *   outside the prefetch path.
 * @property credentialTypes best-effort cross-issuer expected credential-type
 *   set known by the prefetcher when persisting this entry. Empty list for
 *   legacy envelopes. Note: granularity is currently the global expected set
 *   used for the aggregation filter, not strictly the credential types served
 *   by this specific URI — the aggregation response groups status lists by
 *   `credential_type` but we do not narrow it further per URI.
 * @property docTypes best-effort list of mdoc docTypes the issuer publishes
 *   within the requested scope at prefetch time. **Not URI-granular**: a URI
 *   may serve a credential type that maps to a subset of these docTypes. Empty
 *   list for legacy envelopes or when no docType filter information was
 *   available at prefetch time.
 */
@Serializable
data class CachedStatusList(
    val uri: String,
    val format: CachedFormat,
    val payloadBase64: String,
    val iat: Long? = null,
    val ttlSeconds: Long? = null,
    val fetchedAtEpochMillis: Long,
    val issuerEntityId: String? = null,
    val credentialTypes: List<String> = emptyList(),
    val docTypes: List<String> = emptyList()
)
