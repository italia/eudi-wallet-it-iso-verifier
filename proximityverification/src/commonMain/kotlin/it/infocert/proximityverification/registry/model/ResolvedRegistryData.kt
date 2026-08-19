package it.infocert.proximityverification.registry.model

import kotlinx.serialization.Serializable

/**
 * Complete result of a registry resolution chain.
 *
 * This is the unit of persistence in `LocalStorage`: a successful online
 * resolution serializes a [ResolvedRegistryData] under a single key, and the
 * offline branch deserializes the same blob.
 *
 * @property credentials filtered Credential Catalog entries (step 4).
 * @property issuerMetadata per-issuer resolved metadata (step 5),
 *   including extracted `status_list_aggregation_endpoint` (deferred to offline status verification).
 * @property claimTypes Claims Registry definitions indexed by claim name (step 6).
 *   Populated when the Claims Registry endpoint is available; empty otherwise.
 * @property schemas Schema Registry entries (step 4'). Populated when the
 *   Schema Registry endpoint is available; empty otherwise. Used as a
 *   technical correlator between catalog credential types and `mso_mdoc`
 *   docTypes published by issuers.
 * @property resolvedAtEpochMillis wall-clock timestamp of the resolution,
 *   useful for diagnostics and future cache-staleness policies.
 */
@Serializable
data class ResolvedRegistryData(
    val credentials: List<CatalogCredential>,
    val issuerMetadata: List<IssuerResolvedMetadata>,
    val claimTypes: Map<String, ClaimTypeDefinition> = emptyMap(),
    val schemas: List<SchemaDefinition> = emptyList(),
    val resolvedAtEpochMillis: Long
)
