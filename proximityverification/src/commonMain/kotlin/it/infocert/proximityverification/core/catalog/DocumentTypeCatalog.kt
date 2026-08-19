package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import kotlinx.coroutines.CancellationException

/**
 * Registry of known credential configurations (document types).
 *
 * Independent of [ProximityVerification.initialize] — the catalog is always available.
 * Thread-safe: reads do not block; writes use copy-on-write semantics.
 *
 * Example usage:
 * ```
 * val catalog = ProximityVerification.documentTypeCatalog
 * catalog.registerFromJson(myJsonString)
 * val configs = catalog.getRegisteredConfigurations()
 * ```
 */
interface DocumentTypeCatalog {

    /**
     * Registers credential configurations from a JSON string.
     * Auto-detects single credential config vs. full issuer metadata format.
     * On duplicate docType, last-write-wins (silently replaces).
     *
     * @throws ProximityVerificationException with PVE31 on invalid JSON.
     * @throws ProximityVerificationException with PVE32 if issuer metadata contains no mso_mdoc entries.
     */
    @Throws(ProximityVerificationException::class)
    fun registerFromJson(jsonString: String)

    /**
     * Fetches issuer metadata from `issuerUrl/.well-known/openid-credential-issuer`
     * and registers all mso_mdoc credential configurations.
     *
     * @throws ProximityVerificationException with PVE33 on network/HTTP errors.
     * @throws ProximityVerificationException with PVE32 if no mso_mdoc entries found.
     */
    @Throws(ProximityVerificationException::class, CancellationException::class)
    suspend fun registerFromIssuerMetadata(issuerUrl: String)

    /**
     * Returns a snapshot of all registered credential configurations.
     */
    fun getRegisteredConfigurations(): List<CredentialConfiguration>

    /**
     * Returns the credential configuration for the given docType, or null if not registered.
     */
    fun getConfiguration(docType: String): CredentialConfiguration?

    /**
     * Returns true if a credential configuration is registered for the given docType.
     */
    fun isRegistered(docType: String): Boolean

    /**
     * Removes the credential configuration for the given docType.
     * @return true if the configuration was found and removed, false otherwise.
     */
    fun unregister(docType: String): Boolean

    /**
     * Removes all registered credential configurations.
     */
    fun clear()
}
