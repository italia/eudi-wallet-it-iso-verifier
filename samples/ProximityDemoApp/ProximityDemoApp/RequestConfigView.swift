import SwiftUI
import proximityverification

struct RequestConfigView: View {
    let catalog: DocumentTypeCatalog
    @ObservedObject var configStore: RequestConfigStore

    @State private var registeredConfigs: [CredentialConfiguration] = []
    @State private var showJsonSheet = false
    @State private var showUrlSheet = false
    @State private var alertMessage: String?
    @State private var showAlert = false

    var body: some View {
        List {
            catalogManagementSection
            portraitCorroborationSection
            requestBuilderSection
            summarySection
        }
        .navigationTitle("Request Config")
        .onAppear { refreshCatalog() }
        .alert("Result", isPresented: $showAlert, actions: { Button("OK", role: .cancel) {} }, message: {
            Text(alertMessage ?? "")
        })
        .sheet(isPresented: $showJsonSheet) {
            JsonInputSheet { json in
                showJsonSheet = false
                loadFromJson(json)
            }
        }
        .sheet(isPresented: $showUrlSheet) {
            UrlInputSheet { url in
                showUrlSheet = false
                loadFromUrl(url)
            }
        }
    }

    // MARK: - Section A: Catalog Management

    private var catalogManagementSection: some View {
        Section("Catalog Management") {
            HStack(spacing: 12) {
                Button("Load JSON") { showJsonSheet = true }
                    .buttonStyle(.bordered)
                Button("Load from URL") { showUrlSheet = true }
                    .buttonStyle(.bordered)
            }

            if registeredConfigs.isEmpty {
                Text("No document types registered")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(registeredConfigs, id: \.docType) { config in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(config.display.first?.name ?? config.docType)
                                .font(.body.weight(.semibold))
                            Text(config.docType)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("\(config.claims.count) claims")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(role: .destructive) {
                            let _ = catalog.unregister(docType: config.docType)
                            refreshCatalog()
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                }
            }
        }
    }

    // MARK: - Portrait corroboration preset

    /// Imposta in un tocco la richiesta a due `DocRequest` prescritta dal design (credenziale
    /// selezionata con tutti i claim + mDL con il solo `portrait`) e **dichiara l'intenzione**.
    ///
    /// Dichiararla, invece di inferirla ogni volta dalla forma della richiesta, è ciò che permette di
    /// accorgersi quando la forma non è più quella attesa: la sezione lo dice in rosso invece di
    /// tornare silenziosamente a "non dichiarata".
    private var portraitCorroborationSection: some View {
        Section("Portrait corroboration") {
            Text(
                "Requests the selected credential in full plus the mDL for its portrait only, "
                    + "for credentials that carry no photo of their own."
            )
            .font(.caption)
            .foregroundStyle(.secondary)

            let request = PortraitCorroboration.resolveRequest(
                config: configStore.config,
                registeredDocTypes: Set(registeredConfigs.map { $0.docType })
            )

            HStack {
                Button("Corroborate portrait") { applyCorroborationPreset() }
                    .buttonStyle(.bordered)
                Spacer()
                if !isNotRequested(request) {
                    Button("Clear") { configStore.config = PortraitCorroboration.clearIntent(config: configStore.config) }
                        .buttonStyle(.borderless)
                }
            }

            switch request {
            case .notRequested:
                Text("Not declared")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            case let .declared(shape):
                Text(
                    "Declared: \(shape.primaryDocTypes.joined(separator: ", ")) "
                        + "+ \(shape.corroboratingDocType) (portrait only)"
                )
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.accentColor)
            case let .declaredButUnrecognised(reason):
                Text("Declared, but the request no longer has the expected shape: \(reason)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.red)
            }
        }
    }

    private func isNotRequested(_ request: PortraitCorroboration.Request) -> Bool {
        if case .notRequested = request { return true }
        return false
    }

    private func applyCorroborationPreset() {
        switch PortraitCorroboration.applyPreset(
            config: configStore.config,
            registeredConfigs: registeredConfigs
        ) {
        case let .applied(config, shape):
            configStore.config = config
            alertMessage = "Corroborating \(shape.primaryDocTypes.joined(separator: ", ")) "
                + "with the mDL portrait"
            showAlert = true
        case let .notApplicable(reason):
            alertMessage = reason
            showAlert = true
        }
    }

    // MARK: - Section B: Request Builder

    private var requestBuilderSection: some View {
        Section("Request Builder") {
            ForEach(registeredConfigs, id: \.docType) { credConfig in
                docTypeCard(credConfig)
            }
        }
    }

    @ViewBuilder
    private func docTypeCard(_ credConfig: CredentialConfiguration) -> some View {
        let docType = credConfig.docType
        let displayName = credConfig.display.first?.name ?? docType
        let docSelection = binding(for: docType, credConfig: credConfig)

        DisclosureGroup {
            if docSelection.wrappedValue.enabled {
                quickActions(credConfig: credConfig, docType: docType)
                claimsList(credConfig: credConfig, docType: docType)
            }
        } label: {
            HStack {
                VStack(alignment: .leading) {
                    Text(displayName).font(.subheadline.weight(.semibold))
                    Text(docType).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Toggle("", isOn: docSelection.enabled)
                    .labelsHidden()
            }
        }
    }

    @ViewBuilder
    private func quickActions(credConfig: CredentialConfiguration, docType: String) -> some View {
        HStack(spacing: 12) {
            Button("All") { selectAll(credConfig: credConfig, docType: docType) }
                .buttonStyle(.bordered)
                .controlSize(.small)
            Button("Mandatory") { selectMandatory(credConfig: credConfig, docType: docType) }
                .buttonStyle(.bordered)
                .controlSize(.small)
            Button("Clear") { clearSelection(credConfig: credConfig, docType: docType) }
                .buttonStyle(.bordered)
                .controlSize(.small)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func claimsList(credConfig: CredentialConfiguration, docType: String) -> some View {
        let grouped = Dictionary(grouping: credConfig.claims, by: { $0.ns })
        ForEach(grouped.keys.sorted(), id: \.self) { namespace in
            Text(namespace)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.blue)
            if let claims = grouped[namespace] {
                ForEach(claims.indices, id: \.self) { index in
                    claimRow(claim: claims[index], docType: docType)
                }
            }
        }
    }

    @ViewBuilder
    private func claimRow(claim: ClaimDescriptor, docType: String) -> some View {
        let key = RequestConfig.claimKey(namespace: claim.ns, identifier: claim.identifier)
        let entryBinding = claimBinding(docType: docType, key: key)
        let displayName = claim.display.first?.name ?? claim.identifier

        HStack {
            Toggle(isOn: entryBinding.selected) {
                VStack(alignment: .leading) {
                    Text(displayName).font(.subheadline)
                    if claim.isMandatory {
                        Text("mandatory").font(.caption2).foregroundStyle(.blue)
                    }
                }
            }
            if entryBinding.wrappedValue.selected {
                VStack {
                    Text("Retain").font(.caption2)
                    Toggle("", isOn: entryBinding.intentToRetain)
                        .labelsHidden()
                        .toggleStyle(.switch)
                        .controlSize(.mini)
                }
            }
        }
    }

    // MARK: - Summary

    private var summarySection: some View {
        Section {
            let enabledCount = configStore.config.docTypeConfigs.filter { (docType, cfg) in
                cfg.enabled && registeredConfigs.contains(where: { $0.docType == docType })
            }.count
            let claimCount = configStore.config.docTypeConfigs
                .filter { (docType, cfg) in cfg.enabled && registeredConfigs.contains(where: { $0.docType == docType }) }
                .reduce(0) { acc, entry in acc + entry.value.claimSelections.filter { $0.value.selected }.count }
            Text("\(enabledCount) document(s), \(claimCount) total claims selected")
                .font(.subheadline.weight(.semibold))
        }
    }

    // MARK: - Actions

    private func refreshCatalog() {
        registeredConfigs = catalog.getRegisteredConfigurations()
        configStore.config = RequestConfig.mergeWithCatalog(
            registeredConfigs: registeredConfigs,
            existing: configStore.config
        )
    }

    private func loadFromJson(_ json: String) {
        do {
            try catalog.registerFromJson(jsonString: json)
            refreshCatalog()
            alertMessage = "Loaded successfully"
            showAlert = true
        } catch {
            alertMessage = "Error: \(error.localizedDescription)"
            showAlert = true
        }
    }

    private func loadFromUrl(_ url: String) {
        Task {
            do {
                try await catalog.registerFromIssuerMetadata(issuerUrl: url)
                await MainActor.run { refreshCatalog() }
                await MainActor.run {
                    alertMessage = "Loaded from issuer metadata"
                    showAlert = true
                }
            } catch {
                await MainActor.run {
                    alertMessage = "Error: \(error.localizedDescription)"
                    showAlert = true
                }
            }
        }
    }

    private func selectAll(credConfig: CredentialConfiguration, docType: String) {
        var doc = configStore.config.docTypeConfigs[docType] ?? RequestConfig.defaultForDocType(credConfig)
        for claim in credConfig.claims {
            let key = RequestConfig.claimKey(namespace: claim.ns, identifier: claim.identifier)
            var entry = doc.claimSelections[key] ?? ClaimSelectionEntry()
            entry.selected = true
            doc.claimSelections[key] = entry
        }
        configStore.config.docTypeConfigs[docType] = doc
    }

    private func selectMandatory(credConfig: CredentialConfiguration, docType: String) {
        var doc = configStore.config.docTypeConfigs[docType] ?? RequestConfig.defaultForDocType(credConfig)
        for claim in credConfig.claims {
            let key = RequestConfig.claimKey(namespace: claim.ns, identifier: claim.identifier)
            var entry = doc.claimSelections[key] ?? ClaimSelectionEntry()
            entry.selected = claim.isMandatory
            doc.claimSelections[key] = entry
        }
        configStore.config.docTypeConfigs[docType] = doc
    }

    private func clearSelection(credConfig: CredentialConfiguration, docType: String) {
        var doc = configStore.config.docTypeConfigs[docType] ?? RequestConfig.defaultForDocType(credConfig)
        for claim in credConfig.claims {
            let key = RequestConfig.claimKey(namespace: claim.ns, identifier: claim.identifier)
            var entry = doc.claimSelections[key] ?? ClaimSelectionEntry()
            entry.selected = false
            doc.claimSelections[key] = entry
        }
        configStore.config.docTypeConfigs[docType] = doc
    }

    // MARK: - Bindings

    private func binding(for docType: String, credConfig: CredentialConfiguration) -> Binding<DocTypeSelectionConfig> {
        Binding(
            get: {
                configStore.config.docTypeConfigs[docType]
                    ?? RequestConfig.defaultForDocType(credConfig)
            },
            set: { newValue in
                configStore.config.docTypeConfigs[docType] = newValue
            }
        )
    }

    private func claimBinding(docType: String, key: String) -> Binding<ClaimSelectionEntry> {
        Binding(
            get: {
                configStore.config.docTypeConfigs[docType]?.claimSelections[key]
                    ?? ClaimSelectionEntry()
            },
            set: { newValue in
                if configStore.config.docTypeConfigs[docType] == nil {
                    configStore.config.docTypeConfigs[docType] = DocTypeSelectionConfig()
                }
                configStore.config.docTypeConfigs[docType]?.claimSelections[key] = newValue
            }
        )
    }
}

// MARK: - Input Sheets

struct JsonInputSheet: View {
    let onSubmit: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var text: String = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Paste credential metadata JSON below:")
                    .font(.subheadline)
                TextEditor(text: $text)
                    .font(.system(.caption, design: .monospaced))
                    .border(Color.secondary.opacity(0.3))
                    .frame(minHeight: 200)
            }
            .padding()
            .navigationTitle("Load from JSON")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Load") { onSubmit(text) }
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

struct UrlInputSheet: View {
    let onSubmit: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var url: String = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Enter issuer base URL:")
                    .font(.subheadline)
                TextField("https://example.com", text: $url)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .keyboardType(.URL)
                Spacer()
            }
            .padding()
            .navigationTitle("Load from URL")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Load") { onSubmit(url) }
                        .disabled(url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
