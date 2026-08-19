package it.infocert.proximityverification.registry

import io.ktor.http.ContentType
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.FederationEndpoints
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Steps 1–2 of the federation registry resolution chain.
 *
 * - Step 1: fetch the RP Entity Configuration at
 *   `{federationEntityUrl}/.well-known/openid-federation`, extract the first
 *   value of `authority_hints` to obtain the Trust Anchor URL.
 * - Step 2: fetch the TA Entity Configuration at
 *   `{trustAnchorUrl}/.well-known/openid-federation`, extract
 *   `metadata.federation_entity.federation_resolve_endpoint` (and the optional
 *   `federation_fetch_endpoint`).
 *
 * Each HTTP call uses [RegistryHttp.CALL_TIMEOUT_MILLIS]. Any failure returns
 * `null` (the caller falls back to cache/built-in defaults). Cancellation is
 * always re-thrown by the underlying helpers.
 */
internal class FederationDiscovery(
    private val http: RegistryHttp = RegistryHttp()
) {

    private val tag: String = "FederationDiscovery"

    /**
     * Discovers the [FederationEndpoints] starting from [federationEntityUrl],
     * the base entity URL of the Relying Party in the federation.
     */
    suspend fun discover(federationEntityUrl: String): FederationEndpoints? {
        val rpConfigUrl: String = RegistryHttp.federationEntityConfigUrl(federationEntityUrl)
        Logger.debug(tag) { "Step 1: GET $rpConfigUrl" }
        val rpJwt: String? = http.getOrNull(rpConfigUrl, ContentType("application", "entity-statement+jwt"))
            ?: http.getOrNull(rpConfigUrl, ContentType("application", "jwt"))
        if (rpJwt == null) {
            Logger.warn(tag) { "Step 1 failed: RP entity configuration unreachable" }
            return null
        }
        val rpClaims: JsonObject = http.decodeJwsPayload(rpJwt) ?: run {
            Logger.warn(tag) { "Step 1 failed: cannot decode RP entity statement payload" }
            return null
        }
        val taUrl: String = extractTrustAnchorUrl(rpClaims) ?: run {
            Logger.warn(tag) { "Step 1 failed: no authority_hints in RP entity configuration" }
            return null
        }
        Logger.debug(tag) { "Step 1 ok: trustAnchor=$taUrl" }

        val taConfigUrl: String = RegistryHttp.federationEntityConfigUrl(taUrl)
        Logger.debug(tag) { "Step 2: GET $taConfigUrl" }
        val taJwt: String? = http.getOrNull(taConfigUrl, ContentType("application", "entity-statement+jwt"))
            ?: http.getOrNull(taConfigUrl, ContentType("application", "jwt"))
        if (taJwt == null) {
            Logger.warn(tag) { "Step 2 failed: TA entity configuration unreachable" }
            return null
        }
        val taClaims: JsonObject = http.decodeJwsPayload(taJwt) ?: run {
            Logger.warn(tag) { "Step 2 failed: cannot decode TA entity statement payload" }
            return null
        }
        val federationEntity: JsonObject? = taClaims["metadata"]?.jsonObject
            ?.get("federation_entity")?.jsonObject
        if (federationEntity == null) {
            Logger.warn(tag) { "Step 2 failed: missing metadata.federation_entity" }
            return null
        }
        val resolveEndpoint: String? = federationEntity["federation_resolve_endpoint"]
            ?.jsonPrimitive?.contentOrNull
        if (resolveEndpoint.isNullOrBlank()) {
            Logger.warn(tag) { "Step 2 failed: missing federation_resolve_endpoint" }
            return null
        }
        val fetchEndpoint: String? = federationEntity["federation_fetch_endpoint"]
            ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        Logger.debug(tag) { "Step 2 ok: resolveEndpoint=$resolveEndpoint" }
        return FederationEndpoints(
            trustAnchorUrl = taUrl,
            resolveEndpoint = resolveEndpoint,
            fetchEndpoint = fetchEndpoint
        )
    }

    private fun extractTrustAnchorUrl(rpClaims: JsonObject): String? {
        val hints = rpClaims["authority_hints"]?.jsonArray ?: return null
        for (element in hints) {
            val candidate: String? = runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
            if (!candidate.isNullOrBlank()) return candidate
        }
        return null
    }
}
