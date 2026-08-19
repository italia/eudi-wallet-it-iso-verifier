import SwiftUI
import UIKit
import proximityverification

/// Swift copy of the reader certificate fields (mirrors the PrefetchSummaryUi pattern),
/// with Instant/nullable already converted to string at the KMP boundary.
struct ReaderCertificateUi {
    let subjectCommonName: String?
    let subjectDn: String
    let issuerCommonName: String?
    let issuerDn: String
    let notBefore: String
    let notAfter: String
    let isExpired: Bool
    let serialNumber: String
    let sha256Fingerprint: String
    let pem: String

    static func map(_ info: ReaderCertificateInfo) -> ReaderCertificateUi {
        ReaderCertificateUi(
            subjectCommonName: info.subjectCommonName,
            subjectDn: info.subjectDn,
            issuerCommonName: info.issuerCommonName,
            issuerDn: info.issuerDn,
            notBefore: String(describing: info.notBefore),
            notAfter: String(describing: info.notAfter),
            isExpired: info.isExpired,
            serialNumber: info.serialNumber,
            sha256Fingerprint: info.sha256Fingerprint,
            pem: info.pem
        )
    }
}

struct ReaderCertificateBadge: View {
    let cert: ReaderCertificateUi
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: cert.isExpired ? "exclamationmark.shield" : "checkmark.shield")
                    .foregroundStyle(cert.isExpired ? .orange : .green)
                VStack(alignment: .leading, spacing: 2) {
                    Text(cert.isExpired ? "Reader certificate expired" : "Reader certificate active")
                        .font(.subheadline.weight(.semibold))
                    Text("\(cert.subjectCommonName ?? cert.subjectDn) · tap for details")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.tertiarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke((cert.isExpired ? Color.orange : Color.green).opacity(0.4), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

struct ReaderCertificateSheet: View {
    let cert: ReaderCertificateUi
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    row("Subject", cert.subjectDn)
                    row("Issuer", cert.issuerDn)
                    row("Valid from", cert.notBefore)
                    row("Valid until", cert.notAfter)
                    row("Status", cert.isExpired ? "Expired" : "Valid")
                    row("Serial", cert.serialNumber)
                    row("SHA-256", cert.sha256Fingerprint)
                    Text("PEM")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(cert.pem)
                        .font(.system(.caption, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    HStack(spacing: 12) {
                        Button("Copy PEM") { UIPasteboard.general.string = cert.pem }
                            .buttonStyle(.bordered)
                        Button("Copy fingerprint") { UIPasteboard.general.string = cert.sha256Fingerprint }
                            .buttonStyle(.bordered)
                    }
                }
                .padding()
            }
            .navigationTitle("Reader certificate")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private func row(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            Text(value).font(.subheadline)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
