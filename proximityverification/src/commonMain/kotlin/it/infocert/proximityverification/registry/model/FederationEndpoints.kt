package it.infocert.proximityverification.registry.model

import kotlinx.serialization.Serializable

/**
 * Federation endpoints extracted from the Trust Anchor Entity Configuration
 * (`metadata.federation_entity`).
 *
 * Reference: OpenID Federation 1.0 §7.
 *
 * @property trustAnchorUrl base entity URL of the Trust Anchor.
 * @property resolveEndpoint URL of the federation resolve endpoint.
 *   Used to fetch the resolved metadata of any subordinate entity (e.g. an issuer).
 * @property fetchEndpoint URL of the federation fetch endpoint, when published.
 */
@Serializable
data class FederationEndpoints(
    val trustAnchorUrl: String,
    val resolveEndpoint: String,
    val fetchEndpoint: String? = null
)
