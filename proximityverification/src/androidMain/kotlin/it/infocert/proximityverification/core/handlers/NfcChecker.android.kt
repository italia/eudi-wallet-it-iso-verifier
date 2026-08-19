package it.infocert.proximityverification.core.handlers

import android.content.Context
import android.nfc.NfcAdapter
import it.infocert.proximityverification.ContextInitializer

/**
 * Android implementation of NFC availability and state checking.
 */
actual object NfcChecker {
    
    /**
     * Check if NFC hardware is available on this Android device.
     * @return true if NFC hardware is present, false otherwise
     */
    actual fun getNfcAvailability(): Boolean {
        return try {
            val context = ContextInitializer.applicationContext
            val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            nfcAdapter != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if NFC is currently enabled on this Android device.
     * @return true if NFC is enabled and ready to use, false otherwise
     */
    actual fun isNfcEnabled(): Boolean {
        return try {
            val context = ContextInitializer.applicationContext
            val nfcAdapter = NfcAdapter.getDefaultAdapter(context) ?: return false
            nfcAdapter.isEnabled
        } catch (e: Exception) {
            false
        }
    }
}

