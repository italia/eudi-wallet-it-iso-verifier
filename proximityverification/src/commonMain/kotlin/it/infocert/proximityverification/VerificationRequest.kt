package it.infocert.proximityverification

/**
 * Opaque verification request for a single document type.
 *
 * Constructed exclusively via [VerificationRequestBuilder.build].
 * [requestedClaims] maps `namespace → (identifier → intentToRetain)`.
 */
class VerificationRequest internal constructor(
    internal val docType: String,
    internal val requestedClaims: Map<String, Map<String, Boolean>>
)
