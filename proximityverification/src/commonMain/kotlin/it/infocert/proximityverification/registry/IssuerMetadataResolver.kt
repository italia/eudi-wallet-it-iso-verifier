package it.infocert.proximityverification.registry

import io.ktor.http.encodeURLParameter
import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.core.catalog.CredentialConfigurationParser
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Step 5 of the federation registry resolution chain.
 *
 * For each issuer entity ID returned by the Credential Catalog, performs a
 * `federation_resolve` against the Trust Anchor and parses
 * `metadata.openid_credential_issuer` via [CredentialConfigurationParser].
 *
 * The `status_list_aggregation_endpoint` (when present) is extracted and stored
 * for future offline status verification; it is not consumed in Phase 1.
 */
internal class IssuerMetadataResolver(
    private val http: RegistryHttp = RegistryHttp()
) {

    private val tag: String = "IssuerMetadataResolver"

    /**
     * Resolves a single issuer via the federation resolve endpoint.
     *
     * @param resolveEndpoint URL of the federation resolve endpoint discovered
     *   in the TA Entity Configuration.
     * @param issuerEntityId the `sub` parameter of the resolve request.
     * @param trustAnchorUrl `trust_anchor` parameter, recommended by OpenID
     *   Federation §7.2.
     * @return [IssuerResolvedMetadata] or `null` if any step fails (HTTP,
     *   missing metadata, or no `mso_mdoc` configuration in the issuer payload).
     */
    suspend fun resolveIssuer(
        resolveEndpoint: String,
        issuerEntityId: String,
        trustAnchorUrl: String
    ): IssuerResolvedMetadata? {
        val withEntityTypeUrl: String = buildResolveUrl(
            resolveEndpoint = resolveEndpoint,
            issuerEntityId = issuerEntityId,
            trustAnchorUrl = trustAnchorUrl,
            includeEntityType = true
        )
        Logger.debug(tag) { "Step 5: GET $withEntityTypeUrl" }
        val payload: JsonObject = http.getJsonOrJwsPayload(withEntityTypeUrl)
            ?: run {
                Logger.info(tag) {
                    "Step 5 retry for $issuerEntityId: dropping entity_type=openid_credential_issuer"
                }
                http.getJsonOrJwsPayload(buildResolveUrl(
                    resolveEndpoint = resolveEndpoint,
                    issuerEntityId = issuerEntityId,
                    trustAnchorUrl = trustAnchorUrl,
                    includeEntityType = false
                ))
            }
            ?: run {
                Logger.warn(tag) { "Step 5 failed for $issuerEntityId: resolve endpoint unreachable" }
                return null
            }
        val openidCredentialIssuer: JsonObject? = payload["metadata"]?.jsonObject
            ?.get("openid_credential_issuer")?.jsonObject
        if (openidCredentialIssuer == null) {
            Logger.warn(tag) { "Step 5 failed for $issuerEntityId: missing metadata.openid_credential_issuer" }
            return null
        }
        val configurations: List<CredentialConfiguration> = try {
            CredentialConfigurationParser.parseIssuerMetadata(openidCredentialIssuer.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.warn(tag) { "Step 5 failed for $issuerEntityId: ${e.message}" }
            return null
        } catch (e: Throwable) {
            Logger.warn(tag) { "Step 5 failed for $issuerEntityId: ${e.message}" }
            return null
        }
        val statusListAggregationEndpoint: String? = openidCredentialIssuer["status_list_aggregation_endpoint"]
            ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val jwks: JsonObject? = openidCredentialIssuer["jwks"]?.jsonObject
        Logger.debug(tag) {
            buildString {
                append("Step 5 ok for $issuerEntityId: ${configurations.size} mso_mdoc config(s)")
                if (statusListAggregationEndpoint != null) append(", statusListAggregation present")
                if (jwks != null) append(", jwks present")
            }
        }
        return IssuerResolvedMetadata(
            entityId = issuerEntityId,
            statusListAggregationEndpoint = statusListAggregationEndpoint,
            credentialConfigurations = configurations,
            jwks = jwks
        )
    }

    private fun buildResolveUrl(
        resolveEndpoint: String,
        issuerEntityId: String,
        trustAnchorUrl: String,
        includeEntityType: Boolean
    ): String {
        val separator: String = if (resolveEndpoint.contains('?')) "&" else "?"
        val sub: String = issuerEntityId.encodeURLParameter()
        val anchor: String = trustAnchorUrl.encodeURLParameter()
        val base: String = "$resolveEndpoint${separator}sub=$sub&trust_anchor=$anchor"
        return if (includeEntityType) "$base&entity_type=openid_credential_issuer" else base
    }
}
