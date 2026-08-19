import SwiftUI
import UIKit
import CoreBluetooth
import proximityverification

/// Tag dei log del sample per la corroborazione del ritratto.
///
/// Il verdetto lo emette il consumer, non l'SDK: `proximity-verification-ktm` logga solo i fatti della
/// sessione (docType e claim richiesti, elementi immagine per documento, docType negati dal wallet) e
/// non conosce questa feature.
let corroborationTag = "SampleCorroboration"

enum InitializationState: String {
    case uninitialized
    case initializing
    case initialized
    case error
}

struct PrefetchSummaryUi {
    let requestedDocTypes: [String]
    let resolvedIssuers: [String]
    let fetchedLists: [String]
    let failedLists: [(uri: String, reason: String)]
    let failedIssuers: [(issuer: String, reason: String)]
    let persistedAtEpochMillis: Int64
}

struct ContentView: View {
    @State private var status: String = "Status: idle"
    @State private var showScanner = false
    @State private var results: [DocumentResult] = []
    @State private var requestOutcome: RequestOutcome?
    @State private var sessionInfo: SessionResult?
    @State private var protocolFailure: ProtocolFailureResult?
    @State private var currentStep: String = "-"
    @State private var initializationState: InitializationState = .uninitialized
    @State private var offlineToggleState: Bool = false
    @State private var showStandaloneInitAlert: Bool = false
    @State private var standaloneIssuerUrl: String = ""
    @State private var offlineToggleInFlight: Bool = false
    @State private var prefetchSummary: PrefetchSummaryUi?
    @State private var prefetchErrorText: String?
    /// Cosa si è chiesto in materia di corroborazione del ritratto, risolto **al momento della
    /// richiesta**: il verdetto lato risposta va letto contro ciò che si è chiesto, non contro la
    /// configurazione corrente, che l'utente può avere cambiato nel frattempo.
    @State private var corroborationRequest: PortraitCorroboration.Request = .notRequested
    @State private var readerCert: ReaderCertificateUi?
    @State private var showReaderCertSheet = false
    // Held as State to keep the manager alive beyond onAppear's scope.
    // Creation triggers the iOS BLE permission prompt upfront.
    @State private var _bluetoothPermissionManager: CBCentralManager?
    @StateObject private var settingsStore = SampleSettingsStore()
    @StateObject private var requestConfigStore = RequestConfigStore(
        catalog: ProximityVerification.shared.documentTypeCatalog
    )

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let cert = readerCert {
                    ReaderCertificateBadge(cert: cert) { showReaderCertSheet = true }
                        .padding(.horizontal)
                }
                if offlineToggleState {
                    HStack(spacing: 12) {
                        Image(systemName: "icloud.and.arrow.down")
                            .imageScale(.large)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Offline verification mode active")
                                .font(.subheadline.weight(.semibold))
                            Text("Cached status lists are being used when the device is offline.")
                                .font(.caption)
                        }
                        Spacer()
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(Color.accentColor.opacity(0.4), lineWidth: 1)
                    )
                    .padding(.horizontal)
                }
                Text("Initialization: \(initializationState.rawValue)")
                    .font(.subheadline)
                Text("Current step: \(currentStep)")
                    .font(.subheadline)
                Text(status)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .padding(.top)

                VStack(spacing: 12) {
                    Button(initializationState == .initializing ? "Initializing..." : "Initialize") {
                        initializeAction()
                    }
                    .frame(maxWidth: .infinity)
                    .buttonStyle(.borderedProminent)
                    .disabled(initializationState == .initializing)

                    Button("Initialize Standalone") {
                        standaloneIssuerUrl = ""
                        showStandaloneInitAlert = true
                    }
                    .frame(maxWidth: .infinity)
                    .buttonStyle(.bordered)
                    .disabled(initializationState == .initializing)

                    HStack(spacing: 12) {
                        Button("QR Verification") {
                            scanOrRequestPermissions()
                        }
                        .frame(maxWidth: .infinity)
                        .buttonStyle(.bordered)
                        .disabled(initializationState != .initialized)

                        Button("NFC Verification") {
                            startNfcVerification()
                        }
                        .frame(maxWidth: .infinity)
                        .buttonStyle(.bordered)
                        .disabled(initializationState != .initialized)
                    }

                    HStack(spacing: 12) {
                        Button("Close Library") {
                            closeLibrary()
                        }
                        .frame(maxWidth: .infinity)
                        .buttonStyle(.bordered)
                        .disabled(initializationState != .initialized)

                        Button("Clear Storage") {
                            clearStorage()
                        }
                        .frame(maxWidth: .infinity)
                        .buttonStyle(.bordered)
                    }
                }
                .padding(.horizontal)

                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Offline status list verification")
                            .font(.subheadline.weight(.medium))
                        Text(toggleSubtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    if offlineToggleInFlight {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Toggle("", isOn: Binding(
                            get: { offlineToggleState },
                            set: { newValue in onOfflineToggle(newValue) }
                        ))
                        .labelsHidden()
                        .disabled(initializationState != .initialized || offlineToggleInFlight)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)

                if let prefetchErrorText = prefetchErrorText {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Offline Prefetch Error")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(prefetchErrorText)
                            .font(.subheadline)
                            .foregroundStyle(.red)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .padding(.horizontal)
                }

                if let summary = prefetchSummary {
                    VStack(alignment: .leading, spacing: 12) {
                        resultSectionCard(title: "Offline Status List Prefetch") {
                            resultRow(
                                label: "Requested docTypes",
                                value: summary.requestedDocTypes.isEmpty ? "(none)" : summary.requestedDocTypes.joined(separator: ", ")
                            )
                            resultRow(label: "Resolved issuers", value: "\(summary.resolvedIssuers.count)")
                            resultRow(label: "Lists fetched", value: "\(summary.fetchedLists.count)")
                            resultRow(
                                label: "Lists failed",
                                value: "\(summary.failedLists.count)",
                                valueColor: summary.failedLists.isEmpty ? .primary : .red
                            )
                            resultRow(
                                label: "Issuers failed",
                                value: "\(summary.failedIssuers.count)",
                                valueColor: summary.failedIssuers.isEmpty ? .primary : .red
                            )
                            resultRow(label: "Persisted at (epoch ms)", value: "\(summary.persistedAtEpochMillis)")
                        }
                        if !summary.failedLists.isEmpty {
                            resultSectionCard(title: "Failed lists") {
                                ForEach(Array(summary.failedLists.enumerated()), id: \.offset) { _, entry in
                                    resultRow(label: entry.uri, value: entry.reason, valueColor: .red)
                                }
                            }
                        }
                        if !summary.failedIssuers.isEmpty {
                            resultSectionCard(title: "Failed issuers") {
                                ForEach(Array(summary.failedIssuers.enumerated()), id: \.offset) { _, entry in
                                    resultRow(label: entry.issuer, value: entry.reason, valueColor: .red)
                                }
                            }
                        }
                    }
                    .padding(14)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
                    )
                    .padding(.horizontal)
                }

                let hasOutcome = protocolFailure != nil || sessionInfo != nil || !results.isEmpty
                if hasOutcome {
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            Text("Verification Result")
                                .font(.headline)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            if let failure = protocolFailure {
                                VStack(alignment: .leading, spacing: 12) {
                                    resultSectionCard(title: "Protocol Failure") {
                                        resultRow(label: "Reason", value: failure.reason, valueColor: .red)
                                        resultRow(label: "Message", value: failure.message)
                                    }
                                }
                                .padding(14)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
                                )
                            }
                            if let session = sessionInfo {
                                VStack(alignment: .leading, spacing: 12) {
                                    resultSectionCard(title: "Session Info") {
                                        resultRow(label: "Engagement Type", value: session.engagementType)
                                        resultRow(label: "Connection Method", value: session.connectionMethod)
                                        resultRow(label: "Request Size", value: session.requestSize)
                                        resultRow(label: "Response Size", value: session.responseSize)
                                        resultRow(label: "Device Response Status", value: session.deviceResponseStatus)
                                    }
                                    resultSectionCard(title: "Timing") {
                                        resultRow(label: "NFC Tap -> Engagement", value: session.timing.nfcTapToEngagement)
                                        resultRow(label: "Engagement -> Request", value: session.timing.engagementToRequest)
                                        resultRow(label: "Request -> Response", value: session.timing.requestToResponse)
                                        resultRow(label: "Scanning Time", value: session.timing.scanningTime)
                                    }
                                }
                                .padding(14)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
                                )
                            }
                            portraitCorroborationCard
                            requiredClaimsCard
                            ForEach(Array(results.enumerated()), id: \.element.id) { index, doc in
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Document #\(index + 1)")
                                        .font(.headline)
                                    resultSectionCard(title: "Identity") {
                                        resultRow(label: "Document Type", value: doc.docType)
                                    }
                                    resultSectionCard(title: "Issuer Certificate Info") {
                                        resultRow(
                                            label: "Trust Point",
                                            value: doc.trustPointName ?? "Not available"
                                        )
                                    }
                                    resultSectionCard(title: "Validity Info") {
                                        resultRow(label: "Valid From", value: doc.validity.validFrom)
                                        resultRow(label: "Valid Until", value: doc.validity.validUntil)
                                        resultRow(label: "Signed At", value: doc.validity.signedAt)
                                        resultRow(
                                            label: "Expected Update",
                                            value: doc.validity.expectedUpdate ?? "Not set"
                                        )
                                    }
                                    resultSectionCard(title: "Verification Checks") {
                                        if doc.checks.isEmpty {
                                            Text("No checks available")
                                                .font(.footnote)
                                                .foregroundStyle(.secondary)
                                        } else {
                                            ForEach(doc.checks) { check in
                                                VStack(alignment: .leading, spacing: 2) {
                                                    HStack {
                                                        Text(check.type.name)
                                                            .font(.subheadline)
                                                        Spacer()
                                                        Text(check.status.name)
                                                            .font(.caption.weight(.semibold))
                                                            .padding(.horizontal, 9)
                                                            .padding(.vertical, 4)
                                                            .background(checkColor(for: check.status).opacity(0.16))
                                                            .foregroundStyle(checkColor(for: check.status))
                                                            .clipShape(Capsule())
                                                    }
                                                    if let details = check.details {
                                                        Text(details)
                                                            .font(.caption2)
                                                            .foregroundStyle(.secondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    resultSectionCard(title: "Document Data") {
                                        ForEach(doc.kvPairs) { kv in
                                            if kv.mimetype == DocumentValueMimeType.image,
                                               let data = kv.imageData,
                                               let image = UIImage(data: data) {
                                                VStack(alignment: .leading, spacing: 6) {
                                                    Text(kv.key)
                                                        .font(.subheadline.weight(.semibold))
                                                    Image(uiImage: image)
                                                        .resizable()
                                                        .aspectRatio(contentMode: .fit)
                                                        .frame(maxWidth: .infinity)
                                                        .frame(height: 180)
                                                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                                }
                                            } else {
                                                resultRow(label: kv.key, value: kv.textValue ?? "")
                                            }
                                        }
                                    }
                                }
                                .padding(14)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Color(.separator).opacity(0.35), lineWidth: 1)
                                )
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                Spacer()
            }
            .padding()
            .navigationTitle("Sample mDL Reader")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    NavigationLink("Request Config") {
                        RequestConfigView(
                            catalog: ProximityVerification.shared.documentTypeCatalog,
                            configStore: requestConfigStore
                        )
                    }
                    NavigationLink("Settings") {
                        SettingsView(store: settingsStore)
                    }
                    NavigationLink("Debug") {
                        DebugView()
                    }
                }
            }
        }
        .sheet(isPresented: $showScanner) {
            QRScannerView { scanned in
                showScanner = false
                handleScan(b64url: scanned)
            }
        }
        .sheet(isPresented: $showReaderCertSheet) {
            if let cert = readerCert {
                ReaderCertificateSheet(cert: cert)
            }
        }
        .alert("Initialize Standalone", isPresented: $showStandaloneInitAlert) {
            TextField("Issuer metadata URL (optional)", text: $standaloneIssuerUrl)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
            Button("Initialize") {
                initializeStandalone(issuerUrl: standaloneIssuerUrl.isEmpty ? nil : standaloneIssuerUrl)
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("Optionally provide an issuer metadata URL. Leave empty to use the built-in mDL catalog.")
        }
        .onAppear {
            // Trigger iOS BLE permission prompt upfront.
            // CBCentralManager instantiation is the only way to request BLE permission on iOS.
            if _bluetoothPermissionManager == nil {
                _bluetoothPermissionManager = CBCentralManager()
            }
        }
    }

    private func clearStorage() {
        Task {
            do {
                try await ProximityVerification.shared.clearLibraryLocalStorage()
                let suiteName = Constants.shared.KEY_TAGS_FILE_NAME
                if let defaults = UserDefaults(suiteName: suiteName) {
                    defaults.dictionaryRepresentation().keys.forEach { defaults.removeObject(forKey: $0) }
                    defaults.synchronize()
                }
                let refreshed = (try? await ProximityVerification.shared.isStatusListsForOfflineUseEnabled().boolValue) ?? false
                offlineToggleState = refreshed
                prefetchSummary = nil
                prefetchErrorText = nil
                readerCert = nil
                status = "Storage cleared"
            } catch {
                status = "Error clearing storage: \(error.localizedDescription)"
            }
        }
    }

    private func refreshReaderCertificate() {
        Task {
            let info = try? await ProximityVerification.shared.getReaderCertificate()
            readerCert = info.map { ReaderCertificateUi.map($0) }
        }
    }

    private func scanOrRequestPermissions() {
        showScanner = true
    }

    private func closeLibrary() {
        ProximityVerification.shared.close()
        initializationState = .uninitialized
        status = "Library closed - active flow cancelled"
        currentStep = "-"
        results = []
        requestOutcome = nil
        sessionInfo = nil
        protocolFailure = nil
        offlineToggleState = false
        offlineToggleInFlight = false
        prefetchSummary = nil
        prefetchErrorText = nil
        readerCert = nil
    }

    private func onOfflineToggle(_ newValue: Bool) {
        guard initializationState == .initialized else { return }
        if offlineToggleInFlight { return }
        if newValue {
            enableOfflineStatusLists()
        } else {
            disableOfflineStatusLists()
        }
    }

    private func enableOfflineStatusLists() {
        offlineToggleInFlight = true
        prefetchSummary = nil
        prefetchErrorText = nil
        status = "Enabling offline mode..."

        Task {
            do {
                let summary = try await ProximityVerification.shared
                    .enableStatusListsForOfflineUse(docTypes: [])
                let mapped = mapPrefetchSummary(summary)
                offlineToggleState = true
                offlineToggleInFlight = false
                prefetchSummary = mapped
                status = "Offline mode enabled: \(mapped.fetchedLists.count) list(s) cached, " +
                    "\(mapped.failedLists.count) failed list(s), " +
                    "\(mapped.failedIssuers.count) failed issuer(s)"
            } catch {
                let refreshed = (try? await ProximityVerification.shared.isStatusListsForOfflineUseEnabled().boolValue) ?? false
                offlineToggleInFlight = false
                offlineToggleState = refreshed
                if let pve = kotlinPve(from: error) {
                    prefetchErrorText = "\(pve.code.code): \(pve.message ?? "")"
                    status = "Offline enable error: \(pve.code.code)"
                } else {
                    prefetchErrorText = error.localizedDescription
                    status = "Offline enable error: \(error.localizedDescription)"
                }
            }
        }
    }

    private func disableOfflineStatusLists() {
        offlineToggleInFlight = true
        prefetchErrorText = nil

        Task {
            do {
                try await ProximityVerification.shared.disableStatusListsForOfflineUse()
                offlineToggleState = false
                offlineToggleInFlight = false
                prefetchSummary = nil
                status = "Offline mode disabled"
            } catch {
                offlineToggleInFlight = false
                status = "Error disabling offline mode: \(error.localizedDescription)"
            }
        }
    }

    private func mapPrefetchSummary(_ summary: StatusListPrefetchSummary) -> PrefetchSummaryUi {
        let failedLists: [(uri: String, reason: String)] = summary.failedLists
            .map { (key, value) in (uri: key, reason: value.name) }
            .sorted { $0.uri < $1.uri }
        let failedIssuers: [(issuer: String, reason: String)] = summary.failedIssuers
            .map { (key, value) in (issuer: key, reason: value.name) }
            .sorted { $0.issuer < $1.issuer }
        return PrefetchSummaryUi(
            requestedDocTypes: summary.requestedDocTypes,
            resolvedIssuers: summary.resolvedIssuers,
            fetchedLists: summary.fetchedLists,
            failedLists: failedLists,
            failedIssuers: failedIssuers,
            persistedAtEpochMillis: summary.persistedAtEpochMillis
        )
    }

    private var toggleSubtitle: String {
        if offlineToggleInFlight {
            return "Updating…"
        }
        return offlineToggleState
            ? "Active — cached lists are used when offline"
            : "Inactive — verification only succeeds when online"
    }

    private func buildVerificationSettings() -> ProximityVerificationSettings? {
        let catalog = ProximityVerification.shared.documentTypeCatalog
        let requestBuilder = VerificationRequestBuilder(catalog: catalog)
        var requests: [VerificationRequest] = []
        for (docType, docConfig) in requestConfigStore.config.docTypeConfigs {
            guard docConfig.enabled, catalog.isRegistered(docType: docType) else { continue }
            let selectedClaims = docConfig.claimSelections.filter { $0.value.selected }
            guard !selectedClaims.isEmpty else { continue }
            do {
                let scope = try requestBuilder.forDocType(docType: docType)
                for (key, entry) in selectedClaims {
                    let parts = key.split(separator: "::")
                    guard parts.count == 2 else { continue }
                    scope.requestClaim(
                        namespace: String(parts[0]),
                        identifier: String(parts[1]),
                        intentToRetain: entry.intentToRetain
                    )
                }
                let req = try scope.build()
                requests.append(req)
            } catch {
                continue
            }
        }
        guard !requests.isEmpty else {
            status = "No documents or claims selected. Configure in Request Config."
            return nil
        }
        corroborationRequest = PortraitCorroboration.resolveRequest(
            config: requestConfigStore.config,
            registeredDocTypes: Set(catalog.getRegisteredConfigurations().map { $0.docType })
        )
        // La diagnostica non deve poter sparire in silenzio: se la corroborazione era dichiarata ma la
        // richiesta non ha più la forma attesa, lo si dice subito.
        switch corroborationRequest {
        case let .declaredButUnrecognised(reason):
            print("[\(corroborationTag)] Request shape broken: \(reason)")
        case let .declared(shape):
            print(
                "[\(corroborationTag)] Requested: primary=\(shape.primaryDocTypes.joined(separator: ", ")), "
                    + "corroborating=\(shape.corroboratingDocType) (portrait only)"
            )
        case .notRequested:
            break
        }
        return settingsStore.buildVerificationSettings(requests: requests)
    }

    private func initializeAction() {
        LoggerConfig().delegateLogger = MyLoggerDelegate()
        Logger.shared.currentLevel = .trace
        initializationState = .initializing
        status = "Initializing..."
        results = []
        requestOutcome = nil
        sessionInfo = nil
        protocolFailure = nil

        Task {
            do {
                let deviceInfo = DeviceInfo(
                    platform: "ios",
                    appVersion: "4.4.1",
                    buildVersion: "190",
                    lang: "it"
                )
                let providerInfo = RelyingPartyProviderInfo(
                    baseUrl: "https://pre.api.gateway.ipzs.it/t/itwallet.api/verifier-proxy/1.0/v1",
                    csrParam: CSRParam(
                        commonName: "Valida Documenti",
                        organization: "IPZS - Istituto Poligrafico e Zecca dello Stato",
                        organizationalUnit: "IT-Wallet Relying Party",
                        country: "IT"
                    ),
                    deviceInfo: deviceInfo,
                    federationEntityUrl: "https://pre.verifier.wallet.ipzs.it",
                    trustAnchors: [Constants.shared.SS_IOT_ECDSA_ROOT, Constants.shared.PRE_ROOT],
                    cloudProjectNumber: nil
                )
                let params = RelyingPartyInstanceParams(providerInfo: providerInfo, allowedIssuerIds: [])
                _ = try await ProximityVerification.shared.initializeRelyingPartyInstance(params: params)
                status = "Initialized - Token acquired"
                initializationState = .initialized
                let isOn = (try? await ProximityVerification.shared.isStatusListsForOfflineUseEnabled().boolValue) ?? false
                offlineToggleState = isOn
                refreshReaderCertificate()
            } catch {
                status = "Initialization error: \(error.localizedDescription)"
                initializationState = .error
            }
        }
    }

    private func initializeStandalone(issuerUrl: String?) {
        initializationState = .initializing
        status = "Initializing standalone..."
        results = []
        requestOutcome = nil
        sessionInfo = nil
        protocolFailure = nil

        Task {
            do {
                let issuerMeta: StandaloneIssuerMetadata? = issuerUrl.flatMap {
                    $0.isEmpty ? nil : StandaloneIssuerMetadata.FromUrl(url: $0)
                }
                let params = StandaloneRelyingPartyParams(
                    trustAnchors: nil,
                    issuerMetadata: issuerMeta
                )
                try await ProximityVerification.shared.initializeStandalone(params: params)
                status = "Initialized (standalone mode — self-signed cert, no backend)"
                initializationState = .initialized
                offlineToggleState = false
                refreshReaderCertificate()
            } catch {
                status = "Standalone init error: \(error.localizedDescription)"
                initializationState = .error
            }
        }
    }

    private func handleScan(b64url: String) {
        results.removeAll()
        requestOutcome = nil
        sessionInfo = nil
        protocolFailure = nil
        currentStep = "-"
        guard let settings = buildVerificationSettings() else { return }
        let callback = VerificationDelegate(
            status: $status,
            currentStep: $currentStep,
            results: $results,
            sessionInfo: $sessionInfo,
            protocolFailure: $protocolFailure,
            requestOutcome: $requestOutcome,
            onDocumentsReceived: { documents in logCorroborationVerdict(documents: documents) }
        )
        do {
            try ProximityVerification.shared.startVerification(
                encodedDeviceEngagement: b64url,
                settings: settings,
                callback: callback,
                platformContext: nil
            )
            status = "Verification in progress..."
        } catch {
            status = "Verification failed: \(error.localizedDescription)"
            if let pve = kotlinPve(from: error) {
                handleNfcError(pve)
            }
        }
    }

    private func startNfcVerification() {
        results.removeAll()
        requestOutcome = nil
        sessionInfo = nil
        protocolFailure = nil
        currentStep = "-"
        guard let settings = buildVerificationSettings() else { return }
        let callback = VerificationDelegate(
            status: $status,
            currentStep: $currentStep,
            results: $results,
            sessionInfo: $sessionInfo,
            protocolFailure: $protocolFailure,
            requestOutcome: $requestOutcome,
            onDocumentsReceived: { documents in logCorroborationVerdict(documents: documents) }
        )
        ProximityVerification.shared.startVerificationWithNfc(
            settings: settings,
            callback: callback,
            activity: nil
        )
        status = "Ready to scan NFC..."
    }

    private func handleNfcError(_ error: ProximityVerificationException) {
        switch error.code.code {
        case "PVE24":
            showAlert(title: "NFC Not Available", message: "This device doesn't support NFC.")
            status = "NFC not available"
        case "PVE25":
            showSettingsAlertForNfc()
            status = "NFC disabled"
        case "PVE26":
            showAlert(title: "NFC Scan Failed", message: "Please try again and hold devices close together.")
            status = "NFC scan failed"
        case "PVE27":
            showAlert(title: "Connection Lost", message: "NFC connection was interrupted. Please try again.")
            status = "NFC connection lost"
        case "PVE28":
            showAlert(title: "Invalid Data", message: "Invalid NFC data received.")
            status = "Invalid NFC data"
        case "PVE29":
            showAlert(title: "NFC Scan Cancelled", message: "The NFC sheet was closed by the user.")
            status = "NFC scan cancelled by user"
        default:
            showAlert(title: "Error", message: error.message ?? "Unknown error occurred")
            status = "Error: \(error.code.code)"
        }
    }

    private func showAlert(title: String, message: String) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let root = scene.windows.first?.rootViewController {
                root.present(alert, animated: true)
            }
        }
    }

    private func showSettingsAlertForNfc() {
        DispatchQueue.main.async {
            let alert = UIAlertController(
                title: "NFC Disabled",
                message: "Enable NFC-related permissions in iOS Settings if needed.",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
            alert.addAction(UIAlertAction(title: "Open Settings", style: .default) { _ in
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            })
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let root = scene.windows.first?.rootViewController {
                root.present(alert, animated: true)
            }
        }
    }

    private func kotlinPve(from error: Error) -> ProximityVerificationException? {
        let nsError = error as NSError
        return nsError.userInfo["KotlinException"] as? ProximityVerificationException
    }

    /// Esito della corroborazione del ritratto, ricalcolato in-app dai documenti ricevuti: è lo stesso
    /// verdetto che l'SDK scrive nei log su `VerificationSession:RESP`, qui reso visibile.
    ///
    /// Il sample mostra la foto anche quando un check di Tier 0 del documento corroborante è fallito —
    /// è uno strumento di diagnostica — ma lo dichiara: in produzione la policy dell'app non la
    /// mostrerebbe.
    @ViewBuilder
    private var portraitCorroborationCard: some View {
        if !results.isEmpty {
            let verdict = PortraitCorroboration.evaluate(request: corroborationRequest, documents: results)
            switch verdict {
            case .notRequested:
                EmptyView()

            case let .notSatisfied(_, reason):
                resultSectionCard(title: "Portrait Corroboration") {
                    resultRow(label: "Outcome", value: "NOT SATISFIED", valueColor: .orange)
                    resultRow(label: "Reason", value: reason)
                    Text(
                        "The requirement says a missing photo must not block the verification: "
                            + "the primary credential below is unaffected."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

            case let .broken(reason):
                resultSectionCard(title: "Portrait Corroboration") {
                    resultRow(label: "Outcome", value: "BROKEN REQUEST", valueColor: .red)
                    resultRow(label: "Reason", value: reason)
                    Text(
                        "Corroboration was declared in Request Config but the request did not have "
                            + "the expected shape. Re-apply the preset, or clear the declaration."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

            case let .satisfied(docType, failedTier0Checks):
                resultSectionCard(title: "Portrait Corroboration") {
                    resultRow(label: "Outcome", value: "SATISFIED", valueColor: .green)
                    resultRow(label: "Portrait from", value: docType)
                    if !failedTier0Checks.isEmpty {
                        resultRow(
                            label: "Production would hide this photo",
                            value: failedTier0Checks.map { $0.name }.joined(separator: ", ") + " FAILED",
                            valueColor: .red
                        )
                    }
                    if let document = results.first(where: { $0.docType == docType }),
                       let portrait = PortraitCorroboration.portrait(of: document),
                       let image = UIImage(data: portrait) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 220)
                    }
                }
            }
        }
    }

    /// Esito della policy di obbligatorietà, ricalcolato in-app dal `RequestOutcome`: è lo stesso
    /// inventario che l'SDK scrive nei log su `VerificationSession:RESP`, qui reso visibile.
    @ViewBuilder
    private var requiredClaimsCard: some View {
        if let outcome = requestOutcome {
            switch RequiredClaims.evaluate(outcome: outcome) {
            case .complete:
                resultSectionCard(title: "Required Claims") {
                    resultRow(label: "Outcome", value: "COMPLETE", valueColor: .green)
                }

            case let .incomplete(missingDocuments, missingClaims):
                resultSectionCard(title: "Required Claims") {
                    resultRow(label: "Outcome", value: "INCOMPLETE", valueColor: .orange)
                    ForEach(missingDocuments) { document in
                        resultRow(
                            label: "Missing document",
                            value: "\(document.docType) — \(document.status.name)"
                                + (document.errorCode.map { " (error \($0))" } ?? "")
                        )
                    }
                    ForEach(missingClaims) { claim in
                        resultRow(
                            label: "Missing claim",
                            value: "\(claim.namespace)/\(claim.identifier) — \(claim.status.name)"
                                + (claim.errorCode.map { " (error \($0))" } ?? "")
                        )
                    }
                    Text(
                        "The sample requires every claim it asked for. WITHHELD means the wallet "
                            + "declared it refused the claim, ABSENT means it just did not send it — "
                            + "a distinction taken from unsigned data, shown for diagnostics only."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private func resultSectionCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
            content()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private func resultRow(label: String, value: String, valueColor: Color = .primary) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(label)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value)
                .font(.subheadline)
                .foregroundStyle(valueColor)
                .multilineTextAlignment(.trailing)
        }
    }

    private func checkColor(for status: CheckStatus) -> Color {
        switch status {
        case CheckStatus.passed:
            return .green
        case CheckStatus.failed:
            return .red
        default:
            return .orange
        }
    }

    /// Scrive il verdetto sulla corroborazione nei log **del sample**: la feature è nostra, non
    /// dell'SDK, e i log dell'SDK riportano solo i fatti della sessione.
    private func logCorroborationVerdict(documents: [DocumentResult]) {
        switch PortraitCorroboration.evaluate(request: corroborationRequest, documents: documents) {
        case .notRequested:
            break
        case let .satisfied(docType, failedTier0Checks):
            print("[\(corroborationTag)] SATISFIED: portrait from \(docType)")
            if !failedTier0Checks.isEmpty {
                let names = failedTier0Checks.map { $0.name }.joined(separator: ", ")
                print("[\(corroborationTag)] Production would hide this photo: \(names) FAILED")
            }
        case let .notSatisfied(_, reason):
            print("[\(corroborationTag)] NOT SATISFIED: \(reason)")
        case let .broken(reason):
            print("[\(corroborationTag)] BROKEN: \(reason)")
        }
    }
}

