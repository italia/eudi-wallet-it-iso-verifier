package it.infocert.etuitus.sampleandroid

import android.content.Context
import android.content.SharedPreferences
import it.infocert.proximityverification.VerificationRequest
import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.core.catalog.DocumentTypeCatalog
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.models.initialization.TransportPreference
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SampleSettings(
    val blePeripheralServerModeEnabled: Boolean = true,
    val bleCentralClientModeEnabled: Boolean = true,
    val nfcEnabled: Boolean = true,
    val preferredTransport: String = "AUTO",
    val bleUseL2Cap: Boolean = false,
    val bleUseL2CapInEngagement: Boolean = true,
    val allowMultipleRequests: Boolean = false,
    val nfcScanMessage: String = "Hold near credential holder's device",
    val nfcTransferMessage: String = "Transferring data, keep in reader's field"
) {
    fun buildVerificationSettings(requests: List<VerificationRequest>): ProximityVerificationSettings {
        return ProximityVerificationSettings(
            requests = requests,
            allowMultipleRequests = allowMultipleRequests,
            bleEnabled = bleCentralClientModeEnabled || blePeripheralServerModeEnabled,
            nfcDataTransferEnabled = nfcEnabled,
            bleCentralClientMode = bleCentralClientModeEnabled,
            blePeripheralServerMode = blePeripheralServerModeEnabled,
            bleUseL2CAP = bleUseL2Cap,
            bleUseL2CAPInEngagement = bleUseL2CapInEngagement,
            nfcScanMessage = nfcScanMessage.ifBlank { "Hold near credential holder's device" },
            nfcTransferMessage = nfcTransferMessage.ifBlank { "Transferring data, keep in reader's field" },
            preferredTransport = resolveTransportPreference(),
        )
    }

    private fun resolveTransportPreference(): TransportPreference = when (preferredTransport) {
        "BLE" -> TransportPreference.BLE
        "NFC" -> TransportPreference.NFC
        else -> TransportPreference.AUTO
    }
}

class SettingsRepository(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SampleSettings {
        val default = SampleSettings()
        return SampleSettings(
            blePeripheralServerModeEnabled = preferences.getBoolean(KEY_BLE_PERIPHERAL_SERVER_MODE, default.blePeripheralServerModeEnabled),
            bleCentralClientModeEnabled = preferences.getBoolean(KEY_BLE_CENTRAL_CLIENT_MODE, default.bleCentralClientModeEnabled),
            nfcEnabled = preferences.getBoolean(KEY_NFC_ENABLED, default.nfcEnabled),
            preferredTransport = preferences.getString(KEY_PREFERRED_TRANSPORT, default.preferredTransport) ?: default.preferredTransport,
            bleUseL2Cap = preferences.getBoolean(KEY_BLE_USE_L2CAP, default.bleUseL2Cap),
            bleUseL2CapInEngagement = preferences.getBoolean(KEY_BLE_USE_L2CAP_IN_ENGAGEMENT, default.bleUseL2CapInEngagement),
            allowMultipleRequests = preferences.getBoolean(KEY_ALLOW_MULTIPLE_REQUESTS, default.allowMultipleRequests),
            nfcScanMessage = preferences.getString(KEY_NFC_SCAN_MESSAGE, default.nfcScanMessage).orEmpty(),
            nfcTransferMessage = preferences.getString(KEY_NFC_TRANSFER_MESSAGE, default.nfcTransferMessage).orEmpty()
        )
    }

    fun save(settings: SampleSettings) {
        preferences.edit()
            .putBoolean(KEY_BLE_PERIPHERAL_SERVER_MODE, settings.blePeripheralServerModeEnabled)
            .putBoolean(KEY_BLE_CENTRAL_CLIENT_MODE, settings.bleCentralClientModeEnabled)
            .putBoolean(KEY_NFC_ENABLED, settings.nfcEnabled)
            .putString(KEY_PREFERRED_TRANSPORT, settings.preferredTransport)
            .putBoolean(KEY_BLE_USE_L2CAP, settings.bleUseL2Cap)
            .putBoolean(KEY_BLE_USE_L2CAP_IN_ENGAGEMENT, settings.bleUseL2CapInEngagement)
            .putBoolean(KEY_ALLOW_MULTIPLE_REQUESTS, settings.allowMultipleRequests)
            .putString(KEY_NFC_SCAN_MESSAGE, settings.nfcScanMessage)
            .putString(KEY_NFC_TRANSFER_MESSAGE, settings.nfcTransferMessage)
            .apply()
    }

    fun resetToDefaults(): SampleSettings {
        preferences.edit().clear().apply()
        val defaultSettings = SampleSettings()
        save(defaultSettings)
        return defaultSettings
    }

    companion object {
        private const val PREFS_NAME = "sample_app_settings"
        private const val KEY_BLE_PERIPHERAL_SERVER_MODE = "ble_peripheral_server_mode"
        private const val KEY_BLE_CENTRAL_CLIENT_MODE = "ble_central_client_mode"
        private const val KEY_NFC_ENABLED = "nfc_enabled"
        private const val KEY_PREFERRED_TRANSPORT = "preferred_transport"
        private const val KEY_BLE_USE_L2CAP = "ble_use_l2cap"
        private const val KEY_BLE_USE_L2CAP_IN_ENGAGEMENT = "ble_use_l2cap_in_engagement"
        private const val KEY_ALLOW_MULTIPLE_REQUESTS = "allow_multiple_requests"
        private const val KEY_NFC_SCAN_MESSAGE = "nfc_scan_message"
        private const val KEY_NFC_TRANSFER_MESSAGE = "nfc_transfer_message"
    }
}

@Serializable
data class RequestConfig(
    val docTypeConfigs: Map<String, DocTypeSelectionConfig> = emptyMap(),
    /**
     * L'operatore ha dichiarato di volere la corroborazione del ritratto (vedi
     * [PortraitCorroboration]). È **intenzione dichiarata**, non inferita dalla forma della richiesta:
     * se la selezione dei claim viene poi modificata a mano e la forma non è più quella attesa, lo si
     * segnala invece di far sparire la diagnostica in silenzio.
     */
    val corroborationRequested: Boolean = false
) {
    companion object {
        fun claimKey(namespace: String, identifier: String): String = "$namespace::$identifier"

        fun defaultForCatalog(catalog: DocumentTypeCatalog): RequestConfig {
            val configs: Map<String, DocTypeSelectionConfig> =
                catalog.getRegisteredConfigurations().associate { config ->
                    config.docType to defaultForDocType(config)
                }
            return RequestConfig(docTypeConfigs = configs)
        }

        fun defaultForDocType(config: CredentialConfiguration): DocTypeSelectionConfig {
            val claims: Map<String, ClaimSelectionEntry> = config.claims.associate { claim ->
                claimKey(claim.ns, claim.identifier) to ClaimSelectionEntry(
                    selected = false,
                    intentToRetain = false
                )
            }
            return DocTypeSelectionConfig(enabled = false, claimSelections = claims)
        }

        fun mergeWithCatalog(
            registeredConfigs: List<CredentialConfiguration>,
            existing: RequestConfig
        ): RequestConfig {
            val merged: Map<String, DocTypeSelectionConfig> = registeredConfigs.associate { credConfig ->
                val existingDocConfig: DocTypeSelectionConfig? = existing.docTypeConfigs[credConfig.docType]
                if (existingDocConfig == null || existingDocConfig.claimSelections.isEmpty()) {
                    credConfig.docType to defaultForDocType(credConfig)
                } else {
                    val defaultClaims: Map<String, ClaimSelectionEntry> =
                        defaultForDocType(credConfig).claimSelections
                    val mergedClaims: Map<String, ClaimSelectionEntry> = defaultClaims + existingDocConfig.claimSelections
                    credConfig.docType to existingDocConfig.copy(claimSelections = mergedClaims)
                }
            }
            // `copy`, non un nuovo RequestConfig: un merge con il catalog non deve perdere
            // l'intenzione dichiarata dall'operatore (es. la corroborazione del ritratto).
            return existing.copy(docTypeConfigs = merged)
        }
    }
}

@Serializable
data class DocTypeSelectionConfig(
    val enabled: Boolean = true,
    val claimSelections: Map<String, ClaimSelectionEntry> = emptyMap()
)

@Serializable
data class ClaimSelectionEntry(
    val selected: Boolean = false,
    val intentToRetain: Boolean = false
)

class RequestConfigRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json: Json = Json { ignoreUnknownKeys = true }

    fun load(): RequestConfig? {
        val raw: String = preferences.getString(KEY_CONFIG, null) ?: return null
        return try {
            json.decodeFromString<RequestConfig>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun save(config: RequestConfig) {
        val raw: String = json.encodeToString(config)
        preferences.edit().putString(KEY_CONFIG, raw).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_CONFIG).apply()
    }

    companion object {
        private const val PREFS_NAME = "request_config"
        private const val KEY_CONFIG = "config_v1"
    }
}
