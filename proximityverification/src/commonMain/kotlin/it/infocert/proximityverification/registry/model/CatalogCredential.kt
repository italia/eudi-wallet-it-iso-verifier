package it.infocert.proximityverification.registry.model

import kotlinx.serialization.Serializable

/**
 * Single credential entry returned by the IT-Wallet Digital Credentials Catalog
 * (§7.6.2, Table 7.6) after optional `allowedIssuerIds` filtering.
 *
 * @property credentialType opaque credential type identifier (e.g. `"mDL"`, `"pid"`).
 * @property issuerIds list of issuer entity IDs eligible to issue this credential,
 *   after optional `allowedIssuerIds` filtering.
 * @property version optional credential version published by the catalog. When
 *   present, it correlates with Schema Registry entries.
 * @property isProximitySupported optional `restriction_policy.presentation_flows.proximity`
 *   flag. Treated as opt-out only: explicit `false` excludes the entry; missing or
 *   `true` keeps it eligible (the field is not normative in IT-Wallet today).
 */
@Serializable
data class CatalogCredential(
    val credentialType: String,
    val issuerIds: List<String>,
    val version: String? = null,
    val isProximitySupported: Boolean? = null
)
