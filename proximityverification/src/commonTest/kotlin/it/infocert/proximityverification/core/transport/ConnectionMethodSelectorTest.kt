package it.infocert.proximityverification.core.transport

import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.models.initialization.TransportPreference
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.util.UUID
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionMethodSelectorTest {

    private val sharedUuidForBle: UUID = UUID.randomUUID()

    private val inputBleCentralClient: MdocConnectionMethodBle = MdocConnectionMethodBle(
        supportsPeripheralServerMode = false,
        supportsCentralClientMode = true,
        peripheralServerModeUuid = null,
        centralClientModeUuid = sharedUuidForBle
    )

    private val inputBlePeripheralServer: MdocConnectionMethodBle = MdocConnectionMethodBle(
        supportsPeripheralServerMode = true,
        supportsCentralClientMode = false,
        peripheralServerModeUuid = sharedUuidForBle,
        centralClientModeUuid = null
    )

    // ── selectBleMethod ─────────────────────────────────────────────

    /**
     * Regression: when settings narrow BLE from
     * central+peripheral to peripheral-only, [ConnectionMethodSelector.selectBleMethod]
     * on the runtime-filtered list must return the peripheral method, not the central one.
     */
    @Test
    fun selectBleMethod_shouldRespectRuntimeNarrowedMethodList(): Unit {
        val runtimeNarrowedMethods: List<MdocConnectionMethod> = listOf(inputBlePeripheralServer)
        val actualMethod: MdocConnectionMethodBle? =
            ConnectionMethodSelector.selectBleMethod(runtimeNarrowedMethods)
        assertEquals(inputBlePeripheralServer, actualMethod)
    }

    @Test
    fun selectBleMethod_prefersCentralClientWhenBothPresent(): Unit {
        val actualMethod: MdocConnectionMethodBle? =
            ConnectionMethodSelector.selectBleMethod(
                listOf(inputBlePeripheralServer, inputBleCentralClient)
            )
        assertEquals(inputBleCentralClient, actualMethod)
    }

    @Test
    fun selectBleMethod_returnsNullForEmptyList(): Unit {
        assertNull(ConnectionMethodSelector.selectBleMethod(emptyList()))
    }

    // ── select ──────────────────────────────────────────────────────

    @Test
    fun select_shouldPreferCentralClientModeWhenBothBleModesAreAvailable(): Unit {
        val sharedUuid: UUID = UUID.randomUUID()
        val peripheralServerMode: MdocConnectionMethodBle = MdocConnectionMethodBle(
            supportsPeripheralServerMode = true,
            supportsCentralClientMode = false,
            peripheralServerModeUuid = sharedUuid,
            centralClientModeUuid = null
        )
        val centralClientMode: MdocConnectionMethodBle = MdocConnectionMethodBle(
            supportsPeripheralServerMode = false,
            supportsCentralClientMode = true,
            peripheralServerModeUuid = null,
            centralClientModeUuid = sharedUuid
        )
        val selectedMethod: MdocConnectionMethod? = ConnectionMethodSelector.select(
            connectionMethods = listOf(peripheralServerMode, centralClientMode),
            preferredTransport = TransportPreference.AUTO
        )
        assertEquals(centralClientMode, selectedMethod)
    }

    @Test
    fun select_shouldReturnNfcWhenNfcIsPreferred(): Unit {
        val sharedUuid: UUID = UUID.randomUUID()
        val centralClientMode: MdocConnectionMethodBle = MdocConnectionMethodBle(
            supportsPeripheralServerMode = false,
            supportsCentralClientMode = true,
            peripheralServerModeUuid = null,
            centralClientModeUuid = sharedUuid
        )
        val nfcMethod: MdocConnectionMethodNfc = MdocConnectionMethodNfc(
            commandDataFieldMaxLength = 0xFFFF,
            responseDataFieldMaxLength = 0x10000
        )
        val selectedMethod: MdocConnectionMethod? = ConnectionMethodSelector.select(
            connectionMethods = listOf(centralClientMode, nfcMethod),
            preferredTransport = TransportPreference.NFC
        )
        assertEquals(nfcMethod, selectedMethod)
    }

    @Test
    fun filterBySettings_shouldExcludeDisabledTransports(): Unit {
        val sharedUuid: UUID = UUID.randomUUID()
        val centralClientMode: MdocConnectionMethodBle = MdocConnectionMethodBle(
            supportsPeripheralServerMode = false,
            supportsCentralClientMode = true,
            peripheralServerModeUuid = null,
            centralClientModeUuid = sharedUuid
        )
        val peripheralServerMode: MdocConnectionMethodBle = MdocConnectionMethodBle(
            supportsPeripheralServerMode = true,
            supportsCentralClientMode = false,
            peripheralServerModeUuid = sharedUuid,
            centralClientModeUuid = null
        )
        val nfcMethod: MdocConnectionMethodNfc = MdocConnectionMethodNfc(
            commandDataFieldMaxLength = 0xFFFF,
            responseDataFieldMaxLength = 0x10000
        )
        val settings: ProximityVerificationSettings = ProximityVerificationSettings(
            requests = emptyList(),
            bleEnabled = true,
            bleCentralClientMode = true,
            blePeripheralServerMode = false,
            nfcDataTransferEnabled = false
        )
        val filteredMethods: List<MdocConnectionMethod> = ConnectionMethodSelector
            .filterBySettings(
                connectionMethods = listOf(centralClientMode, peripheralServerMode, nfcMethod),
                settings = settings
            )
        assertTrue(filteredMethods.contains(centralClientMode))
        assertFalse(filteredMethods.contains(peripheralServerMode))
        assertFalse(filteredMethods.contains(nfcMethod))
    }

    // ── requireNonEmpty ─────────────────────────────────────────────

    @Test
    fun requireNonEmpty_throwsPve41WhenFilteredListIsEmpty(): Unit {
        val uuid = UUID.randomUUID()
        val bleMethod = MdocConnectionMethodBle(
            supportsPeripheralServerMode = false,
            supportsCentralClientMode = true,
            peripheralServerModeUuid = null,
            centralClientModeUuid = uuid
        )
        val exception = assertFailsWith<ProximityVerificationException> {
            ConnectionMethodSelector.requireNonEmpty(
                filtered = emptyList(),
                offered = listOf(bleMethod)
            )
        }
        assertEquals("PVE41", exception.code.code)
    }

    @Test
    fun requireNonEmpty_returnsFilteredListWhenNonEmpty(): Unit {
        val nfcMethod = MdocConnectionMethodNfc(
            commandDataFieldMaxLength = 0xFFFF,
            responseDataFieldMaxLength = 0x10000
        )
        val result = ConnectionMethodSelector.requireNonEmpty(
            filtered = listOf(nfcMethod),
            offered = listOf(nfcMethod)
        )
        assertEquals(listOf(nfcMethod), result)
    }
}
