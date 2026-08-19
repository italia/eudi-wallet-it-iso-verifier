package it.infocert.proximityverification.crl.model

import kotlinx.serialization.Serializable

/**
 * Persisted entry in the CRL cache. The raw DER payload is preserved
 * (Base64 encoded for JSON-friendly storage) so the CRL can be re-parsed
 * and verified at use time.
 *
 * @property certKey deterministic key derived from `${issuerDN}|${serialHex}`
 *   of the certificate this CRL applies to. Used as the map key in the
 *   single-envelope persistence layout.
 * @property subjectDn RFC 4514 / RFC 2253 encoded subject DN of the cert.
 * @property issuerDn RFC 4514 / RFC 2253 encoded issuer DN of the cert.
 * @property serialHex lowercase hex-encoded serial number of the cert
 *   (sign-padding byte stripped).
 * @property crlUri CRL Distribution Point URI from which the payload was fetched.
 * @property payloadBase64 raw CRL DER bytes, Base64-encoded.
 * @property thisUpdateEpochSeconds optional `thisUpdate` timestamp (epoch seconds)
 *   extracted from the CRL.
 * @property nextUpdateEpochSeconds optional `nextUpdate` timestamp (epoch seconds)
 *   extracted from the CRL — drives the [staleness][isStale] check.
 * @property fetchedAtEpochMillis wall-clock timestamp at which the entry was stored.
 */
@Serializable
data class CachedCrl(
    val certKey: String,
    val subjectDn: String,
    val issuerDn: String,
    val serialHex: String,
    val crlUri: String,
    val payloadBase64: String,
    val thisUpdateEpochSeconds: Long? = null,
    val nextUpdateEpochSeconds: Long? = null,
    val fetchedAtEpochMillis: Long
)
