import Foundation
import proximityverification
import SwiftUI

class VerificationDelegate: NSObject, VerificationCallback {

    @Binding var status: String
    @Binding var currentStep: String
    @Binding var results: [DocumentResult]
    @Binding var sessionInfo: SessionResult?
    @Binding var protocolFailure: ProtocolFailureResult?

    /// Inventario di ciò che si è chiesto e di ciò che è tornato, così come lo riporta l'SDK: la
    /// policy di obbligatorietà del sample (`RequiredClaims`) è calcolata da qui.
    @Binding var requestOutcome: RequestOutcome?

    /// Invocato una volta quando i documenti sono arrivati, per gli esiti che il sample calcola da
    /// sé (es. la corroborazione del ritratto). Fuori dalla UI: un log dentro una `View` si
    /// ripeterebbe a ogni ridisegno.
    private let onDocumentsReceived: ([DocumentResult]) -> Void

    init(
        status: Binding<String>,
        currentStep: Binding<String>,
        results: Binding<[DocumentResult]>,
        sessionInfo: Binding<SessionResult?>,
        protocolFailure: Binding<ProtocolFailureResult?>,
        requestOutcome: Binding<RequestOutcome?>,
        onDocumentsReceived: @escaping ([DocumentResult]) -> Void = { _ in }
    ) {
        _status = status
        _currentStep = currentStep
        _results = results
        _sessionInfo = sessionInfo
        _protocolFailure = protocolFailure
        _requestOutcome = requestOutcome
        self.onDocumentsReceived = onDocumentsReceived
    }

    func onVerificationUpdate(step: VerificationStep) {
        DispatchQueue.main.async {
            self.currentStep = "\(step)"
            if step is VerificationStep.Starting {
                self.results = []
                self.sessionInfo = nil
                self.protocolFailure = nil
                self.requestOutcome = nil
            }
            switch step {
            // NFC-specific steps (occur before Starting for NFC flows)
            case let nfcStarted as VerificationStep.NfcScanningStarted:
                self.status = "NFC: \(nfcStarted.message)"
            case is VerificationStep.NfcTagDetected:
                self.status = "NFC: Tag detected, reading credential…"
            case is VerificationStep.NfcEngagementReading:
                self.status = "NFC: Reading engagement data…"
            case is VerificationStep.NfcEngagementCompleted:
                self.status = "NFC: Engagement completed, connecting via BLE…"
            case is VerificationStep.EngagementParsed:
                self.status = "Engagement parsed"
            case let transportSelected as VerificationStep.TransportSelected:
                self.status = "Transport: \(transportSelected.transportType)"
            case let nfcReady as VerificationStep.NfcDataTransferReady:
                self.status = "Bring device near holder: \(nfcReady.message)"
            
            // Standard verification steps
            case is VerificationStep.Starting:
                self.status = "Starting verification process…"
            case is VerificationStep.Connecting:
                self.status = "Connecting to mDL holder…"
            case is VerificationStep.SendingRequest:
                self.status = "Sending request…"
            case is VerificationStep.Authenticating:
                self.status = "Authenticating on mDL…"
            case is VerificationStep.Verifying:
                self.status = "Verifying response…"
            case is VerificationStep.Completed:
                self.status = "Verification completed"
            case is VerificationStep.Failed:
                self.status = "Verification failed"
            default:
                self.status = "Update: \(step)"
            }
        }
    }

    func onVerificationCompleted(outcome: VerificationOutcome) {
        DispatchQueue.main.async {
            if let documentReceived = outcome as? VerificationOutcome.DocumentReceived {
                self.status = "Success: \(documentReceived.documents.count) document(s)"
                self.protocolFailure = nil
                self.requestOutcome = documentReceived.requestOutcome
                self.sessionInfo = SessionResult(from: documentReceived.sessionInfo)
                let documents = documentReceived.documents.map { DocumentResult(from: $0) }
                self.results = documents
                self.onDocumentsReceived(documents)
                return
            }
            if let protocolFailed = outcome as? VerificationOutcome.ProtocolFailed {
                let reason = String(describing: protocolFailed.reason)
                let message = protocolFailed.message
                Logger.shared.debug(tag: "ProximityDemoApp", message: {
                    "[CALLBACK][FAILED] reason=\(reason) message=\(message)"
                })
                self.status = "Error: \(reason) - \(message)"
                self.sessionInfo = nil
                self.protocolFailure = ProtocolFailureResult(reason: reason, message: message)
                self.results = []
                self.requestOutcome = nil
                return
            }
            self.status = "Verification completed with unknown outcome"
            self.sessionInfo = nil
            self.protocolFailure = nil
            self.results = []
            self.requestOutcome = nil
        }
    }
}
