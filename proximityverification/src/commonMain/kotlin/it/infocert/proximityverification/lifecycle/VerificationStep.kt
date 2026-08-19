package it.infocert.proximityverification.lifecycle

sealed class VerificationStep {
    // NFC-specific steps (occur before Starting for NFC flows)
    data class NfcScanningStarted(val message: String) : VerificationStep()
    data object NfcTagDetected : VerificationStep()
    data object NfcEngagementReading : VerificationStep()
    data object NfcEngagementCompleted : VerificationStep()

    /** Engagement data successfully parsed (QR decoded or NFC handover read). */
    data object EngagementParsed : VerificationStep()

    /**
     * Fired at the start of verification to inform the app which transport is active.
     * Emitted before [Connecting].
     *
     * @property transportType Human-readable label, e.g. `"BLE"` or `"NFC"`.
     */
    data class TransportSelected(val transportType: String) : VerificationStep()

    /**
     * QR engagement resolved to NFC data transfer — the user must bring the device
     * near the holder's device. The consumer should display a proximity prompt.
     *
     * @property message Suggested user-facing message (e.g. "Hold near holder's device").
     */
    data class NfcDataTransferReady(val message: String) : VerificationStep()

    // Standard verification steps
    data object Starting : VerificationStep()
    data object Connecting : VerificationStep()
    data object SendingRequest : VerificationStep()
    data object Authenticating : VerificationStep()
    data object Verifying : VerificationStep()
    data object Completed : VerificationStep()
    data object Failed : VerificationStep()
}