package it.infocert.proximityverification.core.models.engagement

import it.infocert.proximityverification.core.models.nfc.NfcEngagementResult

/**
 * Sealed class representing different types of device engagement.
 * Used internally to handle both QR code and NFC engagement paths.
 */
internal sealed class EngagementData {
    /**
     * QR code-based engagement.
     * @property encodedBytes The raw device engagement bytes decoded from the QR code
     */
    data class QrCode(val encodedBytes: ByteArray) : EngagementData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as QrCode

            return encodedBytes.contentEquals(other.encodedBytes)
        }

        override fun hashCode(): Int {
            return encodedBytes.contentHashCode()
        }
    }
    
    /**
     * NFC-based engagement.
     * @property result The complete NFC engagement result including transport and handover
     */
    data class Nfc(val result: NfcEngagementResult) : EngagementData()
}

