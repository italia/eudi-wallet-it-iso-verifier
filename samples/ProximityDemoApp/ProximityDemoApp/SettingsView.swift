import SwiftUI

struct SettingsView: View {
    @ObservedObject var store: SampleSettingsStore

    var body: some View {
        Form {
            Section {
                Toggle("BLE (mdoc central client mode)", isOn: $store.settings.bleCentralClientMode)
                Toggle("BLE (mdoc peripheral server mode)", isOn: $store.settings.blePeripheralServerMode)
                Toggle("NFC Data Transfer", isOn: $store.settings.nfcEnabled)
            } header: {
                Text("NFC Negotiated Handover Transports")
            } footer: {
                Text("Controls which transports the reader advertises during NFC negotiated handover. Does not affect QR engagement.")
            }

            Section {
                Picker("Preferred Transport", selection: $store.settings.preferredTransport) {
                    Text("AUTO").tag("AUTO")
                    Text("BLE").tag("BLE")
                    Text("NFC").tag("NFC")
                }
            } header: {
                Text("Transport Selection")
            } footer: {
                Text("Priority when multiple transports are available (applies to both QR and NFC flows).")
            }

            Section("Transport Options") {
                Toggle("Use L2CAP if available", isOn: $store.settings.bleL2Cap)
                Toggle("Use L2CAP in engagement if available", isOn: $store.settings.bleL2CapInEngagement)
                Toggle("Keep connection open after first request", isOn: $store.settings.allowMultipleRequests)
            }

            Section("NFC Messages") {
                TextField("Scan Message", text: $store.settings.nfcScanMessage)
                TextField("Transfer Message", text: $store.settings.nfcTransferMessage)
            }

            Section {
                Button("Reset to Defaults", role: .destructive) {
                    store.resetToDefaults()
                }
            }
        }
        .navigationTitle("Settings")
    }
}
