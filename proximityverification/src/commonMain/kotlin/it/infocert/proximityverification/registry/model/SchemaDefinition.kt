package it.infocert.proximityverification.registry.model

import kotlinx.serialization.Serializable

/**
 * Single entry from the IT-Wallet Schema Registry.
 *
 * The Schema Registry correlates a catalog `credential_type` (and optional
 * `version`) with a technical `format` and, for `mso_mdoc`, the expected
 * `docType`. The SDK uses it as a correlator only: schema content (CDDL/JSON
 * Schema) and integrity validation are out of scope for this phase.
 *
 * @property id schema identifier as published by the Trust Anchor.
 * @property version schema version, when published.
 * @property credentialType catalog credential type this schema applies to.
 * @property format technical format (e.g. `"mso_mdoc"`, `"dc+sd-jwt"`).
 * @property docType ISO 18013-5 docType for `mso_mdoc` schemas, when published.
 * @property schemaUri pointer to the schema document, when published.
 * @property schemaIntegrity integrity digest of the schema document, when published.
 * @property description human-readable description, when published.
 */
@Serializable
data class SchemaDefinition(
    val id: String,
    val version: String? = null,
    val credentialType: String,
    val format: String,
    val docType: String? = null,
    val schemaUri: String? = null,
    val schemaIntegrity: String? = null,
    val description: String? = null
)
