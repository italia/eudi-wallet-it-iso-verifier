package it.infocert.proximityverification.core.catalog

import kotlinx.serialization.Serializable

/**
 * Describes an mso_mdoc credential configuration aligned with OID4VCI §12.2.4.
 *
 * Example usage:
 * ```
 * val config = CredentialConfiguration(
 *     docType = "org.iso.18013.5.1.mDL",
 *     display = listOf(CredentialDisplay("Driving License", "en")),
 *     claims = listOf(
 *         ClaimDescriptor(ns = "org.iso.18013.5.1", identifier = "family_name",
 *             isMandatory = true, dataType = "date",
 *             display = listOf(ClaimDisplay("Family Name", "en")))
 *     )
 * )
 * ```
 */
@Serializable
data class CredentialConfiguration(
    val docType: String,
    val display: List<CredentialDisplay>,
    val claims: List<ClaimDescriptor>
)

/**
 * Localized display metadata for a credential configuration.
 */
@Serializable
data class CredentialDisplay(
    val name: String,
    val locale: String
)

/**
 * Describes a single claim (data element) within a credential configuration.
 *
 * @property ns The ISO/mdoc namespace this claim belongs to.
 * @property identifier The data element identifier within the namespace.
 * @property isMandatory Whether this claim is mandatory per the credential schema.
 * @property dataType Semantic type hint used for correct CBOR rendering.
 *   Recognised values: `"string"`, `"date"`, `"date_time"`, `"number"`,
 *   `"boolean"`, `"picture"`, `"complex"`, `"blob"`. Defaults to `"string"`.
 * @property display Localized display metadata for this claim.
 */
@Serializable
data class ClaimDescriptor(
    val ns: String,
    val identifier: String,
    val isMandatory: Boolean,
    val dataType: String = DATA_TYPE_STRING,
    val display: List<ClaimDisplay>
) {
    companion object {
        const val DATA_TYPE_STRING: String = "string"
        const val DATA_TYPE_DATE: String = "date"
        const val DATA_TYPE_FULL_DATE: String = "full-date"
        const val DATA_TYPE_DATE_TIME: String = "date_time"
        const val DATA_TYPE_NUMBER: String = "number"
        const val DATA_TYPE_UINT: String = "uint"
        const val DATA_TYPE_BOOLEAN: String = "boolean"
        const val DATA_TYPE_PICTURE: String = "picture"
        const val DATA_TYPE_PICTURE_JPEG: String = "jpeg"
        const val DATA_TYPE_COMPLEX: String = "complex"
        const val DATA_TYPE_BLOB: String = "blob"
    }
}

/**
 * Localized display metadata for a claim.
 */
@Serializable
data class ClaimDisplay(
    val name: String,
    val locale: String
)
