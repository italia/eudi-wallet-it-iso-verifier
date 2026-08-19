package it.infocert.proximityverification

import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.handlers.DispatcherProvider
import it.infocert.proximityverification.core.handlers.NfcChecker
import it.infocert.proximityverification.core.models.engagement.EngagementResult
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.transport.ConnectionMethodSelector
import it.infocert.proximityverification.core.transport.HandoverBuilder
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.exceptions.VerificationException
import it.infocert.proximityverification.lifecycle.VerificationStep
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.mdoc.nfc.scanMdocReader
import org.multipaz.nfc.NfcTagReader
import org.multipaz.prompt.PromptDismissedException
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState

private const val TAG = "ProximityVerificationNfc"
private const val USER_SHEET_CLOSED_CANCELLATION_REASON = "IOS_NFC_USER_SHEET_CLOSED"
private const val USER_CANCELLED_NFC_SCAN_MESSAGE = "User cancelled NFC scan (sheet closed)."

private suspend fun scanNfcEngagementInternal(
    settings: ProximityVerificationSettings,
    callback: VerificationCallback?,
    dispatcherProvider: DispatcherProvider
): EngagementResult {
    val mn = "scanNfcEngagement"
    val nfcAvailable = NfcChecker.getNfcAvailability()
    if (!nfcAvailable) {
        Logger.error(TAG) { "$mn - NFC hardware not available on this device" }
        throw ProximityVerificationException(
            "NFC hardware not available on this device",
            code = ProximityVerificationExceptionCode.PVE24()
        )
    }
    val advertisedConnectionMethods = HandoverBuilder.buildNegotiatedHandoverConnectionMethods(settings)
    val transportOptions = MdocTransportOptions(
        bleUseL2CAP = settings.bleUseL2CAP,
        bleUseL2CAPInEngagement = settings.bleUseL2CAPInEngagement
    )
    val effectiveCallback = callback ?: object : VerificationCallback {
        override fun onVerificationUpdate(step: VerificationStep) {}
        override fun onVerificationCompleted(outcome: it.infocert.proximityverification.core.models.VerificationOutcome) {}
    }
    var capturedEnabledMethods: List<MdocConnectionMethod> = emptyList()
    var lastResult: EngagementResult? = null
    val callbackScope: CoroutineScope =
        CoroutineScope(currentCoroutineContext() + dispatcherProvider.callbackDispatcher)
    val pendingProgressJobs: MutableList<Job> = mutableListOf()
    val sessionMonitor = NfcSessionMonitor()
    try {
        executeDispatchVerificationUpdate(
            dispatcherProvider = dispatcherProvider,
            callback = effectiveCallback,
            step = VerificationStep.NfcScanningStarted(settings.nfcScanMessage)
        )
        Logger.debug(TAG) { "NFC scanning started callback invoked" }
        val parentJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        val verificationScope = CoroutineScope(currentCoroutineContext())
        sessionMonitor.startMonitoring(
            scope = verificationScope,
            parentJob = parentJob,
            verificationGeneration = ProximityVerification.getActiveVerificationGeneration()
        ) { sessionClosed ->
            if (sessionClosed) {
                Logger.warn(TAG) { "NFC session closed by user, cancelling scan..." }
                parentJob?.cancel(CancellationException(USER_SHEET_CLOSED_CANCELLATION_REASON))
            }
        }
        val reader = NfcTagReader.getReaders().first()
        val scanResult = reader.scanMdocReader(
            message = settings.nfcScanMessage,
            options = transportOptions,
            selectConnectionMethod = { connectionMethods ->
                Logger.debug(TAG) { "Available connection methods: $connectionMethods" }
                val progressJob = callbackScope.launch {
                    effectiveCallback.onVerificationUpdate(VerificationStep.NfcTagDetected)
                    effectiveCallback.onVerificationUpdate(VerificationStep.NfcEngagementReading)
                }
                pendingProgressJobs.add(progressJob)
                val enabledMethods = ConnectionMethodSelector.filterBySettings(
                    connectionMethods = connectionMethods,
                    settings = settings
                )
                capturedEnabledMethods = ConnectionMethodSelector.requireNonEmpty(enabledMethods, connectionMethods)
                val selected = ConnectionMethodSelector.select(
                    connectionMethods = enabledMethods,
                    preferredTransport = settings.preferredTransport
                )
                Logger.debug(TAG) { "Selected connection method: $selected (preferred: ${settings.preferredTransport})" }
                selected
            },
            negotiatedHandoverConnectionMethods = advertisedConnectionMethods
        )
        if (scanResult == null) {
            Logger.warn(TAG) { "$mn - NFC scan returned null (likely user cancelled)." }
            sessionMonitor.stopMonitoring()
            currentCoroutineContext().ensureActive()
            throw ProximityVerificationException(
                USER_CANCELLED_NFC_SCAN_MESSAGE,
                code = ProximityVerificationExceptionCode.PVE29()
            )
        }
        sessionMonitor.stopMonitoring()
        pendingProgressJobs.forEach { progressJob -> progressJob.join() }
        pendingProgressJobs.clear()
        executeDispatchVerificationUpdate(
            dispatcherProvider = dispatcherProvider,
            callback = effectiveCallback,
            step = VerificationStep.NfcEngagementCompleted
        )
        Logger.info(TAG) { "NFC engagement successful, connection method: ${scanResult.transport.connectionMethod}" }
        val result: EngagementResult = EngagementResult.fromNfcScan(
            encodedDeviceEngagement = scanResult.encodedDeviceEngagement.toByteArray(),
            handover = scanResult.handover,
            reusableTransport = scanResult.transport,
            enabledConnectionMethods = capturedEnabledMethods
        )
        lastResult = result
        return result
    } catch (e: ProximityVerificationException) {
        throw e
    } catch (e: CancellationException) {
        sessionMonitor.stopMonitoring()
        if (isUserSheetClosedCancellation(e)) {
            Logger.warn(TAG) { "$mn - NFC scan cancelled by user (sheet closed)." }
            throw ProximityVerificationException(
                USER_CANCELLED_NFC_SCAN_MESSAGE,
                code = ProximityVerificationExceptionCode.PVE29()
            )
        }
        Logger.debug(TAG) { "$mn - NFC scan cancelled by lifecycle: ${e.message}" }
        throw e
    } catch (e: Exception) {
        Logger.error(TAG) { "NFC engagement failed: ${e.message}" }
        if (e !is ProximityVerificationException && e !is CancellationException) {
            Logger.error(TAG) {
                "Unexpected throwable escaping NFC flow: ${e::class.simpleName}: ${e.message}"
            }
            throw ProximityVerificationException(
                "Unexpected NFC error: ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE26()
            )
        }
        when (classifyIosNfcError(e)) {
            IosNfcErrorType.Benign -> {
                Logger.info(TAG) { "$mn - benign NFC termination (Code=201). Reusing last valid engagement result." }
                return lastResult ?: throw ProximityVerificationException(
                    "Benign NFC termination but no engagement result available",
                    e,
                    code = ProximityVerificationExceptionCode.PVE26()
                )
            }
            IosNfcErrorType.UserCancelled -> {
                Logger.warn(TAG) { "User cancelled NFC scan." }
                throw ProximityVerificationException(
                    USER_CANCELLED_NFC_SCAN_MESSAGE,
                    code = ProximityVerificationExceptionCode.PVE29()
                )
            }
            IosNfcErrorType.RealError -> {
                Logger.error(TAG) { "NFC engagement failed: ${e.message}" }
                throw ProximityVerificationException(
                    "NFC engagement failed: ${e.message}",
                    e,
                    code = ProximityVerificationExceptionCode.PVE26()
                )
            }
        }
    } catch (e: Error) {
        Logger.error(TAG) { "$mn - NFC engagement system error (kotlin.Error): ${e.message}" }
        throw ProximityVerificationException(
            "NFC system error: ${e.message}",
            e,
            code = ProximityVerificationExceptionCode.PVE26()
        )
    } finally {
        sessionMonitor.stopMonitoring()
        pendingProgressJobs.forEach { progressJob -> progressJob.cancel() }
        pendingProgressJobs.clear()
    }
}

private suspend fun isAppInForegroundSafe(): Boolean =
    withContext(Dispatchers.Main.immediate) {
        UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    }

private suspend fun executeDispatchVerificationUpdate(
    dispatcherProvider: DispatcherProvider,
    callback: VerificationCallback,
    step: VerificationStep
): Unit {
    withContext(dispatcherProvider.callbackDispatcher) {
        callback.onVerificationUpdate(step)
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
    val dpProvider: DispatcherProvider = ProximityVerification.getDispatcherProvider()
    ProximityVerification.launchVerification(mn, callback) {
        val resolvedSettings = resolveUsableTransportSettings(settings)
        val engagement: EngagementResult = scanNfcEngagementInternal(
            settings = resolvedSettings,
            callback = callback,
            dispatcherProvider = dpProvider
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
    val mn = "executeWithNfcDataTransferTag"
    var hasCompletedTransfer = false
    val sessionMonitor = NfcSessionMonitor()
    try {
        executeDispatchVerificationUpdate(
            dispatcherProvider = dispatcherProvider,
            callback = callback,
            step = VerificationStep.NfcScanningStarted(scanMessage)
        )
        val parentJob = currentCoroutineContext()[Job]
        val verificationScope = CoroutineScope(currentCoroutineContext())
        sessionMonitor.startMonitoring(
            scope = verificationScope,
            parentJob = parentJob,
            verificationGeneration = ProximityVerification.getActiveVerificationGeneration()
        ) { sessionClosed ->
            if (sessionClosed) {
                Logger.warn(TAG) { "NFC session closed by user, cancelling data transfer scan..." }
                parentJob?.cancel(CancellationException(USER_SHEET_CLOSED_CANCELLATION_REASON))
            }
        }
        val reader = NfcTagReader.getReaders().first()
        reader.scan(
            message = scanMessage,
            tagInteractionFunc = { tag ->
                transport.setTag(tag)
                // Intentional: nfcTransferMessage covers both "about to start" and "active" phases (see design doc §5.3)
                transport.updateDialogMessage(scanMessage)
                block()
                hasCompletedTransfer = true
                Unit
            }
        )
        sessionMonitor.stopMonitoring()
    } catch (_: PromptDismissedException) {
        sessionMonitor.stopMonitoring()
        throw ProximityVerificationException(
            USER_CANCELLED_NFC_SCAN_MESSAGE,
            code = ProximityVerificationExceptionCode.PVE29()
        )
    } catch (e: CancellationException) {
        sessionMonitor.stopMonitoring()
        if (isUserSheetClosedCancellation(e)) {
            Logger.warn(TAG) { "$mn - NFC data transfer scan cancelled by user (sheet closed)." }
            throw ProximityVerificationException(
                USER_CANCELLED_NFC_SCAN_MESSAGE,
                code = ProximityVerificationExceptionCode.PVE29()
            )
        }
        Logger.debug(TAG) { "$mn - NFC data transfer scan cancelled by lifecycle: ${e.message}" }
        throw e
    } catch (e: Exception) {
        sessionMonitor.stopMonitoring()
        when (classifyIosNfcError(e)) {
            IosNfcErrorType.Benign -> {
                if (hasCompletedTransfer) {
                    Logger.info(TAG) { "$mn - benign NFC termination after completed transfer." }
                    return
                }
                throw ProximityVerificationException(
                    "Benign NFC termination before data transfer completed",
                    e,
                    code = ProximityVerificationExceptionCode.PVE26()
                )
            }
            IosNfcErrorType.UserCancelled -> {
                Logger.warn(TAG) { "$mn - User cancelled NFC data transfer scan." }
                throw ProximityVerificationException(
                    USER_CANCELLED_NFC_SCAN_MESSAGE,
                    code = ProximityVerificationExceptionCode.PVE29()
                )
            }
            IosNfcErrorType.RealError -> {
                Logger.error(TAG) { "$mn - NFC data transfer scan failed: ${e.message}" }
                throw ProximityVerificationException(
                    "NFC data transfer scan failed: ${e.message}",
                    e,
                    code = ProximityVerificationExceptionCode.PVE26()
                )
            }
        }
    } catch (e: Error) {
        sessionMonitor.stopMonitoring()
        Logger.error(TAG) { "$mn - NFC data transfer system error (kotlin.Error): ${e.message}" }
        throw ProximityVerificationException(
            "NFC system error: ${e.message}",
            e,
            code = ProximityVerificationExceptionCode.PVE26()
        )
    } finally {
        sessionMonitor.stopMonitoring()
    }
}

@Throws(ProximityVerificationException::class, CancellationException::class)
actual fun ProximityVerification.resetNfcState(activity: Any?) {}

// ── iOS-specific helpers ─────────────────────────────────────────

private class NfcSessionMonitor {
    private var monitoringJob: Job? = null
    private var onSessionClosed: ((Boolean) -> Unit)? = null
    private var monitoringVerificationGeneration: Long? = null

    fun startMonitoring(
        scope: CoroutineScope,
        parentJob: Job?,
        verificationGeneration: Long?,
        onClosed: (Boolean) -> Unit
    ) {
        onSessionClosed = onClosed
        monitoringVerificationGeneration = verificationGeneration
        Logger.debug(TAG) {
            "SessionMonitor - monitor start (verificationGen=${verificationGeneration ?: "none"})"
        }
        monitoringJob = scope.launch(Dispatchers.Default) {
            var sheetOpened = false
            var foregroundTicks = 0
            while (isActive) {
                delay(300)
                val isForeground = isAppInForegroundSafe()
                if (!sheetOpened) {
                    if (!isForeground) {
                        sheetOpened = true
                        foregroundTicks = 0
                    }
                } else {
                    if (isForeground) {
                        foregroundTicks++
                        if (foregroundTicks >= 2) {
                            Logger.debug(TAG) { "SessionMonitor: detected sheet closed" }
                            onSessionClosed?.invoke(true)
                            break
                        }
                    } else {
                        foregroundTicks = 0
                    }
                }
            }
        }
    }

    fun stopMonitoring() {
        Logger.debug(TAG) {
            "SessionMonitor - monitor stop (verificationGen=${monitoringVerificationGeneration ?: "none"})"
        }
        monitoringJob?.cancel()
        monitoringJob = null
        onSessionClosed = null
        monitoringVerificationGeneration = null
    }
}

private enum class IosNfcErrorType {
    Benign,
    UserCancelled,
    RealError
}

private fun isUserSheetClosedCancellation(exception: CancellationException): Boolean {
    if (exception.message == USER_SHEET_CLOSED_CANCELLATION_REASON) {
        return true
    }
    var cause: Throwable? = exception.cause
    while (cause != null) {
        if (cause is CancellationException && cause.message == USER_SHEET_CLOSED_CANCELLATION_REASON) {
            return true
        }
        cause = cause.cause
    }
    return false
}

private fun classifyIosNfcError(e: Throwable): IosNfcErrorType {
    val msg = e.message?.lowercase() ?: return IosNfcErrorType.RealError
    if (
        msg.contains("firstndef") ||
        msg.contains("code=201") ||
        msg.contains("nfcerror domain") && msg.contains("201") ||
        msg.contains("session invalidated unexpectedly") ||
        (msg.contains("com.apple.nfc") && msg.contains("201"))
    ) {
        return IosNfcErrorType.Benign
    }
    if (
        msg.contains("user canceled") ||
        msg.contains("user cancelled") ||
        msg.contains("code=200") ||
        msg.contains("session invalidated by user")
    ) {
        return IosNfcErrorType.UserCancelled
    }
    return IosNfcErrorType.RealError
}
