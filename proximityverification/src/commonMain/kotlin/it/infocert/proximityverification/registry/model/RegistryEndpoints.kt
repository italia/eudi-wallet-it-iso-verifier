package it.infocert.proximityverification.registry.model

import kotlinx.serialization.Serializable

/**
 * Endpoints exposed by the IT-Wallet registry discovery document
 * (`/.well-known/it-wallet-registry`).
 *
 * Reference: IT-Wallet Registry Infrastructure §7.2.
 *
 * @property credentialCatalog absolute URL of the Credential Catalog endpoint.
 * @property claimsRegistry absolute URL of the Claims Registry endpoint, when published.
 * @property schemaRegistry absolute URL of the Schema Registry endpoint, when published.
 */
@Serializable
data class RegistryEndpoints(
    val credentialCatalog: String,
    val claimsRegistry: String? = null,
    val schemaRegistry: String? = null
)
