package it.infocert.proximityverification.core.handlers

import platform.CoreNFC.NFCNDEFReaderSession

/**
 * iOS implementation of NFC availability and state checking.
 */
actual object NfcChecker {
    
    /**
     * Check if NFC hardware is available on this iOS device.
     * On iOS, this checks if NFCNDEFReaderSession is available.
     * @return true if NFC hardware is present, false otherwise
     */
    actual fun getNfcAvailability(): Boolean {
        return try {
            // Check if NFC reading is available on this device
            // Available on iPhone 7 and later
            NFCNDEFReaderSession.readingAvailable
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if NFC is currently enabled on this iOS device.
     * On iOS, NFC is always "enabled" if the hardware is available.
     * The system prompts the user when an NFC session is started.
     * @return true if NFC is available (iOS doesn't have a separate "enabled" state)
     */
    actual fun isNfcEnabled(): Boolean {
        return getNfcAvailability()
    }
}

