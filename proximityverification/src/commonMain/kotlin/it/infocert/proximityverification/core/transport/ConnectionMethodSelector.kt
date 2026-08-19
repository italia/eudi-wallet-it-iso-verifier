package it.infocert.proximityverification.core.transport

import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.models.initialization.TransportPreference
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc

/**
 * Selects a connection method using ISO 18013-5 transport preference rules.
 */
internal object ConnectionMethodSelector {

    internal fun select(
        connectionMethods: List<MdocConnectionMethod>,
        preferredTransport: TransportPreference?
    ): MdocConnectionMethod? {
        if (connectionMethods.isEmpty()) {
            return null
        }
        return when (preferredTransport) {
            TransportPreference.NFC -> {
                connectionMethods.firstOrNull { it is MdocConnectionMethodNfc }
                    ?: connectionMethods.firstOrNull()
            }
            TransportPreference.BLE -> {
                selectBleMethod(connectionMethods)
                    ?: connectionMethods.firstOrNull()
            }
            TransportPreference.AUTO, null -> {
                selectBleMethod(connectionMethods)
                    ?: connectionMethods.firstOrNull { it is MdocConnectionMethodNfc }
                    ?: connectionMethods.firstOrNull()
            }
        }
    }

    internal fun filterBySettings(
        connectionMethods: List<MdocConnectionMethod>,
        settings: ProximityVerificationSettings
    ): List<MdocConnectionMethod> {
        return connectionMethods.filter { connectionMethod ->
            when (connectionMethod) {
                is MdocConnectionMethodBle -> isBleMethodAllowed(connectionMethod, settings)
                is MdocConnectionMethodNfc -> settings.nfcDataTransferEnabled
                else -> true
            }
        }
    }

    internal fun isBleMethodAllowed(
        connectionMethod: MdocConnectionMethodBle,
        settings: ProximityVerificationSettings
    ): Boolean {
        val canUseCentralClientMode: Boolean = connectionMethod.supportsCentralClientMode &&
            settings.isBleCentralClientModeEnabled()
        val canUsePeripheralServerMode: Boolean = connectionMethod.supportsPeripheralServerMode &&
            settings.isBlePeripheralServerModeEnabled()
        return canUseCentralClientMode || canUsePeripheralServerMode
    }

    internal fun selectBleMethod(
        connectionMethods: List<MdocConnectionMethod>
    ): MdocConnectionMethodBle? {
        val bleMethods: List<MdocConnectionMethodBle> =
            connectionMethods.filterIsInstance<MdocConnectionMethodBle>()
        if (bleMethods.isEmpty()) {
            return null
        }
        return bleMethods.firstOrNull { it.supportsCentralClientMode }
            ?: bleMethods.firstOrNull()
    }

    internal fun requireNonEmpty(
        filtered: List<MdocConnectionMethod>,
        offered: List<MdocConnectionMethod>
    ): List<MdocConnectionMethod> {
        if (filtered.isEmpty()) {
            val offeredDesc = offered.joinToString { it::class.simpleName ?: it.toString() }
            throw ProximityVerificationException(
                "No compatible connection method: mDL offers [$offeredDesc] but none are available with the current transport configuration.",
                code = ProximityVerificationExceptionCode.PVE41()
            )
        }
        return filtered
    }
}
