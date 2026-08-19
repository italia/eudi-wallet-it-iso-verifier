import Foundation
import proximityverification

struct SampleSettings: Codable {
    var blePeripheralServerMode: Bool = true
    var bleCentralClientMode: Bool = true
    var nfcEnabled: Bool = true
    var preferredTransport: String = "AUTO"
    var bleL2Cap: Bool = false
    var bleL2CapInEngagement: Bool = true
    var allowMultipleRequests: Bool = false
    var nfcScanMessage: String = "Hold near credential holder's device"
    var nfcTransferMessage: String = "Transferring data, keep in reader's field"
}

final class SampleSettingsStore: ObservableObject {
    @Published var settings: SampleSettings {
        didSet { save() }
    }

    private let userDefaults: UserDefaults
    private let key: String = "sample_settings_v1"

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        self.settings = SampleSettingsStore.loadSettings(from: userDefaults, key: key) ?? SampleSettings()
    }

    func resetToDefaults() {
        settings = SampleSettings()
        save()
    }

    func buildVerificationSettings(requests: [VerificationRequest]) -> ProximityVerificationSettings {
        let transportPref: TransportPreference = {
            switch settings.preferredTransport {
            case "BLE": return .ble
            case "NFC": return .nfc
            default: return .auto_
            }
        }()
        let scanMessage = settings.nfcScanMessage.isEmpty
            ? "Hold near credential holder's device"
            : settings.nfcScanMessage
        let transferMessage = settings.nfcTransferMessage.isEmpty
            ? "Transferring data, keep in reader's field"
            : settings.nfcTransferMessage
        // Kotlin default parameters are not visible to Swift; use Builder so NFC max lengths
        // and timeouts match ProximityVerificationSettings defaults in commonMain.
        return ProximityVerificationSettings.Companion.shared.builder()
            .withRequests(value: requests)
            .withAllowMultipleRequests(value: settings.allowMultipleRequests)
            .withBleEnabled(value: settings.bleCentralClientMode || settings.blePeripheralServerMode)
            .withNfcDataTransferEnabled(value: settings.nfcEnabled)
            .withBleCentralClientMode(value: settings.bleCentralClientMode)
            .withBlePeripheralServerMode(value: settings.blePeripheralServerMode)
            .withBleUseL2CAP(value: settings.bleL2Cap)
            .withBleUseL2CAPInEngagement(value: settings.bleL2CapInEngagement)
            .withNfcScanMessage(value: scanMessage)
            .withNfcTransferMessage(value: transferMessage)
            .withPreferredTransport(value: transportPref)
            .build()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(settings) else { return }
        userDefaults.set(data, forKey: key)
    }

    private static func loadSettings(from defaults: UserDefaults, key: String) -> SampleSettings? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(SampleSettings.self, from: data)
    }
}

// MARK: - Request Configuration

struct ClaimSelectionEntry: Codable {
    var selected: Bool = false
    var intentToRetain: Bool = false
}

struct DocTypeSelectionConfig: Codable {
    var enabled: Bool = true
    var claimSelections: [String: ClaimSelectionEntry] = [:]
}

struct RequestConfig: Codable {
    var docTypeConfigs: [String: DocTypeSelectionConfig] = [:]
    /// L'operatore ha dichiarato di volere la corroborazione del ritratto (vedi
    /// `PortraitCorroboration`). È **intenzione dichiarata**, non inferita dalla forma della richiesta:
    /// se la selezione dei claim viene poi modificata a mano e la forma non è più quella attesa, lo si
    /// segnala invece di far sparire la diagnostica in silenzio.
    var corroborationRequested: Bool = false

    static func claimKey(namespace: String, identifier: String) -> String {
        return "\(namespace)::\(identifier)"
    }

    static func defaultForCatalog(_ catalog: DocumentTypeCatalog) -> RequestConfig {
        let configs = catalog.getRegisteredConfigurations()
        var map: [String: DocTypeSelectionConfig] = [:]
        for config in configs {
            map[config.docType] = defaultForDocType(config)
        }
        return RequestConfig(docTypeConfigs: map)
    }

    static func defaultForDocType(_ config: CredentialConfiguration) -> DocTypeSelectionConfig {
        var claims: [String: ClaimSelectionEntry] = [:]
        for claim in config.claims {
            let key = claimKey(namespace: claim.ns, identifier: claim.identifier)
            claims[key] = ClaimSelectionEntry(selected: false, intentToRetain: false)
        }
        return DocTypeSelectionConfig(enabled: false, claimSelections: claims)
    }

    static func mergeWithCatalog(
        registeredConfigs: [CredentialConfiguration],
        existing: RequestConfig
    ) -> RequestConfig {
        var merged: [String: DocTypeSelectionConfig] = [:]
        for credConfig in registeredConfigs {
            if let existingDoc = existing.docTypeConfigs[credConfig.docType],
               !existingDoc.claimSelections.isEmpty {
                let defaultClaims = defaultForDocType(credConfig).claimSelections
                let mergedClaims = defaultClaims.merging(existingDoc.claimSelections) { _, existing in existing }
                merged[credConfig.docType] = DocTypeSelectionConfig(
                    enabled: existingDoc.enabled,
                    claimSelections: mergedClaims
                )
            } else {
                merged[credConfig.docType] = defaultForDocType(credConfig)
            }
        }
        // Si parte da `existing`: un merge con il catalog non deve perdere l'intenzione dichiarata
        // dall'operatore (es. la corroborazione del ritratto).
        var result = existing
        result.docTypeConfigs = merged
        return result
    }
}

final class RequestConfigStore: ObservableObject {
    @Published var config: RequestConfig {
        didSet { save() }
    }

    private let userDefaults: UserDefaults
    private let key: String = "request_config_v1"

    init(userDefaults: UserDefaults = .standard, catalog: DocumentTypeCatalog) {
        self.userDefaults = userDefaults
        self.config = RequestConfigStore.loadConfig(from: userDefaults, key: key)
            ?? RequestConfig.defaultForCatalog(catalog)
    }

    func save() {
        guard let data = try? JSONEncoder().encode(config) else { return }
        userDefaults.set(data, forKey: key)
    }

    func reset(catalog: DocumentTypeCatalog) {
        config = RequestConfig.defaultForCatalog(catalog)
    }

    private static func loadConfig(from defaults: UserDefaults, key: String) -> RequestConfig? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(RequestConfig.self, from: data)
    }
}
