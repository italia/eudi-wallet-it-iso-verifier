package it.infocert.proximityverification.core.models.initialization

import it.infocert.proximityverification.VerificationRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Central configuration for a single verification transaction.
 *
 * Merges what-to-request, transport preferences, and NFC engagement
 * parameters into a single immutable value object.
 *
 * Example:
 * ```
 * val settings = ProximityVerificationSettings(
 *     requests = listOf(verificationRequest),
 *     preferredTransport = TransportPreference.AUTO,
 * )
 * ProximityVerification.startVerification(qrString, settings, callback)
 * ```
 *
 * @property requests Document verification requests built from the catalog.
 * @property allowMultipleRequests Whether to allow requesting multiple document types at once.
 * @property bleEnabled Whether BLE transport is enabled at all.
 * @property nfcDataTransferEnabled Whether NFC data transfer is enabled.
 * @property bleCentralClientMode Enable BLE Central Client Mode.
 * @property blePeripheralServerMode Enable BLE Peripheral Server Mode.
 * @property bleUseL2CAP Enable BLE L2CAP for data transfer.
 * @property nfcScanMessage User-facing message to display during NFC tag scan.
 * @property bleUseL2CAPInEngagement Enable BLE L2CAP inside engagement advertisement.
 * @property nfcCommandDataFieldMaxLength NFC command data field max length.
 * @property nfcResponseDataFieldMaxLength NFC response data field max length.
 * @property preferredTransport Preferred transport protocol.
 * @property transportTimeout Timeout for transport connection and request sending.
 * @property responseTimeout Timeout for waiting for the holder's device response.
 * @property nfcTransferMessage User-facing message to display during NFC transfer.
 */
data class ProximityVerificationSettings(
    val requests: List<VerificationRequest>,
    val allowMultipleRequests: Boolean = false,
    val bleEnabled: Boolean = true,
    val nfcDataTransferEnabled: Boolean = true,
    val bleCentralClientMode: Boolean = true,
    val blePeripheralServerMode: Boolean = true,
    val bleUseL2CAP: Boolean = false,
    val bleUseL2CAPInEngagement: Boolean = true,
    val nfcCommandDataFieldMaxLength: Int = DEFAULT_NFC_COMMAND_DATA_FIELD_MAX_LENGTH,
    val nfcResponseDataFieldMaxLength: Int = DEFAULT_NFC_RESPONSE_DATA_FIELD_MAX_LENGTH,
    val nfcScanMessage: String = "Hold near credential holder's device",
    val nfcTransferMessage: String = "Transferring data, keep in reader's field",
    val preferredTransport: TransportPreference = TransportPreference.AUTO,
    val transportTimeout: Duration = 30.seconds,
    val responseTimeout: Duration = 300.seconds,
) {
    internal fun isBleCentralClientModeEnabled(): Boolean {
        return bleEnabled && bleCentralClientMode
    }
    internal fun isBlePeripheralServerModeEnabled(): Boolean {
        return bleEnabled && blePeripheralServerMode
    }
    // TODO: Add input validation for Builder fields (e.g. nfcCommandDataFieldMaxLength > 0, transportTimeout > 0)
    class Builder {
        private var requests: List<VerificationRequest> = emptyList()
        private var allowMultipleRequests: Boolean = false
        private var bleEnabled: Boolean = true
        private var nfcDataTransferEnabled: Boolean = true
        private var bleCentralClientMode: Boolean = true
        private var blePeripheralServerMode: Boolean = true
        private var bleUseL2CAP: Boolean = false
        private var bleUseL2CAPInEngagement: Boolean = true
        private var nfcCommandDataFieldMaxLength: Int = DEFAULT_NFC_COMMAND_DATA_FIELD_MAX_LENGTH
        private var nfcResponseDataFieldMaxLength: Int = DEFAULT_NFC_RESPONSE_DATA_FIELD_MAX_LENGTH
        private var nfcScanMessage: String = "Hold near credential holder's device"
        private var nfcTransferMessage: String = "Transferring data, keep in reader's field"
        private var preferredTransport: TransportPreference = TransportPreference.AUTO
        private var transportTimeout: Duration = 30.seconds
        private var responseTimeout: Duration = 300.seconds
        fun withRequests(value: List<VerificationRequest>): Builder = apply { requests = value }
        fun withAllowMultipleRequests(value: Boolean): Builder = apply { allowMultipleRequests = value }
        fun withBleEnabled(value: Boolean): Builder = apply { bleEnabled = value }
        fun withNfcDataTransferEnabled(value: Boolean): Builder = apply { nfcDataTransferEnabled = value }
        fun withBleCentralClientMode(value: Boolean): Builder = apply { bleCentralClientMode = value }
        fun withBlePeripheralServerMode(value: Boolean): Builder = apply { blePeripheralServerMode = value }
        fun withBleUseL2CAP(value: Boolean): Builder = apply { bleUseL2CAP = value }
        fun withBleUseL2CAPInEngagement(value: Boolean): Builder = apply { bleUseL2CAPInEngagement = value }
        fun withNfcCommandDataFieldMaxLength(value: Int): Builder = apply { nfcCommandDataFieldMaxLength = value }
        fun withNfcResponseDataFieldMaxLength(value: Int): Builder = apply { nfcResponseDataFieldMaxLength = value }
        fun withNfcScanMessage(value: String): Builder = apply { nfcScanMessage = value }
        fun withNfcTransferMessage(value: String): Builder = apply { nfcTransferMessage = value }
        fun withPreferredTransport(value: TransportPreference): Builder = apply { preferredTransport = value }
        fun withTransportTimeout(value: Duration): Builder = apply { transportTimeout = value }
        fun withResponseTimeout(value: Duration): Builder = apply { responseTimeout = value }
        fun build(): ProximityVerificationSettings {
            return ProximityVerificationSettings(
                requests = requests,
                allowMultipleRequests = allowMultipleRequests,
                bleEnabled = bleEnabled,
                nfcDataTransferEnabled = nfcDataTransferEnabled,
                bleCentralClientMode = bleCentralClientMode,
                blePeripheralServerMode = blePeripheralServerMode,
                bleUseL2CAP = bleUseL2CAP,
                bleUseL2CAPInEngagement = bleUseL2CAPInEngagement,
                nfcCommandDataFieldMaxLength = nfcCommandDataFieldMaxLength,
                nfcResponseDataFieldMaxLength = nfcResponseDataFieldMaxLength,
                nfcScanMessage = nfcScanMessage,
                nfcTransferMessage = nfcTransferMessage,
                preferredTransport = preferredTransport,
                transportTimeout = transportTimeout,
                responseTimeout = responseTimeout
            )
        }
    }
    companion object {
        private const val DEFAULT_NFC_COMMAND_DATA_FIELD_MAX_LENGTH: Int = 0xFFFF
        private const val DEFAULT_NFC_RESPONSE_DATA_FIELD_MAX_LENGTH: Int = 0x10000
        fun builder(): Builder {
            return Builder()
        }
    }
}

/**
 * Preferred transport protocol for proximity verification.
 */
enum class TransportPreference {
    /** Prefer Bluetooth Low Energy transport. */
    BLE,
    /** Prefer NFC transport. */
    NFC,
    /** Automatically select the best available transport. */
    AUTO
}
