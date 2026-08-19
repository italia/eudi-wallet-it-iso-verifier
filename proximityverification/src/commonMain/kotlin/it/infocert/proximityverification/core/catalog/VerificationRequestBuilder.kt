package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.VerificationRequest
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import kotlin.Throws

/**
 * Builds [VerificationRequest] instances by selecting claims from the [DocumentTypeCatalog].
 *
 * Example usage:
 * ```
 * val builder = VerificationRequestBuilder(catalog)
 * val request = builder.forDocType("org.iso.18013.5.1.mDL")
 *     .requestMandatoryClaims()
 *     .build()
 * ```
 */
class VerificationRequestBuilder(
    private val catalog: DocumentTypeCatalog
) {
    /**
     * Start building a request for the given docType.
     *
     * @throws ProximityVerificationException with PVE35 if the docType is not registered.
     */
    @Throws(ProximityVerificationException::class)
    fun forDocType(docType: String): DocTypeRequestScope {
        val configuration: CredentialConfiguration = catalog.getConfiguration(docType)
            ?: throw ProximityVerificationException(
                "docType '$docType' is not registered in the catalog",
                code = ProximityVerificationExceptionCode.PVE35()
            )
        return DocTypeRequestScope(configuration)
    }
}

/**
 * Scoped builder for selecting claims within a specific document type.
 * Supports fluent chaining.
 */
class DocTypeRequestScope internal constructor(
    private val configuration: CredentialConfiguration
) {
    private val selectedClaims: MutableMap<String, MutableMap<String, Boolean>> = mutableMapOf()

    /**
     * Request a specific claim by namespace and identifier.
     * Validation against the schema happens at [build] time.
     */
    fun requestClaim(
        namespace: String,
        identifier: String,
        intentToRetain: Boolean = false
    ): DocTypeRequestScope {
        selectedClaims.getOrPut(namespace) { mutableMapOf() }[identifier] = intentToRetain
        return this
    }

    /**
     * Request all claims defined in the credential schema.
     */
    fun requestAllClaims(intentToRetain: Boolean = false): DocTypeRequestScope {
        for (claim in configuration.claims) {
            selectedClaims.getOrPut(claim.ns) { mutableMapOf() }[claim.identifier] =
                intentToRetain
        }
        return this
    }

    /**
     * Request only mandatory claims (where [ClaimDescriptor.isMandatory] is true).
     */
    fun requestMandatoryClaims(intentToRetain: Boolean = false): DocTypeRequestScope {
        for (claim in configuration.claims) {
            if (claim.isMandatory) {
                selectedClaims.getOrPut(claim.ns) { mutableMapOf() }[claim.identifier] =
                    intentToRetain
            }
        }
        return this
    }

    /**
     * Request all claims within a specific namespace.
     */
    fun requestNamespace(
        namespace: String,
        intentToRetain: Boolean = false
    ): DocTypeRequestScope {
        for (claim in configuration.claims) {
            if (claim.ns == namespace) {
                selectedClaims.getOrPut(claim.ns) { mutableMapOf() }[claim.identifier] =
                    intentToRetain
            }
        }
        return this
    }

    /**
     * Build the [VerificationRequest]. Validates that all explicitly added claims
     * (via [requestClaim]) exist in the schema.
     *
     * @throws ProximityVerificationException with PVE34 if any claim is not found in the schema.
     */
    @Throws(ProximityVerificationException::class)
    fun build(): VerificationRequest {
        validateClaims()
        val immutableClaims: Map<String, Map<String, Boolean>> =
            selectedClaims.mapValues { (_, v) -> v.toMap() }
        return VerificationRequest(
            docType = configuration.docType,
            requestedClaims = immutableClaims
        )
    }

    private fun validateClaims() {
        val knownClaims: Set<Pair<String, String>> =
            configuration.claims.map { it.ns to it.identifier }.toSet()
        for ((namespace, identifiers) in selectedClaims) {
            for (identifier in identifiers.keys) {
                if ((namespace to identifier) !in knownClaims) {
                    throw ProximityVerificationException(
                        "Claim '$namespace/$identifier' not found in schema for docType '${configuration.docType}'",
                        code = ProximityVerificationExceptionCode.PVE34()
                    )
                }
            }
        }
    }
}
