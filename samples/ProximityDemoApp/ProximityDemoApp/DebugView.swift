import SwiftUI

struct DebugView: View {
    @State private var refreshToken = UUID()

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("SDK Local Storage")
                    .font(.headline)
                Spacer()
                Button("Refresh") {
                    refreshToken = UUID()
                }
            }
            SharedPreferencesView()
                .id(refreshToken)
        }
        .padding()
        .navigationTitle("Debug")
    }
}
