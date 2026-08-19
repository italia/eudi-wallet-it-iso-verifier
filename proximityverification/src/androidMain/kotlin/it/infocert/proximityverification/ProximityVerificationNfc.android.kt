package it.infocert.proximityverification

import android.app.Activity
import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.handlers.DispatcherProvider
import it.infocert.proximityverification.core.handlers.NfcChecker
import it.infocert.proximityverification.core.models.engagement.EngagementResult
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.nfc.observeNfcDialogAndEnableReaderMode
import it.infocert.proximityverification.core.transport.ConnectionMethodSelector
import it.infocert.proximityverification.core.transport.HandoverBuilder
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.exceptions.VerificationException
import it.infocert.proximityverification.lifecycle.VerificationStep
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.nfc.NfcTagReader
import org.multipaz.mdoc.nfc.scanMdocReader
import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.prompt.PromptDismissedException
import org.multipaz.util.Platform

private const val TAG = "ProximityVerificationNfc"

private suspend fun scanNfcEngagementInternal(
    activity: Any?,
    settings: ProximityVerificationSettings,
    callback: VerificationCallback?
): EngagementResult {
    val androidActivity = activity as? Activity
        ?: throw IllegalArgumentException("Activity parameter must be an Android Activity")
    val mn = "scanNfcEngagement"
    val nfcAvailable = NfcChecker.getNfcAvailability()
    if (!nfcAvailable) {
        Logger.error(TAG) { "$mn - NFC hardware not available on this device" }
        throw ProximityVerificationException(
            "NFC hardware not available on this device",
            code = ProximityVerificationExceptionCode.PVE24()
        )
    }
    val nfcEnabled = NfcChecker.isNfcEnabled()
    if (!nfcEnabled) {
        Logger.error(TAG) { "$mn - NFC is disabled. Please enable NFC in settings" }
        throw ProximityVerificationException(
            "NFC is disabled. Please enable NFC in settings",
            code = ProximityVerificationExceptionCode.PVE25()
        )
    }
    val advertisedConnectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)
    Logger.info(TAG) {
        "NFC engagement will advertise ${advertisedConnectionMethods.size} connection methods: ${advertisedConnectionMethods.joinToString { it.toString() }}"
    }
    val transportOptions = MdocTransportOptions(
        bleUseL2CAP = settings.bleUseL2CAP,
        bleUseL2CAPInEngagement = settings.bleUseL2CAPInEngagement
    )
    val effectiveCallback = callback ?: object : VerificationCallback {
        override fun onVerificationUpdate(step: VerificationStep) {}
        override fun onVerificationCompleted(outcome: it.infocert.proximityverification.core.models.VerificationOutcome) {}
    }
    var capturedEnabledMethods: List<MdocConnectionMethod> = emptyList()
    val androidPromptModel = Platform.promptModel
    effectiveCallback.onVerificationUpdate(VerificationStep.NfcScanningStarted(settings.nfcScanMessage))
    Logger.debug(TAG) { "Starting NFC scan with activity-scoped PromptModel" }
    // Run inside withContext (structured concurrency): a failure here — e.g. a cancelled/failed
    // engagement scan throwing PVE26 — is rethrown to the caller and caught by launchVerification,
    // then dispatched to the callback. A free-standing CoroutineScope(...).async{}.await() would
    // ALSO propagate the failure up the shared parent Job as an unhandled exception, crashing the
    // process. withContext(androidPromptModel) keeps the reader-mode dialog observer on the same
    // prompt model while rethrowing cleanly.
    return withContext(androidPromptModel) {
        val observerJob = async {
            observeNfcDialogAndEnableReaderMode(
                androidActivity,
                androidPromptModel as AndroidPromptModel
            )
        }
        delay(100)
        Logger.debug(TAG) { "Observer started, proceeding with NFC scan" }
        try {
            val reader = NfcTagReader.getReaders().first()
            val scanResult = reader.scanMdocReader(
                message = settings.nfcScanMessage,
                options = transportOptions,
                transportFactory = MdocTransportFactory.Default,
                selectConnectionMethod = { connectionMethods ->
                    Logger.debug(TAG) { "selectConnectionMethod received ${connectionMethods.size} connection methods: ${connectionMethods.joinToString { it.toString() }}" }
                    effectiveCallback.onVerificationUpdate(VerificationStep.NfcTagDetected)
                    effectiveCallback.onVerificationUpdate(VerificationStep.NfcEngagementReading)
                    val enabledMethods = ConnectionMethodSelector.filterBySettings(
                        connectionMethods = connectionMethods,
                        settings = settings
                    )
                    capturedEnabledMethods = ConnectionMethodSelector.requireNonEmpty(enabledMethods, connectionMethods)
                    val selected = ConnectionMethodSelector.select(
                        connectionMethods = enabledMethods,
                        preferredTransport = settings.preferredTransport
                    )
                    Logger.debug(TAG) { "selectConnectionMethod selected: $selected (preferred: ${settings.preferredTransport})" }
                    selected
                },
                negotiatedHandoverConnectionMethods = advertisedConnectionMethods
            )
            Logger.debug(TAG) { "scanNfcMdocReader returned: scanResult=$scanResult" }
            if (scanResult == null) {
                Logger.warn(TAG) { "scanNfcMdocReader returned null - scan was cancelled or failed" }
                throw ProximityVerificationException(
                    "NFC engagement scan was cancelled",
                    code = ProximityVerificationExceptionCode.PVE26()
                )
            }
            Logger.debug(TAG) { "scanResult.transport=${scanResult.transport}, connectionMethod=${scanResult.transport.connectionMethod}" }
            effectiveCallback.onVerificationUpdate(VerificationStep.NfcEngagementCompleted)
            EngagementResult.fromNfcScan(
                encodedDeviceEngagement = scanResult.encodedDeviceEngagement.toByteArray(),
                handover = scanResult.handover,
                reusableTransport = scanResult.transport,
                enabledConnectionMethods = capturedEnabledMethods
            )
        } finally {
            observerJob.cancel()
        }
    }
}

// ── Policy-Driven NFC API ─────────────────────────────────────────

actual fun ProximityVerification.startVerificationWithNfc(
    settings: ProximityVerificationSettings,
    callback: VerificationCallback,
    activity: Any?
) {
    val mn = "startVerificationWithNfc"
    if (settings.requests.isEmpty()) {
        val error = VerificationException(
            "ProximityVerificationSettings.requests cannot be empty",
            code = ProximityVerificationExceptionCode.PVE07()
        )
        Logger.error(TAG) { "$mn - ${error.message}" }
        ProximityVerification.dispatchProtocolFailureOnCallbackDispatcher(mn, callback, error)
        return
    }
    ProximityVerification.launchVerification(mn, callback) {
        val resolvedSettings = resolveUsableTransportSettings(settings)
        val engagement: EngagementResult = scanNfcEngagementInternal(
            activity = activity,
            settings = resolvedSettings,
            callback = callback
        )
        ProximityVerification.executeVerificationFlow(
            mn = mn,
            engagement = engagement,
            settings = resolvedSettings,
            callback = callback,
            platformContext = activity
        )
    }
}

internal actual suspend fun executeWithNfcDataTransferTag(
    platformContext: Any?,
    transport: NfcTransportMdocReader,
    scanMessage: String,
    dispatcherProvider: DispatcherProvider,
    callback: VerificationCallback,
    block: suspend () -> Unit
): Unit {
    val activity: Activity = platformContext as? Activity
        ?: throw IllegalArgumentException("Activity parameter must be an Android Activity")
    withContext(dispatcherProvider.callbackDispatcher) {
        callback.onVerificationUpdate(VerificationStep.NfcScanningStarted(scanMessage))
    }
    val androidPromptModel = Platform.promptModel as AndroidPromptModel
    // Structured concurrency (see scanNfcEngagementInternal): withContext rethrows a failure
    // (e.g. PVE29 on a dismissed prompt) to the caller for graceful dispatch, instead of leaking
    // it up the shared parent Job and crashing the process.
    withContext(androidPromptModel) {
        val observerJob = async {
            observeNfcDialogAndEnableReaderMode(activity, androidPromptModel)
        }
        delay(100)
        try {
            val reader = NfcTagReader.getReaders().first()
            reader.scan(
                message = scanMessage,
                tagInteractionFunc = { tag ->
                    transport.setTag(tag)
                    // Intentional: nfcTransferMessage covers both "about to start" and "active" phases (see design doc §5.3)
                    transport.updateDialogMessage(scanMessage)
                    block()
                    Unit
                }
            )
        } catch (_: PromptDismissedException) {
            throw ProximityVerificationException(
                "NFC data transfer scan was cancelled",
                code = ProximityVerificationExceptionCode.PVE29()
            )
        } finally {
            observerJob.cancel()
        }
    }
}

@Throws(ProximityVerificationException::class, CancellationException::class)
actual fun ProximityVerification.resetNfcState(activity: Any?) {
    if (activity == null) {
        return
    }
    try {
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(activity as Activity)
        adapter?.disableReaderMode(activity)
    } catch (_: Throwable) {}
    Logger.debug(TAG) {
        "resetNfcState - cancel active verification (verificationGen=${ProximityVerification.getActiveVerificationGeneration() ?: "none"})"
    }
    ProximityVerification.cancelActiveVerification()
}
