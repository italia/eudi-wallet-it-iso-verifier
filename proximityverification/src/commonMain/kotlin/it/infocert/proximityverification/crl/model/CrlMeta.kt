package it.infocert.proximityverification.crl.model

/**
 * In-memory metadata extracted from a CRL payload at fetch time. Not
 * persisted on its own — its values flow into [CachedCrl] when the
 * envelope is written.
 *
 * Both fields are nullable: when the CRL is malformed or absent fields,
 * the extractor returns `CrlMeta()` rather than throwing.
 *
 * @property thisUpdateEpochSeconds CRL `thisUpdate` time as epoch seconds.
 * @property nextUpdateEpochSeconds CRL `nextUpdate` time as epoch seconds,
 *   when present (RFC 5280 marks this as optional, though most issuers
 *   include it).
 */
data class CrlMeta(
    val thisUpdateEpochSeconds: Long? = null,
    val nextUpdateEpochSeconds: Long? = null
)
