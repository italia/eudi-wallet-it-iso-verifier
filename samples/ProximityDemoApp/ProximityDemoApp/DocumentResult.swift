
import Foundation
import proximityverification

extension KotlinByteArray {
    /// Convert KotlinByteArray to Swift Data
    func toData() -> Data {
        var buffer = [UInt8]()
        for i in 0..<Int(self.size) {
            let byte: Int8 = self.get(index: Int32(i))
            buffer.append(UInt8(bitPattern: byte))
        }
        return Data(buffer)
    }
}

struct SessionResult {
    let engagementType: String
    let connectionMethod: String
    let requestSize: String
    let responseSize: String
    let deviceResponseStatus: String
    let timing: TimingInfo

    init(from sessionInfo: SessionInfo) {
        self.engagementType = String(describing: sessionInfo.engagementType)
        self.connectionMethod = String(describing: sessionInfo.connectionMethod)
        self.requestSize = SessionResult.stringOrDefault(sessionInfo.requestSize)
        self.responseSize = SessionResult.stringOrDefault(sessionInfo.responseSize)
        self.deviceResponseStatus = SessionResult.stringOrDefault(sessionInfo.deviceResponseStatus)
        self.timing = TimingInfo(from: sessionInfo.timing)
    }

    struct TimingInfo {
        let nfcTapToEngagement: String
        let engagementToRequest: String
        let requestToResponse: String
        let scanningTime: String

        init(from timing: SessionTiming) {
            self.nfcTapToEngagement = SessionResult.stringOrDefault(timing.nfcTapToEngagement)
            self.engagementToRequest = SessionResult.stringOrDefault(timing.engagementReceivedToRequestSent)
            self.requestToResponse = SessionResult.stringOrDefault(timing.requestSentToResponseReceived)
            self.scanningTime = SessionResult.stringOrDefault(timing.scanningTime)
        }
    }

    private static func stringOrDefault(_ value: Any?) -> String {
        guard let value = value else { return "N/A" }
        return String(describing: value)
    }
}

struct ProtocolFailureResult {
    let reason: String
    let message: String
}

struct DocumentResult: Identifiable {
    let id = UUID()
    let docType: String
    let trustPointName: String?
    let validity: ValidityInfo
    let checks: [CheckResult]
    let kvPairs: [KeyValue]

    init(from data: DocumentVerificationResult) {
        self.docType = data.docType
        self.trustPointName = data.issuerCertInfo?.trustPointName
        self.validity = ValidityInfo(from: data.validityInfo)
        self.checks = data.checks.map { CheckResult(from: $0) }
        self.kvPairs = data.data.map { KeyValue(from: $0) }
    }

    struct ValidityInfo {
        let validFrom: String
        let validUntil: String
        let signedAt: String
        let expectedUpdate: String?

        init(from validityInfo: DocumentValidityInfo) {
            self.validFrom = String(describing: validityInfo.validFrom)
            self.validUntil = String(describing: validityInfo.validUntil)
            self.signedAt = String(describing: validityInfo.signedAt)
            self.expectedUpdate = validityInfo.expectedUpdate.map { String(describing: $0) }
        }
    }

    struct CheckResult: Identifiable {
        let id = UUID()
        // Enum dell'SDK, non stringhe: rinominare o rimuovere un caso rompe la compilazione del
        // sample invece di far passare in silenzio un confronto che non matcha più.
        let type: VerificationCheckType
        let status: CheckStatus
        // Rendering leggibile dei metadata additivi del check (`CheckMetadata`), se presenti:
        // stato di revoca + freschezza della status list, esito CRL, finestra di validità MSO,
        // scadenza amministrativa. Si appoggia al toString() della data class Kotlin.
        let details: String?

        init(from check: VerificationCheck) {
            self.type = check.type
            self.status = check.status
            self.details = check.metadata.map { String(describing: $0) }
        }
    }

    struct KeyValue: Identifiable {
        let id = UUID()
        let key: String
        let textValue: String?
        let mimetype: DocumentValueMimeType
        let imageData: Data?

        init(from kv: DocumentKeyValuePair) {
            self.key = kv.key
            self.textValue = kv.textValue
            self.mimetype = kv.mimeType
            self.imageData = kv.imageData?.toData()
        }
    }
}
