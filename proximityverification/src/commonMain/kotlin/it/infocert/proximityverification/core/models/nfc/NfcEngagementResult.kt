package it.infocert.proximityverification.core.models.nfc

import org.multipaz.cbor.DataItem
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.transport.MdocTransport
import kotlin.time.Duration

/**
 * Result of an NFC engagement scan.
 * Contains all necessary data to proceed with verification.
 *
 * @property encodedDeviceEngagement The raw device engagement bytes from the NFC tag
 * @property transport Optional pre-established transport (if NFC data transfer is used)
 * @property handover The handover data item (static or negotiated)
 * @property connectionMethod The connection method to use for data transfer
 * @property processingDuration Time taken to complete the NFC engagement
 */
internal class NfcEngagementResult internal constructor(
    val encodedDeviceEngagement: ByteArray,
    internal val transport: MdocTransport?,
    internal val handover: DataItem,
    internal val connectionMethod: MdocConnectionMethod,
    val processingDuration: Duration
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NfcEngagementResult

        if (!encodedDeviceEngagement.contentEquals(other.encodedDeviceEngagement)) return false
        if (transport != other.transport) return false
        if (handover != other.handover) return false
        if (connectionMethod != other.connectionMethod) return false
        if (processingDuration != other.processingDuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encodedDeviceEngagement.contentHashCode()
        result = 31 * result + (transport?.hashCode() ?: 0)
        result = 31 * result + handover.hashCode()
        result = 31 * result + connectionMethod.hashCode()
        result = 31 * result + processingDuration.hashCode()
        return result
    }
}
