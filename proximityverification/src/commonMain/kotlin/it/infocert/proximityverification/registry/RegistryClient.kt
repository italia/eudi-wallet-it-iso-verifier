package it.infocert.proximityverification.registry

import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.CatalogCredential
import it.infocert.proximityverification.registry.model.ClaimTypeDefinition
import it.infocert.proximityverification.registry.model.RegistryEndpoints
import it.infocert.proximityverification.registry.model.SchemaDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Steps 3–4 and 6 of the federation registry resolution chain.
 *
 * - Step 3: discover the IT-Wallet registry endpoints from
 *   `{taUrl}/.well-known/it-wallet-registry`. Content negotiation prefers JSON
 *   and falls back to JWT (signed registry response).
 * - Step 4: fetch the lightweight Digital Credentials Catalog (IT-Wallet §7.6.2)
 *   and extract `credential_type` + `issuers[].id` for each entry, optionally
 *   filtered by [allowedIssuerIds]. No format filtering occurs here — the catalog
 *   does not carry format or `docType` information (§7.6.3).
 * - Step 6: fetch the Claims Registry and build a `claimName → type` lookup
 *   (IT-Wallet §7.3). Non-blocking: failure returns `null` without aborting
 *   the chain.
 */
internal class RegistryClient(
    private val http: RegistryHttp = RegistryHttp()
) {

    private val tag: String = "RegistryClient"

    /**
     * Performs registry discovery against the Trust Anchor base URL.
     */
    suspend fun discoverRegistry(trustAnchorUrl: String): RegistryEndpoints? {
        val url: String = RegistryHttp.walletRegistryDiscoveryUrl(trustAnchorUrl)
        Logger.debug(tag) { "Step 3: GET $url" }
        val payload: JsonObject = http.getJsonOrJwsPayload(url) ?: run {
            Logger.warn(tag) { "Step 3 failed: registry discovery unreachable" }
            return null
        }
        val endpoints: JsonObject? = payload["endpoints"] as? JsonObject
        fun field(nestedKey: String, vararg flatKeys: String): String? {
            endpoints?.get(nestedKey)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            for (key in flatKeys) {
                payload[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }
        val credentialCatalog: String? = field("credential_catalog", "credential_catalog_endpoint", "credential_catalog")
        if (credentialCatalog.isNullOrBlank()) {
            Logger.warn(tag) { "Step 3 failed: missing credential_catalog endpoint" }
            return null
        }
        val claimsRegistry: String? = field("claims_registry", "claims_registry_endpoint", "claims_registry")
        val schemaRegistry: String? = field("schema_registry", "schema_registry_endpoint", "schema_registry")
        Logger.debug(tag) { "Step 3 ok: credentialCatalog=$credentialCatalog" }
        return RegistryEndpoints(
            credentialCatalog = credentialCatalog,
            claimsRegistry = claimsRegistry?.takeIf { it.isNotBlank() },
            schemaRegistry = schemaRegistry?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Fetches the lightweight Digital Credentials Catalog (IT-Wallet §7.6.2)
     * and extracts `credential_type` + `issuers[].id` for each entry.
     *
     * The catalog does **not** carry format or `docType` information (§7.6.3):
     * those are resolved from the Credential Issuer's Entity Configuration in
     * step 5. Only [allowedIssuerIds] filtering is applied here; empty list
     * disables the issuer filter (every entry accepted).
     */
    suspend fun fetchCredentialCatalog(
        catalogUrl: String,
        allowedIssuerIds: List<String>
    ): List<CatalogCredential>? {
        Logger.debug(tag) { "Step 4: GET $catalogUrl" }
        val payload: JsonObject = http.getJsonOrJwsPayload(catalogUrl) ?: run {
            Logger.warn(tag) { "Step 4 failed: credential catalog unreachable" }
            return null
        }
        val credentials = payload["credentials"]?.jsonArray ?: run {
            Logger.warn(tag) { "Step 4 failed: missing 'credentials' array in catalog" }
            return null
        }
        val filterByIssuer: Boolean = allowedIssuerIds.isNotEmpty()
        val allowedSet: Set<String> = allowedIssuerIds.toSet()
        val results: MutableList<CatalogCredential> = mutableListOf()
        for (entry in credentials) {
            val credential: JsonObject = runCatching { entry.jsonObject }.getOrNull() ?: continue
            val credentialType: String = credential["credential_type"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: continue
            val issuerIds: List<String> = credential["issuers"]?.jsonArray
                ?.mapNotNull { issuer ->
                    runCatching {
                        issuer.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                            ?: issuer.jsonObject["entity_id"]?.jsonPrimitive?.contentOrNull
                            ?: issuer.jsonPrimitive.contentOrNull
                    }.getOrNull()
                }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val filteredIssuerIds: List<String> = if (filterByIssuer) {
                issuerIds.filter { it in allowedSet }
            } else issuerIds
            if (filterByIssuer && filteredIssuerIds.isEmpty()) continue
            val version: String? = credential["version"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val isProximitySupported: Boolean? = credential["restriction_policy"]?.jsonObject
                ?.get("presentation_flows")?.jsonObject
                ?.get("proximity")?.jsonPrimitive?.booleanOrNull
            results += CatalogCredential(
                credentialType = credentialType,
                issuerIds = filteredIssuerIds,
                version = version,
                isProximitySupported = isProximitySupported
            )
        }
        if (results.isEmpty()) {
            Logger.warn(tag) { "Step 4 produced 0 credentials after filtering (allowedIssuerIds=$allowedIssuerIds)" }
        } else {
            Logger.debug(tag) { "Step 4 ok: ${results.size} credential(s) selected" }
        }
        return results
    }

    /**
     * Fetches and parses the Schema Registry. The Schema Registry correlates
     * catalog `credential_type` (and optional `version`) with a technical
     * `format` and, for `mso_mdoc`, the expected `docType`. The SDK uses it as
     * a correlator only; schema content/integrity validation is out of scope.
     *
     * Failure (unreachable, non-JSON, missing `schemas` array) collapses to
     * `null` and the caller falls back to existing issuer-level `mso_mdoc`
     * filtering. Cancellation is always re-thrown by [RegistryHttp].
     */
    suspend fun fetchSchemaRegistry(schemaUrl: String): List<SchemaDefinition>? {
        Logger.debug(tag) { "Schema registry: GET $schemaUrl" }
        val payload: JsonObject = http.getJsonOrJwsPayload(schemaUrl) ?: run {
            Logger.warn(tag) { "Schema registry failed: unreachable" }
            return null
        }
        val schemas: JsonArray = payload["schemas"] as? JsonArray ?: run {
            Logger.warn(tag) { "Schema registry failed: missing 'schemas' array" }
            return null
        }
        val result: List<SchemaDefinition> = schemas.mapNotNull { element ->
            val entry: JsonObject = element as? JsonObject ?: return@mapNotNull null
            val credentialType: String = entry["credential_type"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val format: String = entry["format"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SchemaDefinition(
                id = entry["id"]?.jsonPrimitive?.contentOrNull ?: "$credentialType+$format",
                version = entry["version"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                credentialType = credentialType,
                format = format,
                docType = entry["docType"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                schemaUri = entry["schema_uri"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                schemaIntegrity = entry["schema_uri#integrity"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                description = entry["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            )
        }
        if (result.isEmpty()) {
            Logger.warn(tag) { "Schema registry reachable but returned 0 schemas — falling back to issuer-level filtering" }
        } else {
            Logger.debug(tag) { "Schema registry ok: ${result.size} schema(s) parsed" }
        }
        return result
    }

    /**
     * Step 6: fetches and parses the Claims Registry (IT-Wallet §7.3).
     *
     * Returns a map of `claimName → ClaimTypeDefinition` or `null` if the
     * endpoint is unreachable or the response cannot be parsed. Failure is
     * **total-failure-safe** and non-blocking: any unexpected JSON shape
     * (missing fields, wrong types, malformed entries) is logged at WARN and
     * collapsed to `null`, so the caller can fall back to `KNOWN_ELEMENT_TYPES`
     * heuristic without aborting steps 1–5.
     *
     * Cancellation is always re-thrown.
     */
    suspend fun fetchClaimsRegistry(claimsUrl: String): Map<String, ClaimTypeDefinition>? {
        Logger.debug(tag) { "Step 6: GET $claimsUrl" }
        val payload: JsonObject = http.getJsonOrJwsPayload(claimsUrl) ?: run {
            Logger.warn(tag) { "Step 6 failed: claims registry unreachable" }
            return null
        }
        val claimsObj: JsonObject = payload["claims"] as? JsonObject ?: run {
            Logger.warn(tag) { "Step 6 failed: missing or non-object 'claims' in registry response" }
            return null
        }
        val result: MutableMap<String, ClaimTypeDefinition> = mutableMapOf()
        for ((claimName, element) in claimsObj) {
            val entry: JsonObject = element as? JsonObject ?: continue
            val type: String = (entry["type"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: continue
            val format: String? = (entry["format"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val aliases: List<String> = (entry["aliases"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val description: String? = (entry["description"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            result[claimName] = ClaimTypeDefinition(
                claimName = claimName,
                type = type,
                format = format,
                aliases = aliases,
                description = description
            )
        }
        Logger.debug(tag) { "Step 6 ok: ${result.size} claim type(s) parsed" }
        return result
    }
}
