package it.infocert.proximityverification.core.models.initialization

/**
 * Parameters for initializing a Relying Party instance (V2 API).
 *
 * This represents the structural/bootstrap configuration that remains stable
 * across multiple verification sessions.
 *
 * @property providerInfo Information about the Relying Party provider.
 * @property allowedIssuerIds Optional list of issuer entity IDs used to filter
 *   the IT-Wallet Credential Catalog at federation registry resolution time
 *   (Phase 1). Empty list means no filtering: every `mso_mdoc` entry of the
 *   catalog is accepted.
 */
data class RelyingPartyInstanceParams(
    val providerInfo: RelyingPartyProviderInfo,
    val allowedIssuerIds: List<String> = emptyList()
)
