package it.infocert.proximityverification

/**
 * Parameters for standalone (no-backend) SDK initialization.
 *
 * Use with [ProximityVerification.initializeStandalone] to configure the SDK
 * without contacting the RP backend. Intended for testing and development.
 *
 * @property trustAnchors PEM-encoded CA certificates for issuer cert chain validation.
 *   Pass `null` (default) to skip certificate validation entirely (trust-all).
 * @property issuerMetadata Optional OID4VCI issuer metadata to register in the
 *   [ProximityVerification.documentTypeCatalog]. Pass `null` (default) to use the
 *   built-in mDL ISO 18013-5 catalog.
 */
data class StandaloneRelyingPartyParams(
    val trustAnchors: List<String>? = null,
    val issuerMetadata: StandaloneIssuerMetadata? = null
)

/**
 * Source of OID4VCI issuer metadata for [StandaloneRelyingPartyParams].
 */
sealed class StandaloneIssuerMetadata {
    /**
     * Pass the JSON string directly (e.g. the content of
     * `.well-known/openid-credential-issuer`). Zero network calls.
     */
    data class FromJson(val json: String) : StandaloneIssuerMetadata()

    /**
     * SDK fetches `{url}/.well-known/openid-credential-issuer` at init time.
     * Requires network access to the issuer (not to the RP backend).
     */
    data class FromUrl(val url: String) : StandaloneIssuerMetadata()
}
