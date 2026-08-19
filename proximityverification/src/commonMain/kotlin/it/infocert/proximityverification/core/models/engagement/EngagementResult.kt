package it.infocert.proximityverification.core.models.engagement

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.engagement.DeviceEngagement
import org.multipaz.mdoc.transport.MdocTransport

/**
 * Internal result of parsing device engagement data.
 * Bundles parsed engagement, handover, reusable transport, and resolved connection methods
 * needed by [VerificationSession] and the transport resolution logic in [ProximityVerification].
 */
internal class EngagementResult(
    val engagementType: EngagementType,
    val encodedDeviceEngagement: ByteArray,
    val parsedEngagement: DeviceEngagement,
    val handover: DataItem,
    val reusableTransport: MdocTransport?,
    val resolvedConnectionMethods: List<MdocConnectionMethod>
) {
    internal companion object {
        /**
         * Builds an [EngagementResult] from NFC scan output.
         * Centralises the mapping logic shared by Android and iOS NFC extensions.
         */
        internal fun fromNfcScan(
            encodedDeviceEngagement: ByteArray,
            handover: DataItem,
            reusableTransport: MdocTransport,
            enabledConnectionMethods: List<MdocConnectionMethod>
        ): EngagementResult {
            val parsedEngagement: DeviceEngagement = DeviceEngagement.fromDataItem(
                Cbor.decode(encodedDeviceEngagement)
            )
            val engagementType: EngagementType = EngagementType.fromNfcHandover(handover)
            val effectiveMethods: List<MdocConnectionMethod> = enabledConnectionMethods.ifEmpty {
                listOf(reusableTransport.connectionMethod)
            }
            return EngagementResult(
                engagementType = engagementType,
                encodedDeviceEngagement = encodedDeviceEngagement,
                parsedEngagement = parsedEngagement,
                handover = handover,
                reusableTransport = reusableTransport,
                resolvedConnectionMethods = effectiveMethods
            )
        }
    }
}
