import SwiftUI
import proximityverification

struct SharedPreferencesView: View {
    @State private var preferences: [(key: String, value: String)] = []
    @State private var rotationAngle: Double = 0
    
    var body: some View {
        VStack(spacing: 8) {
            // Header with title and refresh button
            HStack {
                Text("Hardware Key Tags")
                    .font(.headline)
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
                
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.5)) {
                        rotationAngle += 360
                    }
                    loadPreferences()
                }) {
                    Image(systemName: "arrow.clockwise")
                        .foregroundColor(.blue)
                        .font(.system(size: 18))
                }
                .rotationEffect(.degrees(rotationAngle))
            }
            .padding(.horizontal)
            
            // Card view for preferences
            VStack(alignment: .leading, spacing: 0) {
                if preferences.isEmpty {
                    Text("No preferences stored yet")
                        .foregroundColor(.gray)
                        .font(.system(size: 12))
                        .padding()
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(preferences.indices, id: \.self) { index in
                                let pref = preferences[index]
                                HStack(alignment: .top, spacing: 4) {
                                    Text(pref.key)
                                        .font(.system(size: 12, design: .monospaced))
                                        .fontWeight(.bold)
                                        .foregroundColor(.blue)
                                    
                                    Text(": ")
                                        .font(.system(size: 12, design: .monospaced))
                                    
                                    Text(truncateValue(pref.value))
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(.primary)
                                        .lineLimit(nil)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .padding(12)
                    }
                    .frame(maxHeight: 140)
                }
            }
            .background(Color(.secondarySystemBackground))
            .cornerRadius(12)
            .shadow(radius: 2)
        }
        .onAppear {
            loadPreferences()
        }
    }
    
    private func loadPreferences() {
        let suiteName = Constants.shared.KEY_TAGS_FILE_NAME
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            print("⚠️ Could not load preferences: invalid suite name \(suiteName)")
            preferences = []
            return
        }

        // Load only the persistent domain for the suite (actual saved data)
        let dictionary = defaults.persistentDomain(forName: suiteName) ?? [:]
        var loadedPrefs: [(key: String, value: String)] = []

        for (key, value) in dictionary {
            let valueString = "\(value)"
            loadedPrefs.append((key: key, value: valueString))
            
            // Print IOS_DEVICE_CHECK_ATTESTATION_OBJ to console without truncation
            if key == "IOS_DEVICE_CHECK_ATTESTATION_OBJ" {
                print("\n========================================")
                print("Key: IOS_DEVICE_CHECK_ATTESTATION_OBJ")
                print("Value length: \(valueString.count) characters")
                print("========================================")

                // Print in chunks to avoid Xcode console truncation
                let chunkSize = 1000
                let chunks = stride(from: 0, to: valueString.count, by: chunkSize).map { startIndex -> String in
                    let start = valueString.index(valueString.startIndex, offsetBy: startIndex)
                    let end = valueString.index(start, offsetBy: min(chunkSize, valueString.count - startIndex))
                    return String(valueString[start..<end])
                }

                for (index, chunk) in chunks.enumerated() {
                    print("Chunk \(index + 1)/\(chunks.count): \(chunk)")
                }
                print("========================================\n")
            }
        }

        // Print all shared preferences keys for reference
        print("\n========================================")
        print("All Shared Preferences Keys (Suite: \(suiteName)):")
        print("========================================")
        for (key, value) in dictionary.sorted(by: { $0.key < $1.key }) {
            let valuePreview = "\(value)".prefix(50)
            print("[\(key)]: \(valuePreview)\(String("\(value)").count > 50 ? "..." : "")")
        }
        print("========================================\n")

        preferences = loadedPrefs
    }
    
    private func truncateValue(_ value: String) -> String {

        return value
    }
}

#Preview {
    SharedPreferencesView()
}

