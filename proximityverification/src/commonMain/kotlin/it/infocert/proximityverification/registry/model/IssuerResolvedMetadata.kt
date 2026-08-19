package it.infocert.proximityverification.registry.model

import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Per-issuer metadata produced by `IssuerMetadataResolver` after a successful
 * `federation_resolve` call.
 *
 * @property entityId entity identifier of the issuer
 *   (matches `CatalogCredential.issuerIds[i]`).
 * @property statusListAggregationEndpoint value of
 *   `metadata.openid_credential_issuer.status_list_aggregation_endpoint` when present.
 *   Persisted for future offline status verification; not consumed in Phase 1.
 * @property credentialConfigurations parsed `mso_mdoc` credential configurations
 *   produced by [it.infocert.proximityverification.core.catalog.CredentialConfigurationParser].
 * @property jwks the raw `metadata.openid_credential_issuer.jwks` JSON object as
 *   captured during federation resolution. Carries the issuer's `keys[0].x5c`
 *   chain consumed by the offline-mode CRL prefetch pipeline (see
 *   `it.infocert.proximityverification.crl.IssuerCertChainExtractor`). `null` for
 *   issuers that did not publish a `jwks` block, and for legacy persisted
 *   blobs encoded before this field existed (default-`null` keeps the
 *   `@Serializable` envelope backward-compatible).
 */
@Serializable
data class IssuerResolvedMetadata(
    val entityId: String,
    val statusListAggregationEndpoint: String? = null,
    val credentialConfigurations: List<CredentialConfiguration>,
    val jwks: JsonObject? = null
)
