package it.infocert.proximityverification.core.models.initialization

import it.infocert.proximityverification.network.model.common.DeviceInfo

/**
 * Information about the Relying Party provider.
 *
 * @property baseUrl Base URL of the Relying Party backend
 * @property csrParam Certificate Signing Request parameters
 * @property deviceInfo Device information for registration
 * @property federationEntityUrl Base entity URL of the Relying Party in the OpenID
 *   Federation (e.g. `https://rp.example.it`). The SDK appends
 *   `/.well-known/openid-federation` for both the existing well-known config fetch
 *   and the federation registry resolver discovery (Phase 1).
 * @property trustAnchors List of trust anchor certificates (base64 encoded)
 * @property cloudProjectNumber Cloud Project Number for Android Play Integrity (optional)
 */
data class RelyingPartyProviderInfo(
    val baseUrl: String,
    val csrParam: CSRParam,
    val deviceInfo: DeviceInfo,
    val federationEntityUrl: String,
    val trustAnchors: List<String>,
    val cloudProjectNumber: String? = null
)
