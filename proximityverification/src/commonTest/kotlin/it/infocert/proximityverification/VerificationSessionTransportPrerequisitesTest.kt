package it.infocert.proximityverification

import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationSessionTransportPrerequisitesTest {

    private val noOpLoggerDelegate: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    @BeforeTest
    fun setUp() {
        LoggerConfig.delegateLogger = noOpLoggerDelegate
    }

    @AfterTest
    fun tearDown() {
        LoggerConfig.delegateLogger = null
    }

    @Test
    fun bleOnlySettingsBleAvailableReturnsUnchangedSettings(): TestResult = runTest {
        val inputSettings: ProximityVerificationSettings = createSettings(
            bleEnabled = true,
            bleCentralClientMode = true,
            blePeripheralServerMode = true,
            nfcDataTransferEnabled = false
        )
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { true },
            nfcAvailabilityChecker = { false },
            nfcEnabledChecker = { false }
        )
        assertEquals(inputSettings, actualSettings)
    }

    @Test
    fun bleOnlySettingsBleUnavailableThrowsPve09(): TestResult = runTest {
        val inputSettings = createSettings(
            bleEnabled = true,
            bleCentralClientMode = false,
            blePeripheralServerMode = true,
            nfcDataTransferEnabled = false
        )
        val actualException = assertFailsWith<ProximityVerificationException> {
            resolveUsableTransportSettings(
                settings = inputSettings,
                bluetoothChecker = { false },
                nfcAvailabilityChecker = { true },
                nfcEnabledChecker = { true }
            )
        }
        assertEquals("PVE09", actualException.code.code)
    }

    @Test
    fun nfcOnlySettingsNfcAvailableAndEnabledReturnsUnchangedSettings(): TestResult = runTest {
        val inputSettings = createSettings(
            bleEnabled = false,
            bleCentralClientMode = false,
            blePeripheralServerMode = false,
            nfcDataTransferEnabled = true
        )
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { false },
            nfcAvailabilityChecker = { true },
            nfcEnabledChecker = { true }
        )
        assertEquals(inputSettings, actualSettings)
    }

    @Test
    fun nfcOnlySettingsNfcHardwareAbsentThrowsPve24(): TestResult = runTest {
        val inputSettings = createSettings(
            bleEnabled = false,
            bleCentralClientMode = false,
            blePeripheralServerMode = false,
            nfcDataTransferEnabled = true
        )
        val actualException = assertFailsWith<ProximityVerificationException> {
            resolveUsableTransportSettings(
                settings = inputSettings,
                bluetoothChecker = { true },
                nfcAvailabilityChecker = { false },
                nfcEnabledChecker = { true }
            )
        }
        assertEquals("PVE24", actualException.code.code)
    }

    @Test
    fun nfcOnlySettingsNfcDisabledThrowsPve25(): TestResult = runTest {
        val inputSettings = createSettings(
            bleEnabled = false,
            bleCentralClientMode = false,
            blePeripheralServerMode = false,
            nfcDataTransferEnabled = true
        )
        val actualException = assertFailsWith<ProximityVerificationException> {
            resolveUsableTransportSettings(
                settings = inputSettings,
                bluetoothChecker = { true },
                nfcAvailabilityChecker = { true },
                nfcEnabledChecker = { false }
            )
        }
        assertEquals("PVE25", actualException.code.code)
    }

    @Test
    fun bothConfiguredBothAvailableReturnsUnchangedSettings(): TestResult = runTest {
        val inputSettings = createSettings()
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { true },
            nfcAvailabilityChecker = { true },
            nfcEnabledChecker = { true }
        )
        assertEquals(inputSettings, actualSettings)
    }

    @Test
    fun bothConfiguredBleUnavailableNfcAvailableReturnsBleDisabledSettings(): TestResult = runTest {
        val inputSettings = createSettings()
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { false },
            nfcAvailabilityChecker = { true },
            nfcEnabledChecker = { true }
        )
        assertFalse(actualSettings.isBlePeripheralServerModeEnabled())
        assertFalse(actualSettings.isBleCentralClientModeEnabled())
        assertTrue(actualSettings.nfcDataTransferEnabled)
    }

    @Test
    fun bothConfiguredNfcUnavailableBleAvailableReturnsNfcDisabledSettings(): TestResult = runTest {
        val inputSettings = createSettings()
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { true },
            nfcAvailabilityChecker = { false },
            nfcEnabledChecker = { true }
        )
        assertFalse(actualSettings.nfcDataTransferEnabled)
        assertTrue(actualSettings.isBlePeripheralServerModeEnabled())
        assertTrue(actualSettings.isBleCentralClientModeEnabled())
    }

    @Test
    fun bothConfiguredNfcDisabledBleAvailableReturnsNfcDisabledSettings(): TestResult = runTest {
        val inputSettings = createSettings()
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { true },
            nfcAvailabilityChecker = { true },
            nfcEnabledChecker = { false }
        )
        assertFalse(actualSettings.nfcDataTransferEnabled)
        assertTrue(actualSettings.isBlePeripheralServerModeEnabled())
        assertTrue(actualSettings.isBleCentralClientModeEnabled())
    }

    @Test
    fun bothConfiguredBothUnavailableThrowsPve09(): TestResult = runTest {
        val inputSettings = createSettings()
        val actualException = assertFailsWith<ProximityVerificationException> {
            resolveUsableTransportSettings(
                settings = inputSettings,
                bluetoothChecker = { false },
                nfcAvailabilityChecker = { false },
                nfcEnabledChecker = { false }
            )
        }
        assertEquals("PVE09", actualException.code.code)
    }

    @Test
    fun neitherConfiguredReturnsUnchangedSettings(): TestResult = runTest {
        val inputSettings = createSettings(
            bleEnabled = false,
            bleCentralClientMode = false,
            blePeripheralServerMode = false,
            nfcDataTransferEnabled = false
        )
        val actualSettings = resolveUsableTransportSettings(
            settings = inputSettings,
            bluetoothChecker = { false },
            nfcAvailabilityChecker = { false },
            nfcEnabledChecker = { false }
        )
        assertEquals(inputSettings, actualSettings)
    }

    @Test
    fun alreadyResolvedSettingsBleDisabledDoesNotInvokeBluetoothChecker(): TestResult = runTest {
        // Settings già risolti: BLE disabilitato, NFC disponibile.
        // Il BT checker non deve essere invocato una seconda volta.
        val resolvedSettings = createSettings(
            bleEnabled = false,
            bleCentralClientMode = false,
            blePeripheralServerMode = false,
            nfcDataTransferEnabled = true
        )
        var bluetoothCheckerInvoked = false
        val result = resolveUsableTransportSettings(
            settings = resolvedSettings,
            bluetoothChecker = { bluetoothCheckerInvoked = true; false },
            nfcAvailabilityChecker = { true },
            nfcEnabledChecker = { true }
        )
        assertFalse(bluetoothCheckerInvoked, "BT checker should not be called when BLE is already disabled in settings")
        assertEquals(resolvedSettings, result)
    }

    private fun createSettings(
        bleEnabled: Boolean = true,
        bleCentralClientMode: Boolean = true,
        blePeripheralServerMode: Boolean = true,
        nfcDataTransferEnabled: Boolean = true
    ): ProximityVerificationSettings {
        return ProximityVerificationSettings(
            requests = emptyList(),
            bleEnabled = bleEnabled,
            bleCentralClientMode = bleCentralClientMode,
            blePeripheralServerMode = blePeripheralServerMode,
            nfcDataTransferEnabled = nfcDataTransferEnabled
        )
    }
}
