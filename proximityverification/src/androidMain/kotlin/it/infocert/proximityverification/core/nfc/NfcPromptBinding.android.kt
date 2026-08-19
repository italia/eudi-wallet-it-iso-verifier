package it.infocert.proximityverification.core.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.multipaz.nfc.NfcIsoTag
import org.multipaz.nfc.NfcIsoTagAndroid
import org.multipaz.nfc.NfcTagLostException
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.prompt.PromptDialogModel
import org.multipaz.prompt.PromptDismissedException
import org.multipaz.prompt.ScanNfcPromptDialogModel
import org.multipaz.util.Logger
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private const val TAG = "NfcPromptBinding"

/**
 * Provides UI binding for AndroidPromptModel.scanNfcPromptModel.
 * Based on multipaz-compose's ScanNfcTagPromptDialog implementation.
 *
 * This observes the dialog state and enables/disables NFC reader mode accordingly.
 * The app must call this to provide the necessary UI binding for NFC scanning.
 */
internal suspend fun bindNfcPromptModelToReaderMode(
    activity: Activity,
    promptModel: AndroidPromptModel
) {
    Logger.i(TAG, "Starting NFC prompt model binding")

    // Get the NFC dialog model from the prompt model
    val nfcDialogModel = promptModel.getDialogModel(ScanNfcPromptDialogModel.DialogType)

    nfcDialogModel.dialogState.collect { state ->
        when (state) {
            is PromptDialogModel.NoDialogState<*, *> -> {
                if (!state.initial) {
                    Logger.d(TAG, "NoDialogState observed; reader mode left active for caller to manage")
                }
            }

            is PromptDialogModel.DialogShownState<*, *> -> {
                Logger.i(TAG, "NFC dialog shown, enabling reader mode")

                @Suppress("UNCHECKED_CAST")
                val dialogState = state as PromptDialogModel.DialogShownState<ScanNfcPromptDialogModel.NfcDialogParameters<Any>, Any?>

                val adapter = NfcAdapter.getDefaultAdapter(activity)
                if (adapter == null) {
                    Logger.e(TAG, "NFC adapter not available")
                    dialogState.resultChannel.close(
                        IllegalStateException("NFC is not supported on this device")
                    )
                    return@collect
                }

                try {
                    val result = suspendCancellableCoroutine { continuation ->
                        val nfcCallback = NfcReaderCallback(
                            initialMessage = dialogState.parameters.initialMessage,
                            tagInteractionFunc = dialogState.parameters.interactionFunction,
                            continuation = continuation,
                            context = dialogState.parameters.context
                        )

                        // Enable reader mode with appropriate flags
                        val flags = NfcAdapter.FLAG_READER_NFC_A or
                                NfcAdapter.FLAG_READER_NFC_B or
                                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

                        try {
                            adapter.enableReaderMode(
                                activity,
                                nfcCallback,
                                flags,
                                null
                            )
                            Logger.i(TAG, "NFC reader mode enabled successfully")
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to enable NFC reader mode", e)
                            continuation.resumeWithException(e)
                        }
                    }

                    // Send result back to dialog
                    dialogState.resultChannel.send(result)
                    Logger.i(TAG, "NFC scan completed successfully")

                } catch (e: CancellationException) {
                    Logger.i(TAG, "NFC scan cancelled")
                    dialogState.resultChannel.close(PromptDismissedException())
                    throw e
                } catch (e: Throwable) {
                    Logger.e(TAG, "Error during NFC scan", e)
                    dialogState.resultChannel.close(e)
                }
            }
        }
    }
}

/**
 * NFC Reader Callback that handles tag discovery and interaction.
 * Based on multipaz-compose's NfcReaderCallback implementation.
 *
 * BLOCKING CONTRACT: [onTagDiscovered] uses [runBlocking] to bridge the synchronous
 * Android NFC callback to the suspend [tagInteractionFunc]. This is required because:
 * - The Android NFC API calls onTagDiscovered() on a dedicated binder thread
 * - The IsoDep connection is only valid while the callback is executing
 * - Returning before tag interaction completes closes the connection
 *
 * The runBlocking scope is intentionally disconnected from the library session scope.
 * If the session is cancelled during tag interaction, the APDU exchange completes
 * naturally and the result is discarded at the suspendCancellableCoroutine boundary.
 * This preserves NFC protocol integrity and matches the multipaz upstream pattern.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private class NfcReaderCallback<T>(
    val initialMessage: String?,
    val tagInteractionFunc: suspend (tag: NfcIsoTag) -> T?,
    val continuation: kotlinx.coroutines.CancellableContinuation<T>,
    val context: kotlin.coroutines.CoroutineContext
) : NfcAdapter.ReaderCallback {

    @Volatile
    private var isProcessingTag = false

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) {
            Logger.e(TAG, "onTagDiscovered called with null tag")
            return
        }
        
        // Prevent processing multiple tags if we're already processing one or continuation is already completed
        if (isProcessingTag || !continuation.isActive) {
            Logger.d(TAG, "Already processing a tag or continuation completed, ignoring this one")
            return
        }
        
        isProcessingTag = true

        // Look for IsoDep technology
        for (tech in tag.techList) {
            if (tech == IsoDep::class.java.name) {
                val isoDep = IsoDep.get(tag)
                if (isoDep == null) {
                    Logger.e(TAG, "IsoDep.get() returned null")
                    continue
                }

                isoDep.connect()
                isoDep.timeout = 20.seconds.inWholeMilliseconds.toInt()

                var tagConsumed = false
                try {
                    // Android NFC contract: block this binder thread until tag interaction
                    // completes. The IsoDep handle is invalid after onTagDiscovered returns.
                    // Cancellation isolation is accepted — see class KDoc.
                    val ret = runBlocking {
                        Logger.i(TAG, "maxTransceiveLength: ${isoDep.maxTransceiveLength}")
                        val isoTag = NfcIsoTagAndroid(
                            tag = isoDep,
                            context = context,
                            updateMessage = { message ->
                                Logger.i(TAG, "NFC dialog message: $message")
                            }
                        )
                        tagInteractionFunc(isoTag)
                    }

                    Logger.i(TAG, "Tag processed")
                    if (ret == null) {
                        Logger.d(TAG, "Tag interaction returned null, continuing to listen")
                    } else {
                        // Tag deferred for later use (e.g. NFC data transfer).
                        // Do NOT close IsoDep — the transport owns it now.
                        tagConsumed = true
                        continuation.resume(ret, null)
                    }

                } catch (e: NfcTagLostException) {
                    Logger.w(TAG, "Tag lost during interaction", e)
                    isProcessingTag = false
                } catch (e: java.io.IOException) {
                    Logger.w(TAG, "NFC I/O error during interaction (will retry): ${e.message}", e)
                    isProcessingTag = false
                } catch (e: Throwable) {
                    Logger.e(TAG, "Fatal error in tag interaction", e)
                    continuation.resumeWithException(e)
                } finally {
                    if (!tagConsumed) {
                        try {
                            isoDep.close()
                        } catch (e: Exception) {
                            Logger.w(TAG, "Error closing IsoDep", e)
                        }
                    }
                }
                return
            }
        }

        Logger.w(TAG, "No IsoDep technology found on tag")
        // Reset flag if no valid tech was found
        isProcessingTag = false
    }
}
