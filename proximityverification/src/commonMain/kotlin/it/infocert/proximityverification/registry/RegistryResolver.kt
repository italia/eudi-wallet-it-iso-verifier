package it.infocert.proximityverification.registry

import it.infocert.proximityverification.core.catalog.ClaimDescriptor
import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.core.catalog.DocumentTypeCatalog
import it.infocert.proximityverification.core.crypto.LocalStorage
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.CatalogCredential
import it.infocert.proximityverification.registry.model.ClaimTypeDefinition
import it.infocert.proximityverification.registry.model.FederationEndpoints
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import it.infocert.proximityverification.registry.model.RegistryEndpoints
import it.infocert.proximityverification.registry.model.ResolvedRegistryData
import it.infocert.proximityverification.registry.model.SchemaDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Orchestrator for the federation registry resolution chain.
 *
 * Online path executes steps 1–6 fail-fast sequentially: any failure in
 * steps 1–4 aborts the chain and degrades to the offline path (cached data →
 * built-in defaults). Steps 5 and 6 failures are per-issuer / non-blocking
 * respectively.
 *
 * The resolver never throws on registry failures: callers can call
 * [resolve] safely from the SDK initialization flow without breaking
 * existing behaviour. Cancellation is always re-thrown.
 *
 * **Step 4 (Digital Credentials Catalog)** is lightweight per IT-Wallet §7.6.3:
 * it provides only `credential_type` and `issuers[].id`. Format-specific
 * metadata (`docType`, claims, display) is resolved from the Credential
 * Issuer's Entity Configuration in step 5.
 *
 * The `status_list_aggregation_endpoint` extracted in step 5 is persisted but
 * not consumed (deferred to a separate offline status verification feature).
 * Step 6 (Claims Registry) enriches claim data types from the IT-Wallet
 * registry (§7.3); failure is non-blocking — `KNOWN_ELEMENT_TYPES` in
 * `CatalogBridge` covers the well-known ISO 18013-5 claims.
 *
 * **Single-tenant cache (constraint, not a bug):** the persisted
 * [ResolvedRegistryData] is stored under the global key [STORAGE_KEY] without
 * scoping by `federationEntityUrl` or `allowedIssuerIds`. The SDK is
 * single-tenant by construction (one Relying Party backend per install,
 * fixed at `initializeRelyingPartyInstance()` time); switching the
 * federation entity URL or issuer policy is a re-init event and triggers a
 * fresh online resolution that overwrites the cache. Consumers that need to
 * invalidate the cache explicitly (e.g. environment switch in a debug build)
 * can call `clearLibraryLocalStorage()`.
 */
internal class RegistryResolver(
    private val federationDiscovery: FederationDiscovery = FederationDiscovery(),
    private val registryClient: RegistryClient = RegistryClient(),
    private val issuerMetadataResolver: IssuerMetadataResolver = IssuerMetadataResolver(),
    private val networkProbe: () -> Boolean = { NativeUtils.isNetworkAvailable() },
    private val clock: () -> Long = { defaultEpochMillis() }
) {

    private val tag: String = "RegistryResolver"
    private val storageKey: String = STORAGE_KEY
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Runs the full resolution chain.
     *
     * Side effects: registers every produced [CredentialConfiguration] into
     * [catalog] (last-write-wins on `docType`), and persists the resolved
     * data into [storage] under [STORAGE_KEY] when the online path succeeds.
     *
     * @return the fresh [ResolvedRegistryData] on online success, the cached
     *   instance on offline fallback, or `null` if no data could be obtained.
     *   Callers must treat `null` as "keep built-in catalog only".
     */
    suspend fun resolve(
        federationEntityUrl: String,
        allowedIssuerIds: List<String>,
        catalog: DocumentTypeCatalog,
        storage: LocalStorage
    ): ResolvedRegistryData? {
        val previousCached: ResolvedRegistryData? = loadCached(storage)
        if (!networkProbe()) {
            Logger.info(tag) { "Device offline → trying cached registry data" }
            if (previousCached != null) {
                applyToCatalog(previousCached, catalog, source = "cache")
            } else {
                Logger.info(tag) { "Offline and no cached registry data — built-in catalog only" }
            }
            return previousCached
        }
        val online: ResolvedRegistryData? = try {
            resolveOnline(federationEntityUrl, allowedIssuerIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "Online registry resolution threw ${e::class.simpleName}: ${e.message}" }
            null
        }
        if (online != null) {
            persist(storage, online)
            pruneStaleRegistryDocTypes(previous = previousCached, fresh = online, catalog = catalog)
            applyToCatalog(online, catalog, source = "registry")
            return online
        }
        Logger.info(tag) { "Online registry resolution failed → trying cached registry data" }
        if (previousCached != null) {
            applyToCatalog(previousCached, catalog, source = "cache (online failed)")
            return previousCached
        }
        // Cache miss + online failed → try direct-federation if allowed issuers configured
        if (allowedIssuerIds.isNotEmpty()) {
            Logger.info(tag) {
                "Cache miss and online failed; attempting direct-federation fallback (${allowedIssuerIds.size} issuer(s))"
            }
            val fallback: ResolvedRegistryData? = try {
                resolveFromAllowedIssuers(federationEntityUrl, allowedIssuerIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(tag) { "Direct-federation fallback threw ${e::class.simpleName}: ${e.message}" }
                null
            }
            if (fallback != null) {
                persist(storage, fallback)
                applyToCatalog(fallback, catalog, source = "direct-federation fallback")
                return fallback
            }
        }
        Logger.warn(tag) { "All resolution paths failed — built-in catalog only" }
        return null
    }

    private suspend fun resolveOnline(
        federationEntityUrl: String,
        allowedIssuerIds: List<String>
    ): ResolvedRegistryData? {
        Logger.info(tag) { "Registry resolution started for $federationEntityUrl" }
        val federationEndpoints: FederationEndpoints =
            federationDiscovery.discover(federationEntityUrl) ?: return null
        val registryEndpoints: RegistryEndpoints =
            registryClient.discoverRegistry(federationEndpoints.trustAnchorUrl) ?: return null
        val catalogEntries: List<CatalogCredential> =
            registryClient.fetchCredentialCatalog(
                catalogUrl = registryEndpoints.credentialCatalog,
                allowedIssuerIds = allowedIssuerIds
            ) ?: return null
        if (catalogEntries.isEmpty()) {
            Logger.warn(tag) { "Catalog returned 0 entries — nothing to resolve" }
            return ResolvedRegistryData(
                credentials = emptyList(),
                issuerMetadata = emptyList(),
                resolvedAtEpochMillis = clock()
            )
        }
        val schemas: List<SchemaDefinition> =
            registryEndpoints.schemaRegistry?.let { url ->
                registryClient.fetchSchemaRegistry(url)
            } ?: emptyList()
        val eligibleCredentials: List<CatalogCredential> = filterProximityCredentials(catalogEntries)
        if (eligibleCredentials.isEmpty()) {
            Logger.info(tag) { "All catalog entries excluded by proximity policy" }
            return ResolvedRegistryData(
                credentials = eligibleCredentials,
                issuerMetadata = emptyList(),
                schemas = schemas,
                resolvedAtEpochMillis = clock()
            )
        }
        val expectedDocTypes: Set<String> = findExpectedMdocDocTypes(eligibleCredentials, schemas)
        Logger.debug(tag) {
            "Findings: catalog=${eligibleCredentials.size}, schemas=${schemas.size}, expectedDocTypes=${expectedDocTypes.size}"
        }
        val uniqueIssuerIds: List<String> = eligibleCredentials
            .flatMap { it.issuerIds }
            .distinct()
        val issuerMetadata: MutableList<IssuerResolvedMetadata> = mutableListOf()
        for (issuerId in uniqueIssuerIds) {
            val resolved: IssuerResolvedMetadata? = issuerMetadataResolver.resolveIssuer(
                resolveEndpoint = federationEndpoints.resolveEndpoint,
                issuerEntityId = issuerId,
                trustAnchorUrl = federationEndpoints.trustAnchorUrl
            )
            if (resolved != null) issuerMetadata += resolved
        }
        Logger.info(tag) {
            "Issuer resolve done: ${issuerMetadata.size}/${uniqueIssuerIds.size} issuer(s) resolved"
        }
        val correlatedMetadata: List<IssuerResolvedMetadata> = filterIssuerMetadataByExpectedDocTypes(
            issuerMetadata = issuerMetadata,
            expectedDocTypes = expectedDocTypes,
            hasSchemaRegistry = schemas.isNotEmpty()
        )
        val claimTypes: Map<String, ClaimTypeDefinition> =
            registryEndpoints.claimsRegistry?.let { url ->
                registryClient.fetchClaimsRegistry(url)
            } ?: emptyMap()
        if (registryEndpoints.claimsRegistry != null && claimTypes.isEmpty()) {
            Logger.warn(tag) { "Claims registry unavailable — proceeding without claim enrichment" }
        }
        val enrichedMetadata: List<IssuerResolvedMetadata> = if (claimTypes.isNotEmpty()) {
            correlatedMetadata.map { enrichWithClaimTypes(it, claimTypes) }
        } else correlatedMetadata.toList()
        return ResolvedRegistryData(
            credentials = eligibleCredentials,
            issuerMetadata = enrichedMetadata,
            claimTypes = claimTypes,
            schemas = schemas,
            resolvedAtEpochMillis = clock()
        )
    }

    /**
     * Direct-federation fallback: when the IT-Wallet registry chain (Steps 2–4)
     * is unreachable but [allowedIssuerIds] are known, resolves each issuer
     * directly via federation resolve (Step 5 only), skipping the catalog.
     *
     * Step 1 (federation discovery) is still required to obtain the resolve endpoint
     * and trust anchor URL. Returns `null` if Step 1 fails or zero issuers resolve.
     *
     * The produced [ResolvedRegistryData] has empty [ResolvedRegistryData.schemas]
     * and [ResolvedRegistryData.claimTypes] — claim enrichment is unavailable
     * without the Claims Registry. Synthetic [ResolvedRegistryData.credentials]
     * use `docType` as `credentialType` proxy.
     */
    /**
     * Entry point for callers that need only the direct-federation fallback path
     * (Step 1 + Step 5), without re-running the registry chain (Steps 2–4).
     * Persists and applies to [catalog] on success.
     */
    internal suspend fun resolveDirectFederationFallback(
        federationEntityUrl: String,
        allowedIssuerIds: List<String>,
        catalog: DocumentTypeCatalog,
        storage: LocalStorage
    ): ResolvedRegistryData? {
        val fallback = resolveFromAllowedIssuers(federationEntityUrl, allowedIssuerIds) ?: return null
        persist(storage, fallback)
        applyToCatalog(fallback, catalog, source = "direct-federation fallback")
        return fallback
    }

    private suspend fun resolveFromAllowedIssuers(
        federationEntityUrl: String,
        allowedIssuerIds: List<String>
    ): ResolvedRegistryData? {
        Logger.info(tag) {
            "Direct-federation fallback: resolving ${allowedIssuerIds.size} known issuer(s) via federation Step 1"
        }
        val federationEndpoints: FederationEndpoints =
            federationDiscovery.discover(federationEntityUrl) ?: run {
                Logger.warn(tag) { "Direct-federation fallback: federation discovery failed" }
                return null
            }
        val issuerMetadata: MutableList<IssuerResolvedMetadata> = mutableListOf()
        for (issuerEntityId in allowedIssuerIds) {
            val resolved: IssuerResolvedMetadata? = issuerMetadataResolver.resolveIssuer(
                resolveEndpoint = federationEndpoints.resolveEndpoint,
                issuerEntityId = issuerEntityId,
                trustAnchorUrl = federationEndpoints.trustAnchorUrl
            )
            if (resolved != null) {
                issuerMetadata += resolved
            } else {
                Logger.warn(tag) {
                    "Direct-federation fallback: could not resolve $issuerEntityId — skipped"
                }
            }
        }
        if (issuerMetadata.isEmpty()) {
            Logger.warn(tag) { "Direct-federation fallback: no issuer resolved — aborting" }
            return null
        }
        // Build synthetic CatalogCredential list (credentialType = docType as proxy)
        val syntheticCredentials: List<CatalogCredential> = issuerMetadata.flatMap { metadata ->
            metadata.credentialConfigurations.map { config ->
                CatalogCredential(
                    credentialType = config.docType,
                    issuerIds = listOf(metadata.entityId),
                    version = null,
                    isProximitySupported = null
                )
            }
        }
        Logger.info(tag) {
            "Direct-federation fallback: ${issuerMetadata.size}/${allowedIssuerIds.size} issuer(s) resolved, " +
                "${syntheticCredentials.size} synthetic credential(s)"
        }
        return ResolvedRegistryData(
            credentials = syntheticCredentials,
            issuerMetadata = issuerMetadata.toList(),
            claimTypes = emptyMap(),
            schemas = emptyList(),
            resolvedAtEpochMillis = clock()
        )
    }

    /**
     * Removes catalog entries whose Trust Anchor extension
     * `restriction_policy.presentation_flows.proximity` is explicitly `false`.
     * Missing or `true` is treated as eligible.
     */
    private fun filterProximityCredentials(credentials: List<CatalogCredential>): List<CatalogCredential> {
        val (excluded, eligible) = credentials.partition { it.isProximitySupported == false }
        if (excluded.isNotEmpty()) {
            Logger.info(tag) {
                "Excluded ${excluded.size} catalog entry(ies) by proximity policy: " +
                    excluded.joinToString { it.credentialType }
            }
        }
        return eligible
    }

    /**
     * Computes the expected `mso_mdoc` docTypes by correlating eligible catalog
     * entries with Schema Registry entries on `(credential_type, version)`.
     * Returns an empty set when the Schema Registry is unavailable; callers
     * interpret that as "no docType filter".
     */
    private fun findExpectedMdocDocTypes(
        credentials: List<CatalogCredential>,
        schemas: List<SchemaDefinition>
    ): Set<String> {
        if (schemas.isEmpty()) return emptySet()
        val allowedTypes: Set<String> = credentials.map { it.credentialType }.toSet()
        return schemas
            .filter { it.format == "mso_mdoc" && it.credentialType in allowedTypes }
            .mapNotNull { it.docType }
            .toSet()
    }

    /**
     * Restricts each issuer's `mso_mdoc` configurations to those whose `docType`
     * is in [expectedDocTypes]. When [hasSchemaRegistry] is `false`, the original
     * configurations are kept (legacy behaviour: trust issuer-level filtering).
     * Issuers left with zero configurations after filtering are dropped.
     */
    private fun filterIssuerMetadataByExpectedDocTypes(
        issuerMetadata: List<IssuerResolvedMetadata>,
        expectedDocTypes: Set<String>,
        hasSchemaRegistry: Boolean
    ): List<IssuerResolvedMetadata> {
        if (!hasSchemaRegistry) {
            Logger.info(tag) {
                "Schema registry unavailable — keeping all issuer mso_mdoc configurations (degraded)"
            }
            return issuerMetadata
        }
        return issuerMetadata.mapNotNull { metadata ->
            val kept: List<CredentialConfiguration> = metadata.credentialConfigurations
                .filter { it.docType in expectedDocTypes }
            if (kept.isEmpty()) {
                Logger.info(tag) {
                    "Issuer ${metadata.entityId} dropped — no configuration matched expected docTypes"
                }
                null
            } else metadata.copy(credentialConfigurations = kept)
        }
    }

    /**
     * Removes from [catalog] the docTypes that were registered by a previous
     * registry resolution but are no longer present in the [fresh] one, so the
     * catalog never accumulates orphaned registry entries across re-inits.
     *
     * Pruning is **opt-in safe**: a stale docType is unregistered only if the
     * current catalog entry equals what the previous resolution registered
     * (data-class equality). If the entry has been replaced — by built-in
     * registrations performed before `resolve()` runs, or by an explicit
     * consumer `registerFromJson(...)` after init — it is left untouched.
     */
    private fun pruneStaleRegistryDocTypes(
        previous: ResolvedRegistryData?,
        fresh: ResolvedRegistryData,
        catalog: DocumentTypeCatalog
    ) {
        if (previous == null) return
        val freshDocTypes: Set<String> = fresh.issuerMetadata
            .flatMap { it.credentialConfigurations }
            .map { it.docType }
            .toSet()
        val previousByDocType: Map<String, List<CredentialConfiguration>> = previous.issuerMetadata
            .flatMap { it.credentialConfigurations }
            .groupBy { it.docType }
        val staleDocTypes: List<String> = previousByDocType.keys.filter { it !in freshDocTypes }
        if (staleDocTypes.isEmpty()) return
        for (docType in staleDocTypes) {
            val previouslyRegistered: CredentialConfiguration =
                mergeConfigurations(previousByDocType.getValue(docType))
            val current: CredentialConfiguration? = catalog.getConfiguration(docType)
            if (current != null && current == previouslyRegistered) {
                if (catalog.unregister(docType)) {
                    Logger.info(tag) { "Pruned stale registry docType: $docType" }
                }
            } else {
                Logger.info(tag) {
                    "Skipping prune for $docType — current entry differs from previous registry value (kept)"
                }
            }
        }
    }

    private fun applyToCatalog(
        data: ResolvedRegistryData,
        catalog: DocumentTypeCatalog,
        source: String
    ) {
        val configurations: List<CredentialConfiguration> = data.issuerMetadata
            .flatMap { it.credentialConfigurations }
        if (configurations.isEmpty()) {
            Logger.info(tag) { "No credential configurations to register from $source" }
            return
        }
        val grouped: Map<String, List<CredentialConfiguration>> = configurations
            .groupBy { it.docType }
        for ((_, entries) in grouped) {
            val merged: CredentialConfiguration = mergeConfigurations(entries)
            try {
                catalog.registerFromJson(buildSingleConfigJson(merged))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(tag) {
                    "Failed to register docType=${merged.docType} from $source: ${e.message}"
                }
            }
        }
    }

    /**
     * Merges multiple `CredentialConfiguration` entries that share a `docType`
     * (e.g. when several issuers publish the same credential). Display arrays
     * are concatenated and deduplicated by locale; claims are merged on
     * `(ns, identifier)` keeping the first occurrence.
     */
    private fun mergeConfigurations(entries: List<CredentialConfiguration>): CredentialConfiguration {
        if (entries.size == 1) return entries.single()
        val first: CredentialConfiguration = entries.first()
        val displays = entries.flatMap { it.display }.distinctBy { it.locale }
        val claims = entries.flatMap { it.claims }.distinctBy { it.ns to it.identifier }
        return CredentialConfiguration(
            docType = first.docType,
            display = displays,
            claims = claims
        )
    }

    /**
     * Renders a [CredentialConfiguration] as the JSON shape consumed by
     * [DocumentTypeCatalog.registerFromJson] (single config form).
     */
    private fun buildSingleConfigJson(config: CredentialConfiguration): String {
        return buildJsonObject {
            put("doctype", config.docType)
            putJsonObject("credential_metadata") {
                putJsonArray("display") {
                    for (d in config.display) {
                        add(buildJsonObject {
                            put("name", d.name)
                            put("locale", d.locale)
                        })
                    }
                }
                putJsonArray("claims") {
                    for (c in config.claims) {
                        add(buildJsonObject {
                            putJsonArray("path") {
                                add(c.ns)
                                add(c.identifier)
                            }
                            put("mandatory", c.isMandatory)
                            put("value_type", c.dataType)
                            putJsonArray("display") {
                                for (d in c.display) {
                                    add(buildJsonObject {
                                        put("name", d.name)
                                        put("locale", d.locale)
                                    })
                                }
                            }
                        })
                    }
                }
            }
        }.toString()
    }

    /**
     * Enriches claim data types in [metadata] using the Claims Registry lookup.
     *
     * Only claims whose [ClaimDescriptor.dataType] currently equals
     * [ClaimDescriptor.DATA_TYPE_STRING] are candidates for override.
     *
     * **Assumption (and known limitation):** [it.infocert.proximityverification.core.catalog.CredentialConfigurationParser]
     * defaults missing `value_type` to `DATA_TYPE_STRING`, so this function cannot
     * distinguish "issuer omitted `value_type`" from "issuer explicitly published
     * `value_type: \"string\"`". Both cases are eligible for registry override.
     * Today this is benign because OID4VCI issuer metadata observed in production
     * does not publish `value_type` (design §3, row 5). If issuers ever start
     * publishing `value_type` explicitly, this priority must be revisited so that
     * issuer-provided types win over registry types.
     *
     * Lookup is canonical-only (by [ClaimDescriptor.identifier]); see
     * [it.infocert.proximityverification.registry.model.ClaimTypeDefinition.aliases].
     */
    private fun enrichWithClaimTypes(
        metadata: IssuerResolvedMetadata,
        claimTypes: Map<String, ClaimTypeDefinition>
    ): IssuerResolvedMetadata {
        val claimTypeLookup: Map<String, ClaimTypeDefinition> = buildClaimTypeLookup(claimTypes)
        val enrichedConfigs: List<CredentialConfiguration> = metadata.credentialConfigurations.map { config ->
            val enrichedClaims: List<ClaimDescriptor> = config.claims.map { claim ->
                val definition: ClaimTypeDefinition? = claimTypeLookup[claim.identifier]
                if (definition != null && claim.dataType == ClaimDescriptor.DATA_TYPE_STRING) {
                    val mapped: String = mapRegistryType(definition.type, definition.format)
                    if (mapped != ClaimDescriptor.DATA_TYPE_STRING) claim.copy(dataType = mapped) else claim
                } else claim
            }
            config.copy(claims = enrichedClaims)
        }
        return metadata.copy(credentialConfigurations = enrichedConfigs)
    }

    /**
     * Builds a `claimName ∪ alias → ClaimTypeDefinition` lookup. Canonical names
     * win on collision; aliases are added only if not already mapped.
     */
    private fun buildClaimTypeLookup(
        claimTypes: Map<String, ClaimTypeDefinition>
    ): Map<String, ClaimTypeDefinition> {
        val lookup: MutableMap<String, ClaimTypeDefinition> = mutableMapOf()
        for ((claimName, definition) in claimTypes) {
            lookup[claimName] = definition
        }
        for ((_, definition) in claimTypes) {
            for (alias in definition.aliases) {
                if (alias !in lookup) lookup[alias] = definition
            }
        }
        return lookup
    }

    private suspend fun loadCached(storage: LocalStorage): ResolvedRegistryData? {
        return try {
            val raw: String? = storage.getElement(storageKey)
            if (raw.isNullOrBlank()) return null
            json.decodeFromString(ResolvedRegistryData.serializer(), raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "Failed to read cached registry data: ${e.message}" }
            null
        }
    }

    private suspend fun persist(storage: LocalStorage, data: ResolvedRegistryData) {
        try {
            val raw: String = json.encodeToString(ResolvedRegistryData.serializer(), data)
            storage.saveElement(storageKey, raw)
            Logger.info(tag) { "Persisted resolved registry data (${data.credentials.size} credential(s))" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "Failed to persist registry data: ${e.message}" }
        }
    }

    companion object {
        /**
         * Global, unscoped storage key for the persisted [ResolvedRegistryData].
         *
         * Single-tenant by design: one cached blob per SDK install, refreshed
         * on every successful online resolution. See class-level KDoc for the
         * single-tenant rationale and the `clearLibraryLocalStorage()` escape
         * hatch.
         */
        const val STORAGE_KEY: String = "PROX_VER_REGISTRY_RESOLVED_DATA"

        @OptIn(ExperimentalTime::class)
        private fun defaultEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

        /**
         * Maps an IT-Wallet Claims Registry `type` value to the SDK's
         * [ClaimDescriptor] `DATA_TYPE_*` constant.
         *
         * Only the top-level `type` field drives the mapping for binary
         * payloads: `type: "string"` with `format: "data"` or
         * `format: "binary"` is intentionally returned as
         * [ClaimDescriptor.DATA_TYPE_STRING] so that
         * `CatalogBridge.KNOWN_ELEMENT_TYPES` can disambiguate by
         * identifier (e.g. `portrait` -> `Picture`,
         * `signature_usual_mark` -> `Picture`,
         * `biometric_template_finger` -> `Blob`, ...). Returning
         * `DATA_TYPE_BLOB` here would override the default `STRING`
         * dataType on claims and suppress the identifier-based fallback
         * in `CatalogBridge.resolveAttributeType`.
         *
         * `string` + `date` still maps to [ClaimDescriptor.DATA_TYPE_DATE],
         * `string` + `uri` to [ClaimDescriptor.DATA_TYPE_STRING], and
         * `type == "image"` to [ClaimDescriptor.DATA_TYPE_PICTURE].
         */
        internal fun mapRegistryType(registryType: String, registryFormat: String? = null): String {
            val type: String = registryType.lowercase()
            val format: String? = registryFormat?.lowercase()
            return when {
                type == "string" && format == "date" -> ClaimDescriptor.DATA_TYPE_DATE
                type == "string" && format == "uri" -> ClaimDescriptor.DATA_TYPE_STRING
                type in listOf("string", "email", "url") -> ClaimDescriptor.DATA_TYPE_STRING
                type == "date" -> ClaimDescriptor.DATA_TYPE_DATE
                type == "boolean" -> ClaimDescriptor.DATA_TYPE_BOOLEAN
                type in listOf("numeric", "number", "integer") -> ClaimDescriptor.DATA_TYPE_NUMBER
                type == "image" -> ClaimDescriptor.DATA_TYPE_PICTURE
                type in listOf("array", "object") -> ClaimDescriptor.DATA_TYPE_COMPLEX
                else -> ClaimDescriptor.DATA_TYPE_STRING
            }
        }
    }
}
