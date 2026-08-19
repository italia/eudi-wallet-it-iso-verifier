package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses OID4VCI credential configuration JSON into [CredentialConfiguration] models.
 *
 * Supports two JSON shapes:
 *
 * 1. **Single credential config** — matches one entry inside `credential_configurations_supported`:
 *    ```json
 *    { "doctype": "...", "credential_metadata": { "display": [...], "claims": [...] } }
 *    ```
 *
 * 2. **Full issuer metadata** with `credential_configurations_supported` map:
 *    ```json
 *    { "credential_configurations_supported": { "<id>": { "format": "mso_mdoc", "doctype": "...", ... } } }
 *    ```
 *
 * Structure follows [OID4VCI 1.0 Appendix A.2.2](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-credential-issuer-metadata-p):
 * - `doctype` is at the credential config level
 * - `display` and `claims` are inside the `credential_metadata` sub-object
 */
internal object CredentialConfigurationParser {

    private val json: Json = Json { ignoreUnknownKeys = true }

    private const val KEY_CREDENTIAL_METADATA = "credential_metadata"
    private const val KEY_CREDENTIAL_CONFIGURATIONS_SUPPORTED = "credential_configurations_supported"
    private const val FORMAT_MSO_MDOC = "mso_mdoc"

    /**
     * Parses a single credential config JSON (same shape as one entry
     * inside `credential_configurations_supported`).
     */
    fun parseSingle(jsonString: String): CredentialConfiguration {
        val root: JsonObject = parseJsonObject(jsonString)
        return parseCredentialObject(root)
    }

    /**
     * Parses full issuer metadata JSON.
     * Non-mso_mdoc entries are silently skipped.
     */
    fun parseIssuerMetadata(jsonString: String): List<CredentialConfiguration> {
        val root: JsonObject = parseJsonObject(jsonString)
        val configurationsSupported: JsonObject = root[KEY_CREDENTIAL_CONFIGURATIONS_SUPPORTED]?.jsonObject
            ?: throw ProximityVerificationException(
                "Missing '$KEY_CREDENTIAL_CONFIGURATIONS_SUPPORTED' key in issuer metadata",
                code = ProximityVerificationExceptionCode.PVE31()
            )
        val results: List<CredentialConfiguration> = configurationsSupported.entries
            .filter { (_, value) ->
                val format: String? = value.jsonObject["format"]?.jsonPrimitive?.content
                format == FORMAT_MSO_MDOC
            }
            .map { (_, value) -> parseCredentialObject(value.jsonObject) }
        if (results.isEmpty()) {
            throw ProximityVerificationException(
                "No mso_mdoc credential configurations found in issuer metadata",
                code = ProximityVerificationExceptionCode.PVE32()
            )
        }
        return results
    }

    /**
     * Auto-detects JSON shape and parses accordingly.
     */
    fun detectAndParse(jsonString: String): List<CredentialConfiguration> {
        val root: JsonObject = parseJsonObject(jsonString)
        if (root.containsKey(KEY_CREDENTIAL_CONFIGURATIONS_SUPPORTED)) {
            return parseIssuerMetadata(jsonString)
        }
        return listOf(parseSingle(jsonString))
    }

    private fun parseJsonObject(jsonString: String): JsonObject {
        return try {
            json.decodeFromString(JsonObject.serializer(), jsonString)
        } catch (e: Exception) {
            throw ProximityVerificationException(
                "Invalid JSON: ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE31()
            )
        }
    }

    /**
     * Parses a credential config object per OID4VCI Appendix A.2.2:
     * - `doctype` at the top level of the object
     * - `display` and `claims` inside the `credential_metadata` sub-object
     */
    private fun parseCredentialObject(obj: JsonObject): CredentialConfiguration {
        val docType: String = obj["doctype"]?.jsonPrimitive?.content
            ?: throw ProximityVerificationException(
                "Missing 'doctype' field in credential configuration",
                code = ProximityVerificationExceptionCode.PVE31()
            )
        val metadata: JsonObject? = obj[KEY_CREDENTIAL_METADATA]?.jsonObject
        val metadataSource: JsonObject = metadata ?: obj
        val display: List<CredentialDisplay> = parseDisplayArray(metadataSource)
        val claims: List<ClaimDescriptor> = parseClaims(metadataSource)
        return CredentialConfiguration(
            docType = docType,
            display = display,
            claims = claims
        )
    }

    private fun parseDisplayArray(obj: JsonObject): List<CredentialDisplay> {
        val displayArray = obj["display"]?.jsonArray ?: return emptyList()
        return displayArray.map { element ->
            val displayObj: JsonObject = element.jsonObject
            CredentialDisplay(
                // Spec (OID4VCI / IT-Wallet §10.2.5) uses "name"; tolerate "label" as a
                // fallback for issuers that emit the SD-JWT-VC field name (non-conformant).
                name = (displayObj["name"] ?: displayObj["label"])?.jsonPrimitive?.content ?: "",
                locale = displayObj["locale"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    /**
     * OID4VCI Appendix B.2: `"claims"` is a non-empty array of claim descriptor objects.
     * Each element has `"path": ["<namespace>", "<identifier>"]`, plus optional
     * `"mandatory"`, `"display"`, `"value_type"`, etc.
     *
     * Entries with missing or short `path` are silently skipped.
     */
    private fun parseClaims(obj: JsonObject): List<ClaimDescriptor> {
        val claimsArray = obj["claims"]?.jsonArray ?: return emptyList()
        return claimsArray.mapNotNull { element ->
            val claimObj: JsonObject = element.jsonObject
            val path = claimObj["path"]?.jsonArray ?: return@mapNotNull null
            if (path.size < 2) return@mapNotNull null
            val namespace: String = path[0].jsonPrimitive.content
            val identifier: String = path[1].jsonPrimitive.content
            val isMandatory: Boolean = claimObj["mandatory"]?.jsonPrimitive?.booleanOrNull ?: false
            val dataType: String = claimObj["value_type"]?.jsonPrimitive?.content
                ?: ClaimDescriptor.DATA_TYPE_STRING
            val claimDisplay: List<ClaimDisplay> = parseClaimDisplayArray(claimObj)
            ClaimDescriptor(
                ns = namespace,
                identifier = identifier,
                isMandatory = isMandatory,
                dataType = dataType,
                display = claimDisplay
            )
        }.distinctBy { it.ns to it.identifier }
    }

    private fun parseClaimDisplayArray(claimObj: JsonObject): List<ClaimDisplay> {
        val displayArray = claimObj["display"]?.jsonArray ?: return emptyList()
        return displayArray.map { element ->
            val displayObj: JsonObject = element.jsonObject
            ClaimDisplay(
                // Spec (OID4VCI / IT-Wallet §10.2.5) uses "name"; tolerate "label" as a
                // fallback for issuers that emit the SD-JWT-VC field name (non-conformant).
                name = (displayObj["name"] ?: displayObj["label"])?.jsonPrimitive?.content ?: "",
                locale = displayObj["locale"]?.jsonPrimitive?.content ?: ""
            )
        }
    }
}
