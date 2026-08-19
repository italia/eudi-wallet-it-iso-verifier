package it.infocert.proximityverification.crl.model

import org.multipaz.crypto.X509Cert

/**
 * Outcome of [it.infocert.proximityverification.crl.IssuerCertChainExtractor.extract].
 *
 * @property certificates every successfully-parsed entry from
 *   `keys[0].x5c[0..n]` in original order — the issuer's leaf, intermediates,
 *   and root. CRL prefetch covers the full chain.
 * @property parseFailureCount the number of `x5c` entries that could not be
 *   Base64-decoded or parsed as `X509Cert`. Surfaced into
 *   `CrlPrefetchSummary.parseFailureCount` (mapped onto the
 *   [CrlFailureReason.X5C_PARSE_ERROR] bucket).
 */
internal data class ChainExtractionResult(
    val certificates: List<X509Cert>,
    val parseFailureCount: Int
)
