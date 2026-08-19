package it.infocert.proximityverification.core.models.engagement

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple

/**
 * Enum representing the type of engagement used for device verification.
 */
enum class EngagementType(val description: String) {
    QR("QR code"),
    NFC_STATIC_HANDOVER("NFC Static Handover"),
    NFC_NEGOTIATED_HANDOVER("NFC Negotiated Handover");

    companion object {
        /**
         * Determines the NFC engagement type from the handover CBOR structure.
         * Static handover has `null` at index 1; negotiated handover has a non-null value.
         */
        internal fun fromNfcHandover(handover: DataItem): EngagementType {
            return if (handover.asArray[1] == Simple.NULL) {
                NFC_STATIC_HANDOVER
            } else {
                NFC_NEGOTIATED_HANDOVER
            }
        }
    }
}
