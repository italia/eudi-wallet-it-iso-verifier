package it.infocert.proximityverification.registry.model

import kotlinx.serialization.Serializable

/**
 * Single entry from the IT-Wallet Claims Registry (§7.3).
 *
 * Used by [it.infocert.proximityverification.registry.RegistryResolver] to enrich
 * `ClaimDescriptor.dataType` via [it.infocert.proximityverification.registry.RegistryResolver.mapRegistryType]
 * before the `KNOWN_ELEMENT_TYPES` heuristic in `CatalogBridge`.
 *
 * @property claimName canonical claim identifier (e.g. `"family_name"`).
 * @property type semantic data type as published by the registry
 *   (mapped to [it.infocert.proximityverification.core.catalog.ClaimDescriptor]
 *   `DATA_TYPE_*` constants).
 * @property format optional `format` modifier published alongside `type`. Real
 *   Trust Anchor payloads use `type=string` plus `format=date`/`format=data` to
 *   express dates and binary blobs without changing the top-level `type`.
 * @property aliases alternative names referring to the same claim, if published.
 *   Used by the resolver to bridge canonical/non-canonical claim identifiers.
 * @property description human-readable description, if published.
 */
@Serializable
data class ClaimTypeDefinition(
    val claimName: String,
    val type: String,
    val format: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null
)
