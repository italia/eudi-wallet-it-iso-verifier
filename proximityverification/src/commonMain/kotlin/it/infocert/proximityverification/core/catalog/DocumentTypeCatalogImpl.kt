@file:OptIn(ExperimentalAtomicApi::class)

package it.infocert.proximityverification.core.catalog

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.HttpClientProvider
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CancellationException

/**
 * Default implementation of [DocumentTypeCatalog] backed by an [AtomicReference] to an
 * immutable map, providing lock-free reads and copy-on-write mutations.
 *
 * The catalog is always available, independent of `ProximityVerification.initialize()`.
 * Consumers may register and unregister document types at any time from any thread.
 */
internal class DocumentTypeCatalogImpl : DocumentTypeCatalog {

    private val TAG: String = "DocumentTypeCatalog"

    private val storage: AtomicReference<Map<String, CredentialConfiguration>> =
        AtomicReference(emptyMap())

    override fun registerFromJson(jsonString: String) {
        val configurations: List<CredentialConfiguration> =
            CredentialConfigurationParser.detectAndParse(jsonString)
        registerAll(configurations, source = "JSON")
    }

    override suspend fun registerFromIssuerMetadata(issuerUrl: String) {
        val wellKnownUrl: String = buildWellKnownUrl(issuerUrl)
        Logger.info(TAG) { "Fetching issuer metadata from $wellKnownUrl" }
        val responseBody: String = fetchIssuerMetadata(wellKnownUrl)
        val configurations: List<CredentialConfiguration> =
            CredentialConfigurationParser.parseIssuerMetadata(responseBody)
        registerAll(configurations, source = "issuer metadata ($issuerUrl)")
    }

    override fun getRegisteredConfigurations(): List<CredentialConfiguration> {
        return storage.load().values.toList()
    }

    override fun getConfiguration(docType: String): CredentialConfiguration? {
        return storage.load()[docType]
    }

    override fun isRegistered(docType: String): Boolean {
        return storage.load().containsKey(docType)
    }

    override fun unregister(docType: String): Boolean {
        val current: Map<String, CredentialConfiguration> = storage.load()
        if (!current.containsKey(docType)) {
            Logger.debug(TAG) { "Unregister skipped — docType not found: $docType" }
            return false
        }
        storage.store(current - docType)
        Logger.info(TAG) { "Unregistered docType: $docType" }
        return true
    }

    override fun clear() {
        val count: Int = storage.load().size
        storage.store(emptyMap())
        Logger.info(TAG) { "Cleared catalog ($count configuration(s) removed)" }
    }

    private fun registerAll(configurations: List<CredentialConfiguration>, source: String) {
        val current: Map<String, CredentialConfiguration> = storage.load()
        val updated: MutableMap<String, CredentialConfiguration> = current.toMutableMap()
        for (config in configurations) {
            val replacing: Boolean = updated.containsKey(config.docType)
            updated[config.docType] = config
            if (replacing) {
                Logger.debug(TAG) { "Replaced docType: ${config.docType} (${config.claims.size} claims) from $source" }
            } else {
                Logger.info(TAG) { "Registered docType: ${config.docType} (${config.claims.size} claims) from $source" }
            }
        }
        storage.store(updated.toMap())
        Logger.info(TAG) { "Registration complete — ${configurations.size} configuration(s) from $source, catalog now has ${updated.size} total" }
    }

    private fun buildWellKnownUrl(issuerUrl: String): String {
        val base: String = issuerUrl.trimEnd('/')
        return "$base/.well-known/openid-credential-issuer"
    }

    @Throws(ProximityVerificationException::class, CancellationException::class)
    private suspend fun fetchIssuerMetadata(url: String): String {
        return try {
            val response = HttpClientProvider.client.get(url)
            if (!response.status.isSuccess()) {
                throw ProximityVerificationException(
                    "Issuer metadata fetch failed with HTTP ${response.status.value}",
                    code = ProximityVerificationExceptionCode.PVE33()
                )
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            throw e
        } catch (e: Exception) {
            throw ProximityVerificationException(
                "Issuer metadata fetch failed: ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE33()
            )
        }
    }
}
