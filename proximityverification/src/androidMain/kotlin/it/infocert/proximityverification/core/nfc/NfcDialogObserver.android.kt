package it.infocert.proximityverification.core.nfc

import android.app.Activity
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.util.Logger

private const val TAG = "NfcDialogObserver"

/**
 * Binds the AndroidPromptModel to NFC reader mode.
 *
 * This provides the UI binding that multipaz requires for NFC scanning.
 * When the scanNfcPromptModel emits DialogShownState, this enables NFC reader mode.
 * When DialogNoState is emitted, it disables reader mode after linger.
 */
internal suspend fun observeNfcDialogAndEnableReaderMode(
    activity: Activity,
    promptModel: AndroidPromptModel
) {
    Logger.i(TAG, "Starting NFC dialog observer and reader mode binding")
    // The bindNfcPromptModelToReaderMode function will collect dialog states continuously
    bindNfcPromptModelToReaderMode(activity, promptModel)
}
