package it.infocert.proximityverification.core.handlers

/**
 * Platform-specific NFC availability and state checker.
 * Use expect/actual pattern to implement platform-specific behavior.
 */
expect object NfcChecker {
    /**
     * Check if NFC hardware is available on this device.
     * @return true if NFC hardware is present, false otherwise
     */
    fun getNfcAvailability(): Boolean
    
    /**
     * Check if NFC is currently enabled on this device.
     * @return true if NFC is enabled and ready to use, false otherwise
     */
    fun isNfcEnabled(): Boolean
}

