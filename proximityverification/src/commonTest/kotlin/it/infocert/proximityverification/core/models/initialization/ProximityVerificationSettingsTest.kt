package it.infocert.proximityverification.core.models.initialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProximityVerificationSettingsTest {

    @Test
    fun settingsDisableBleModesWhenBleIsDisabled(): Unit {
        val inputSettings = ProximityVerificationSettings(
            requests = emptyList(),
            bleEnabled = false,
            nfcDataTransferEnabled = true,
            bleCentralClientMode = true,
            blePeripheralServerMode = true
        )
        assertFalse(inputSettings.isBleCentralClientModeEnabled())
        assertFalse(inputSettings.isBlePeripheralServerModeEnabled())
        assertTrue(inputSettings.nfcDataTransferEnabled)
    }

    @Test
    fun settingsEnableBleModesWhenBleIsEnabled(): Unit {
        val inputSettings = ProximityVerificationSettings(
            requests = emptyList(),
            bleEnabled = true,
            bleCentralClientMode = true,
            blePeripheralServerMode = true
        )
        assertTrue(inputSettings.isBleCentralClientModeEnabled())
        assertTrue(inputSettings.isBlePeripheralServerModeEnabled())
    }

    @Test
    fun settingsCentralClientDisabledWhenModeIsFalse(): Unit {
        val inputSettings = ProximityVerificationSettings(
            requests = emptyList(),
            bleEnabled = true,
            bleCentralClientMode = false,
            blePeripheralServerMode = true
        )
        assertFalse(inputSettings.isBleCentralClientModeEnabled())
        assertTrue(inputSettings.isBlePeripheralServerModeEnabled())
    }

    @Test
    fun defaultTransportTimeoutIs30Seconds(): Unit {
        val inputSettings = ProximityVerificationSettings(requests = emptyList())
        assertEquals(30.seconds, inputSettings.transportTimeout)
    }

    @Test
    fun defaultResponseTimeoutIs300Seconds(): Unit {
        val inputSettings = ProximityVerificationSettings(requests = emptyList())
        assertEquals(300.seconds, inputSettings.responseTimeout)
    }

    @Test
    fun defaultNfcFieldLengths(): Unit {
        val inputSettings = ProximityVerificationSettings(requests = emptyList())
        assertEquals(0xFFFF, inputSettings.nfcCommandDataFieldMaxLength)
        assertEquals(0x10000, inputSettings.nfcResponseDataFieldMaxLength)
    }

    @Test
    fun defaultPreferredTransportIsAuto(): Unit {
        val inputSettings = ProximityVerificationSettings(requests = emptyList())
        assertEquals(TransportPreference.AUTO, inputSettings.preferredTransport)
    }

    @Test
    fun defaultNfcMessages(): Unit {
        val inputSettings = ProximityVerificationSettings(requests = emptyList())
        assertEquals("Hold near credential holder's device", inputSettings.nfcScanMessage)
        assertEquals("Transferring data, keep in reader's field", inputSettings.nfcTransferMessage)
    }

    @Test
    fun builderMustMirrorDefaults(): Unit {
        val actualSettings: ProximityVerificationSettings = ProximityVerificationSettings.builder().build()
        val expectedSettings: ProximityVerificationSettings = ProximityVerificationSettings(
            requests = emptyList()
        )
        assertEquals(expectedSettings, actualSettings)
    }

    @Test
    fun builderOverridesAllFields(): Unit {
        val actualSettings: ProximityVerificationSettings = ProximityVerificationSettings.builder()
            .withBleEnabled(false)
            .withNfcDataTransferEnabled(false)
            .withBleCentralClientMode(false)
            .withBlePeripheralServerMode(true)
            .withBleUseL2CAP(true)
            .withBleUseL2CAPInEngagement(false)
            .withNfcCommandDataFieldMaxLength(128)
            .withNfcResponseDataFieldMaxLength(256)
            .withNfcScanMessage("scan")
            .withNfcTransferMessage("transfer")
            .withPreferredTransport(TransportPreference.NFC)
            .withTransportTimeout(10.seconds)
            .withResponseTimeout(60.seconds)
            .withAllowMultipleRequests(true)
            .build()
        assertFalse(actualSettings.bleEnabled)
        assertFalse(actualSettings.nfcDataTransferEnabled)
        assertFalse(actualSettings.bleCentralClientMode)
        assertTrue(actualSettings.blePeripheralServerMode)
        assertTrue(actualSettings.bleUseL2CAP)
        assertFalse(actualSettings.bleUseL2CAPInEngagement)
        assertEquals(128, actualSettings.nfcCommandDataFieldMaxLength)
        assertEquals(256, actualSettings.nfcResponseDataFieldMaxLength)
        assertEquals("scan", actualSettings.nfcScanMessage)
        assertEquals("transfer", actualSettings.nfcTransferMessage)
        assertEquals(TransportPreference.NFC, actualSettings.preferredTransport)
        assertEquals(10.seconds, actualSettings.transportTimeout)
        assertEquals(60.seconds, actualSettings.responseTimeout)
        assertTrue(actualSettings.allowMultipleRequests)
    }
}
