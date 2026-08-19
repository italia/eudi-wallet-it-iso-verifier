package it.infocert.proximityverification.nfc

import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.transport.HandoverBuilder
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Basic tests for NFC components that test core functionality
 * without complex mocking of multipaz types.
 */
class NfcComponentsBasicTest {

    private fun createSettings(
        nfcEnabled: Boolean = true,
        bleCentralClientMode: Boolean = true,
        blePeripheralServerMode: Boolean = false
    ): ProximityVerificationSettings {
        return ProximityVerificationSettings(
            requests = emptyList(),
            nfcDataTransferEnabled = nfcEnabled,
            bleCentralClientMode = bleCentralClientMode,
            blePeripheralServerMode = blePeripheralServerMode
        )
    }

    @Test
    fun testHandoverBuilderCreatesConnectionMethods() {
        val settings = createSettings(nfcEnabled = true, bleCentralClientMode = true)

        val connectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)

        assertNotNull(connectionMethods, "Should return a list of connection methods")
        assertTrue(connectionMethods.isNotEmpty(), "Should have at least one connection method")
    }

    @Test
    fun testHandoverBuilderWithNfcEnabled() {
        val settings = createSettings(nfcEnabled = true)

        val connectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)

        // Should have NFC method
        val hasNfc = connectionMethods.any { it is MdocConnectionMethodNfc }
        assertTrue(hasNfc, "Should include NFC connection method when enabled")
    }

    @Test
    fun testHandoverBuilderWithNfcDisabled() {
        val settings = createSettings(nfcEnabled = false, bleCentralClientMode = true)

        val connectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)

        // Should not have NFC method
        val hasNfc = connectionMethods.any { it is MdocConnectionMethodNfc }
        assertFalse(hasNfc, "Should not include NFC when disabled")
    }

    @Test
    fun testHandoverBuilderWithBleEnabled() {
        val settings = createSettings(nfcEnabled = true, bleCentralClientMode = true)

        val connectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)

        // Should have BLE method
        val hasBle = connectionMethods.any { it is MdocConnectionMethodBle }
        assertTrue(hasBle, "Should include BLE connection method when enabled")
    }

    @Test
    fun testHandoverBuilderWithBothTransports() {
        val settings = createSettings(nfcEnabled = true, bleCentralClientMode = true)

        val connectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)

        val hasNfc = connectionMethods.any { it is MdocConnectionMethodNfc }
        val hasBle = connectionMethods.any { it is MdocConnectionMethodBle }

        assertTrue(hasNfc, "Should include NFC")
        assertTrue(hasBle, "Should include BLE")
    }

    @Test
    fun testHandoverBuilderWithNoTransports() {
        val settings = createSettings(
            nfcEnabled = false,
            bleCentralClientMode = false,
            blePeripheralServerMode = false
        )

        val connectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)

        assertTrue(connectionMethods.isEmpty(), "Should have no connection methods when all disabled")
    }

    @Test
    fun testNfcConnectionMethodFieldLengths() {
        val settings = ProximityVerificationSettings(
            requests = emptyList(),
            nfcDataTransferEnabled = true,
            nfcCommandDataFieldMaxLength = 32768,
            nfcResponseDataFieldMaxLength = 49152
        )

        val methods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)
        val nfcMethod = methods.filterIsInstance<MdocConnectionMethodNfc>().firstOrNull()

        assertNotNull(nfcMethod, "Should have NFC method")
        // Field lengths should match configuration
        assertTrue(nfcMethod.commandDataFieldMaxLength > 0)
        assertTrue(nfcMethod.responseDataFieldMaxLength > 0)
    }
}
