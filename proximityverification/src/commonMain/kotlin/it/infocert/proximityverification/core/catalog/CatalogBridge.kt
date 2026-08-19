package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.VerificationRequest
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.Icon

/**
 * Maps catalog models to multipaz (identity-credential) types.
 * All `org.multipaz.*` references are confined to this object.
 */
internal object CatalogBridge {

    /**
     * Well-known ISO 18013-5 data element identifiers mapped to their
     * semantic type. Used as fallback when the JSON metadata doesn't
     * carry an explicit `value_type`.
     */
    private val KNOWN_ELEMENT_TYPES: Map<String, DocumentAttributeType> = mapOf(
        "birth_date" to DocumentAttributeType.Date,
        "issue_date" to DocumentAttributeType.Date,
        "expiry_date" to DocumentAttributeType.Date,
        "portrait_capture_date" to DocumentAttributeType.Date,
        "portrait" to DocumentAttributeType.Picture,
        "signature_usual_mark" to DocumentAttributeType.Picture,
        "driving_privileges" to DocumentAttributeType.ComplexType,
        "biometric_template_face" to DocumentAttributeType.Picture,
        "biometric_template_finger" to DocumentAttributeType.Blob,
        "biometric_template_signature_sign" to DocumentAttributeType.Picture,
        "verification" to DocumentAttributeType.ComplexType,
        "age_in_years" to DocumentAttributeType.Number,
        "age_birth_year" to DocumentAttributeType.Number,
        "age_over_13" to DocumentAttributeType.Boolean,
        "age_over_16" to DocumentAttributeType.Boolean,
        "age_over_18" to DocumentAttributeType.Boolean,
        "age_over_21" to DocumentAttributeType.Boolean,
        "age_over_25" to DocumentAttributeType.Boolean,
        "age_over_60" to DocumentAttributeType.Boolean,
        "age_over_62" to DocumentAttributeType.Boolean,
        "age_over_65" to DocumentAttributeType.Boolean,
        "age_over_68" to DocumentAttributeType.Boolean,
    )

    /**
     * Converts a [CredentialConfiguration] into a multipaz [DocumentType]
     * suitable for [org.multipaz.documenttype.DocumentTypeRepository].
     */
    fun toDocumentType(config: CredentialConfiguration): DocumentType {
        val displayName: String = config.display.firstOrNull()?.name ?: config.docType
        val builder: DocumentType.Builder = DocumentType.Builder(displayName)
            .addMdocDocumentType(config.docType)
        for (claim in config.claims) {
            builder.addMdocAttribute(
                resolveAttributeType(claim),
                claim.identifier,
                claim.display.firstOrNull()?.name ?: claim.identifier,
                "",
                claim.isMandatory,
                claim.ns,
                icon = Icon.PERSON,
                sampleValue = null
            )
        }
        return builder.build()
    }

    /**
     * `true` if a data element with this identifier is classified as an image.
     *
     * Deliberately **internal**: it reads [KNOWN_ELEMENT_TYPES], a compile-time table, whereas the
     * authority on claim naming is the federation registry resolved at runtime — which already
     * publishes per-claim `aliases` (see `RegistryResolver`). Publishing this to consumers would turn
     * an internal assumption into a contract they build on, with no way for them to notice when the
     * registry disagrees. A wrong entry here is one bug to fix in one place; a published one is not.
     */
    fun isPictureIdentifier(claimIdentifier: String): Boolean =
        KNOWN_ELEMENT_TYPES[claimIdentifier] == DocumentAttributeType.Picture

    /**
     * Resolves the [DocumentAttributeType] for a claim.
     * Priority: explicit [ClaimDescriptor.dataType] > well-known identifier lookup > String.
     */
    private fun resolveAttributeType(claim: ClaimDescriptor): DocumentAttributeType {
        if (claim.dataType != ClaimDescriptor.DATA_TYPE_STRING) {
            return mapDataType(claim.dataType)
        }
        return KNOWN_ELEMENT_TYPES[claim.identifier] ?: DocumentAttributeType.String
    }

    private fun mapDataType(dataType: String): DocumentAttributeType {
        return when (dataType) {
            ClaimDescriptor.DATA_TYPE_STRING -> DocumentAttributeType.String
            ClaimDescriptor.DATA_TYPE_DATE -> DocumentAttributeType.Date
            ClaimDescriptor.DATA_TYPE_FULL_DATE -> DocumentAttributeType.Date
            ClaimDescriptor.DATA_TYPE_DATE_TIME -> DocumentAttributeType.DateTime
            ClaimDescriptor.DATA_TYPE_NUMBER -> DocumentAttributeType.Number
            ClaimDescriptor.DATA_TYPE_UINT -> DocumentAttributeType.Number
            ClaimDescriptor.DATA_TYPE_BOOLEAN -> DocumentAttributeType.Boolean
            ClaimDescriptor.DATA_TYPE_PICTURE -> DocumentAttributeType.Picture
            ClaimDescriptor.DATA_TYPE_PICTURE_JPEG -> DocumentAttributeType.Picture
            ClaimDescriptor.DATA_TYPE_COMPLEX -> DocumentAttributeType.ComplexType
            ClaimDescriptor.DATA_TYPE_BLOB -> DocumentAttributeType.Blob
            else -> DocumentAttributeType.String
        }
    }

    /**
     * Converts a [VerificationRequest] (built via the request builder)
     * into a multipaz [DocumentCannedRequest] by building a temporary [DocumentType]
     * with a sample request matching the requested claims.
     */
    fun toCannedRequest(request: VerificationRequest): DocumentCannedRequest {
        val builder: DocumentType.Builder = DocumentType.Builder(request.docType)
            .addMdocDocumentType(request.docType)
        for ((namespace, elements) in request.requestedClaims) {
            for (elementId in elements.keys) {
                builder.addMdocAttribute(
                    KNOWN_ELEMENT_TYPES[elementId] ?: DocumentAttributeType.String,
                    elementId,
                    elementId,
                    "",
                    false,
                    namespace,
                    icon = Icon.PERSON,
                    sampleValue = null
                )
            }
        }
        builder.addSampleRequest(
            id = "dynamic_request",
            displayName = request.docType,
            mdocDataElements = request.requestedClaims
        )
        val documentType: DocumentType = builder.build()
        return documentType.cannedRequests.first()
    }
}
