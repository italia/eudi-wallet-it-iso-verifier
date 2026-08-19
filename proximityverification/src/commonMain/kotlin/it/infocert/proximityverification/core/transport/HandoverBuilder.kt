package it.infocert.proximityverification.core.transport

import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.util.UUID

/**
 * Builds negotiated handover connection methods from verification settings.
 * Used for NFC engagement to advertise available connection methods to the holder.
 */
internal object HandoverBuilder {

    /**
     * Build a list of [MdocConnectionMethod] based on the transport configuration.
     *
     * @param settings Verification settings specifying which transports are enabled.
     * @param bleUuid Optional UUID to use for BLE connection methods (generated if not provided)
     * @return List of connection methods to be used in negotiated handover
     */
    internal fun buildNegotiatedHandoverConnectionMethods(
        settings: ProximityVerificationSettings,
        bleUuid: UUID? = null
    ): List<MdocConnectionMethod> {
        val connectionMethods = mutableListOf<MdocConnectionMethod>()
        val uuid = bleUuid ?: UUID.randomUUID()
        if (settings.isBleCentralClientModeEnabled()) {
            connectionMethods.add(
                MdocConnectionMethodBle(
                    supportsPeripheralServerMode = false,
                    supportsCentralClientMode = true,
                    peripheralServerModeUuid = null,
                    centralClientModeUuid = uuid
                )
            )
        }
        if (settings.isBlePeripheralServerModeEnabled()) {
            connectionMethods.add(
                MdocConnectionMethodBle(
                    supportsPeripheralServerMode = true,
                    supportsCentralClientMode = false,
                    peripheralServerModeUuid = uuid,
                    centralClientModeUuid = null
                )
            )
        }
        if (settings.nfcDataTransferEnabled) {
            connectionMethods.add(
                MdocConnectionMethodNfc(
                    commandDataFieldMaxLength = settings.nfcCommandDataFieldMaxLength.toLong(),
                    responseDataFieldMaxLength = settings.nfcResponseDataFieldMaxLength.toLong()
                )
            )
        }
        return connectionMethods
    }
}
