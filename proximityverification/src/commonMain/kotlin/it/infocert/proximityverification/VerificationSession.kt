package it.infocert.proximityverification

import it.infocert.proximityverification.VerificationRequest
import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.catalog.CatalogBridge
import it.infocert.proximityverification.core.handlers.BluetoothChecker
import it.infocert.proximityverification.core.handlers.DispatcherProvider
import it.infocert.proximityverification.core.handlers.NfcChecker
import it.infocert.proximityverification.core.models.SessionInfo
import it.infocert.proximityverification.core.models.SessionTiming
import it.infocert.proximityverification.core.models.VerificationOutcome
import it.infocert.proximityverification.core.models.document.DocumentData
import it.infocert.proximityverification.core.models.document.DocumentVerificationResult
import it.infocert.proximityverification.core.models.engagement.EngagementType
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.models.request.RequestOutcome
import it.infocert.proximityverification.core.reconciliation.RequestReconciler
import it.infocert.proximityverification.core.reconciliation.ReturnedDocumentFacts
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.lifecycle.VerificationStep
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.core.crypto.CrlChecker
import it.infocert.proximityverification.status.StatusListStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.engagement.DeviceEngagement
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.mdoc.response.MdocDocument
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransportClosedException
import org.multipaz.mdoc.transport.MdocTransportException
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.util.Constants
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.crypto.AsymmetricKey

/**
 * Validates runtime transport availability against [settings] and returns a
 * settings snapshot that reflects only the transports actually usable on this device.
 *
 * - BLE-only, BLE unavailable → throws PVE09.
 * - NFC-only, NFC hardware absent → throws PVE24.
 * - NFC-only, NFC disabled → throws PVE25.
 * - Both configured, both unavailable → throws PVE09.
 * - Both configured, BLE unavailable, NFC available → returns copy with BLE flags disabled.
 * - Both configured, NFC unavailable, BLE available → returns copy with nfcEnabled = false.
 * - Both configured, both available → returns [effectiveConfig] unchanged.
 * - Neither configured → returns [effectiveConfig] unchanged.
 *
 * NFC availability flags are read once and reused across all branches to avoid redundant calls.
 * Checker lambdas are injectable for testing; production callers use the defaults.
 *
 * The returned settings snapshot must be the sole source of truth for all
 * subsequent transport decisions.
 */
internal suspend fun resolveUsableTransportSettings(
    settings: ProximityVerificationSettings,
    bluetoothChecker: suspend () -> Boolean = { BluetoothChecker.getBluetoothAvailability() },
    nfcAvailabilityChecker: () -> Boolean = { NfcChecker.getNfcAvailability() },
    nfcEnabledChecker: () -> Boolean = { NfcChecker.isNfcEnabled() }
): ProximityVerificationSettings {
    val tag = "resolveUsableTransportSettings"
    val isBleConfigured: Boolean = settings.isBlePeripheralServerModeEnabled()
        || settings.isBleCentralClientModeEnabled()
    val isNfcConfigured: Boolean = settings.nfcDataTransferEnabled
    val nfcHardwarePresent: Boolean = if (isNfcConfigured) nfcAvailabilityChecker() else false
    val nfcIsEnabled: Boolean = if (isNfcConfigured) nfcEnabledChecker() else false
    val bleAvailable: Boolean = if (isBleConfigured) bluetoothChecker() else false
    val nfcAvailable: Boolean = isNfcConfigured && nfcHardwarePresent && nfcIsEnabled
    return when {
        isBleConfigured && !isNfcConfigured && !bleAvailable -> throw ProximityVerificationException(
            "Bluetooth not available. Enable Bluetooth before starting verification.",
            code = ProximityVerificationExceptionCode.PVE09()
        )
        isNfcConfigured && !isBleConfigured && !nfcHardwarePresent -> throw ProximityVerificationException(
            "NFC hardware not available on this device.",
            code = ProximityVerificationExceptionCode.PVE24()
        )
        isNfcConfigured && !isBleConfigured && !nfcIsEnabled -> throw ProximityVerificationException(
            "NFC is disabled. Enable NFC before starting verification.",
            code = ProximityVerificationExceptionCode.PVE25()
        )
        isBleConfigured && isNfcConfigured && !bleAvailable && !nfcAvailable -> throw ProximityVerificationException(
            "Neither Bluetooth nor NFC is available. Enable at least one transport before starting verification.",
            code = ProximityVerificationExceptionCode.PVE09()
        )
        isBleConfigured && isNfcConfigured && !bleAvailable -> {
            Logger.warn(tag) { "Bluetooth unavailable; degrading to NFC-only transport" }
            settings.copy(
                bleEnabled = false,
                blePeripheralServerMode = false,
                bleCentralClientMode = false
            )
        }
        isBleConfigured && isNfcConfigured && !nfcAvailable -> {
            Logger.warn(tag) { "NFC unavailable; degrading to BLE-only transport" }
            settings.copy(nfcDataTransferEnabled = false)
        }
        else -> settings
    }
}

data class ReceiveResult(
    val deviceResponse: ByteArray?,
    val reason: TerminationReason,
    val status: Long?,
    val timeOfFirstResponseReceived: Instant?
)

enum class TerminationReason {
    TransportTerminated,
    SessionTerminationReceived,
    AutoCloseSent
}

internal class VerificationSession(
    internal val trustManager: TrustManager?,
    private val documentTypeRepository: DocumentTypeRepository,
    private val certChain: X509CertChain,
    private var dispatcherProvider: DispatcherProvider,
    private val statusListStore: StatusListStore? = null,
    private val crlCheckerProvider: () -> CrlChecker? = { null }
) {
    private val TAG = "VerificationSession"
    
    /**
     * Helper function to dispatch callbacks on the configured callback dispatcher.
     */
    private suspend fun dispatchCallback(block: suspend () -> Unit) {
        withContext(dispatcherProvider.callbackDispatcher) {
            block()
        }
    }

    /**
     * Runs [operation] with a timeout, without parent-trapping child coroutines
     * launched by the operation.
     *
     * [NfcTransportMdocReader.open] launches a long-lived ioJob via
     * `CoroutineScope(currentCoroutineContext()).launch`. When wrapped in a plain
     * `withTimeoutOrNull`, the ioJob becomes a child of the timeout scope and
     * prevents it from completing (structured concurrency waits for all children).
     *
     * This method launches [operation] in a separate coroutine (child of the
     * session scope) and waits only for its *return*, not for its children. The
     * ioJob lives on in the session scope and is cleaned up by [MdocTransport.close].
     */
    private suspend fun executeTransportOperationWithTimeout(
        operationName: String,
        timeout: Duration,
        operation: suspend () -> Unit
    ): Unit {
        val operationReturned = CompletableDeferred<Unit>()
        val operationJob = CoroutineScope(currentCoroutineContext()).launch {
            try {
                operation()
                operationReturned.complete(Unit)
            } catch (e: CancellationException) {
                operationReturned.cancel(e)
                throw e
            } catch (e: Throwable) {
                operationReturned.completeExceptionally(e)
            }
        }
        try {
            withTimeout(timeout) {
                operationReturned.await()
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            operationJob.cancel()
            throw createTransportTimeoutException(operationName, timeout)
        }
    }

    private suspend fun receiveMessagesWithTimeout(
        transport: MdocTransport,
        timeout: Duration,
        sessionEncryption: SessionEncryption,
        allowMultipleRequests: Boolean,
        stepCallback: VerificationCallback?,
        methodName: String
    ): ReceiveResult {
        val receiveResult: ReceiveResult? = withTimeoutOrNull(timeout) {
            receiveMessagesLoop(
                transport = transport,
                sessionEncryption = sessionEncryption,
                allowMultipleRequests = allowMultipleRequests,
                stepCallback = stepCallback
            )
        }
        if (receiveResult != null) {
            return receiveResult
        }
        executeSendTerminationCharacterSafely(
            transport = transport,
            methodName = methodName,
            reason = "receive timeout"
        )
        throw createTransportTimeoutException("receive response", timeout)
    }

    private suspend fun executeSendTerminationCharacterSafely(
        transport: MdocTransport,
        methodName: String,
        reason: String
    ): Unit {
        try {
            Logger.debug(TAG) { "$methodName - Sending termination character due to $reason..." }
            transport.sendMessage(ByteArray(0))
        } catch (e: CancellationException) {
            throw e
        } catch (exception: Exception) {
            Logger.warn(TAG) { "$methodName - Failed to send termination character: ${exception.message}" }
        }
    }

    private fun createTransportTimeoutException(
        operationName: String,
        timeout: Duration
    ): ProximityVerificationException {
        return ProximityVerificationException(
            "Transport operation '$operationName' timed out after ${timeout.inWholeSeconds}s",
            code = ProximityVerificationExceptionCode.PVE30()
        )
    }

    /**
     * Update dispatcher provider used by this session.
     * This keeps callback dispatching consistent after runtime reconfiguration.
     */
    internal fun updateDispatcherProvider(provider: DispatcherProvider): Unit {
        dispatcherProvider = provider
    }

    /**
     * Run verification with pre-resolved transport and pre-parsed engagement.
     * Used by the multi-step API ([ProximityVerification.startVerification] with [EngagementResult]).
     *
     * The caller is responsible for transport selection, engagement parsing, and the
     * [VerificationStep.TransportSelected] callback. This method handles protocol execution only:
     * session encryption, request encoding/sending, response receiving/parsing, and outcome dispatch.
     */
    @OptIn(ExperimentalStdlibApi::class)
    internal suspend fun runVerificationWithResolvedTransport(
        encodedDeviceEngagement: ByteArray,
        parsedEngagement: DeviceEngagement,
        handover: DataItem,
        transport: MdocTransport,
        engagementType: EngagementType,
        requests: List<VerificationRequest>,
        allowMultipleRequests: Boolean,
        settings: ProximityVerificationSettings,
        stepCallback: VerificationCallback?
    ) {
        val mn = "runVerificationWithResolvedTransport"
        Logger.info(TAG) { "$mn - Starting verification session (pre-resolved transport)." }
        dispatchCallback {
            stepCallback?.onVerificationUpdate(VerificationStep.Starting)
        }
        val timeOfEngagementReceived = Clock.System.now()
        val eDeviceKey = parsedEngagement.eDeviceKey
        val eReaderKey = Crypto.createEcPrivateKey(eDeviceKey.curve)
        val encodedSessionTranscript = Utils.generateEncodedSessionTranscript(
            encodedDeviceEngagement,
            handover,
            eReaderKey.publicKey
        )
        val sessionEncryption = SessionEncryption(
            role = MdocRole.MDOC_READER,
            eSelfKey = eReaderKey,
            remotePublicKey = eDeviceKey,
            encodedSessionTranscript = encodedSessionTranscript
        )
        Logger.debug(TAG) { "$mn - Session encryption context initialized." }
        val connectionMethod = transport.connectionMethod
        dispatchCallback {
            stepCallback?.onVerificationUpdate(VerificationStep.Connecting)
        }
        val transcript = encodedSessionTranscript
        Logger.info(TAG) {
            "$mn - Session starting: engagement=$engagementType, connection=$connectionMethod, " +
                "transportTimeout=${settings.transportTimeout.inWholeSeconds}s, " +
                "responseTimeout=${settings.responseTimeout.inWholeSeconds}s"
        }
        VerificationSessionLog.logRequests(requests)
        Logger.debug(TAG) { "$mn - Generating DeviceRequests..." }
        val internalRequests = requests.map { request ->
            CatalogBridge.toCannedRequest(request)
        }
        Logger.debug(TAG) { "$mn - Generated ${internalRequests.size} DeviceRequests." }
        val sessionTranscript: DataItem = RawCbor(transcript)
        val readerKey: AsymmetricKey.X509Compatible = Utils.createReaderAuthKey(certChain)
        val encodedRequest = Utils.generateEncodedDeviceRequest(
            requests = internalRequests,
            sessionTranscript = sessionTranscript,
            readerKey = readerKey
        )
        Logger.debug("$TAG:CBOR") { "$mn - Encoded DeviceRequest (hex): ${encodedRequest.toHexString()}" }
        try {
            val decoded = Cbor.decode(encodedRequest)
            Logger.debug("$TAG:CBOR") { "$mn - Decoded DeviceRequest: $decoded" }
        } catch (e: Exception) {
            Logger.error("$TAG:CBOR") { "$mn - Error decoding DeviceRequest $e" }
        }
        Logger.info("$TAG:VERIFY") { "$mn - Certificate Chain Size: ${certChain.certificates.size}" }
        Logger.info("$TAG:VERIFY") { "$mn - Certificate Subject: ${certChain.certificates.first().subject}" }
        val transcriptHash: String = Crypto.digest(Algorithm.SHA256, transcript).toHexString()
        Logger.info("$TAG:VERIFY") { "$mn - Session Transcript hash: $transcriptHash" }
        try {
            val transportTimeout: Duration = settings.transportTimeout
            val responseTimeout: Duration = settings.responseTimeout
            executeTransportOperationWithTimeout("open transport", transportTimeout) {
                transport.open(eDeviceKey)
            }
            Logger.debug(TAG) { "$mn - Transport opened." }
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.SendingRequest)
            }
            executeTransportOperationWithTimeout("send request", transportTimeout) {
                transport.sendMessage(sessionEncryption.encryptMessage(encodedRequest, null))
            }
            val timeOfFirstMessageSent = Clock.System.now()
            Logger.info(TAG) { "$mn - DeviceRequest sent. Awaiting response (timeout: ${responseTimeout.inWholeSeconds}s)..." }
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.Authenticating)
            }
            val receiveResult = receiveMessagesWithTimeout(
                transport = transport,
                timeout = responseTimeout,
                sessionEncryption = sessionEncryption,
                allowMultipleRequests = allowMultipleRequests,
                stepCallback = stepCallback,
                methodName = mn
            )
            val lastDeviceResponse = receiveResult.deviceResponse
            val terminationReason = receiveResult.reason
            val status = receiveResult.status
            val timeOfFirstResponseReceived = receiveResult.timeOfFirstResponseReceived
            Logger.info(TAG) { "$mn - receiveMessagesLoop exited due to: $terminationReason" }
            if (terminationReason == TerminationReason.TransportTerminated) {
                Logger.info(TAG) { "$mn - Transport terminated." }
                val error = ProximityVerificationException(
                    "Transport terminated unexpectedly",
                    code = ProximityVerificationExceptionCode.PVE07()
                )
                dispatchCallback {
                    stepCallback?.onVerificationUpdate(VerificationStep.Failed)
                    stepCallback?.onVerificationCompleted(
                        ProximityVerification.mapExceptionToOutcome(error)
                    )
                }
                return
            }
            if (terminationReason == TerminationReason.SessionTerminationReceived && lastDeviceResponse == null) {
                Logger.info(TAG) { "$mn - Session termination received without device response." }
                val error = ProximityVerificationException(
                    "mDL ended the session before returning a device response",
                    code = ProximityVerificationExceptionCode.PVE36()
                )
                dispatchCallback {
                    stepCallback?.onVerificationUpdate(VerificationStep.Failed)
                    stepCallback?.onVerificationCompleted(
                        ProximityVerification.mapExceptionToOutcome(error)
                    )
                }
                return
            }
            val parsedDocuments = mutableListOf<DocumentVerificationResult>()
            // Non è un placeholder: se non arriva alcuna response, ogni docType richiesto è ABSENT,
            // che è esattamente ciò che è accaduto.
            var requestOutcome: RequestOutcome =
                RequestReconciler.reconcile(requests, emptyList(), emptyList())
            lastDeviceResponse?.let { resp ->
                Logger.debug(TAG) { "$mn - Parsing device response (${resp.size} bytes)" }
                Logger.debug("$TAG:CBOR") { "$mn - DeviceResponse (hex): ${resp.toHexString()}" }
                val deviceResponseDataItem: DataItem = Cbor.decode(resp)
                Logger.debug("$TAG:CBOR") { "$mn - Decoded DeviceResponse (diagnostic): $deviceResponseDataItem" }
                val verified = verifyDeviceResponseDocuments(
                    root = deviceResponseDataItem,
                    sessionTranscript = RawCbor(transcript),
                    eReaderKey = AsymmetricKey.anonymous(
                        eReaderKey, eReaderKey.curve.defaultKeyAgreementAlgorithm
                    ),
                )
                VerificationSessionLog.logResponse(
                    documentErrors = verified.documentErrors,
                    documents = verified.documents,
                )
                requestOutcome = RequestReconciler.reconcile(
                    requests = requests,
                    documents = verified.documents.map(ReturnedDocumentFacts::from),
                    documentErrors = verified.documentErrors,
                )
                VerificationSessionLog.logRequestOutcome(requestOutcome)
                for (document in verified.documents) {
                    val data = DocumentData.toDocumentVerificationResult(
                        document,
                        documentTypeRepository,
                        trustManager,
                        statusListStore = statusListStore,
                        crlChecker = crlCheckerProvider()
                    )
                    parsedDocuments.add(data)
                    Logger.debug(TAG) { "$mn - Parsed document with ${data.data.size} key-value pairs" }
                }
            } ?: run {
                Logger.warn(TAG) { "$mn - No response received to parse." }
                // Anche qui il riepilogo va emesso: senza response ogni docType richiesto è ABSENT,
                // ed è il caso in cui la riconciliazione dice la cosa più forte.
                VerificationSessionLog.logRequestOutcome(requestOutcome)
            }
            Logger.info(TAG) { "$mn - Verification session completed." }
            val durationEngagementReceivedToRequestSent = timeOfFirstMessageSent - timeOfEngagementReceived
            val durationRequestSentToResponseReceived = if (timeOfFirstResponseReceived != null) {
                timeOfFirstResponseReceived - timeOfFirstMessageSent
            } else {
                Duration.ZERO
            }
            val durationScanningTime = transport.scanningTime
            val outcome = VerificationOutcome.DocumentReceived(
                sessionInfo = SessionInfo(
                    engagementType = engagementType,
                    connectionMethod = connectionMethod.toString(),
                    timing = SessionTiming(
                        nfcTapToEngagement = null,
                        engagementReceivedToRequestSent = durationEngagementReceivedToRequestSent,
                        requestSentToResponseReceived = durationRequestSentToResponseReceived,
                        scanningTime = durationScanningTime
                    ),
                    requestSize = encodedRequest.size.toLong(),
                    responseSize = lastDeviceResponse?.size?.toLong(),
                    deviceResponseStatus = status
                ),
                documents = parsedDocuments,
                requestOutcome = requestOutcome
            )
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.Completed)
                stepCallback?.onVerificationCompleted(outcome)
            }
        } catch (exception: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - Verification failed: ${exception.message}" }
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.Failed)
                stepCallback?.onVerificationCompleted(
                    ProximityVerification.mapExceptionToOutcome(exception)
                )
            }
        } catch (mtce: MdocTransportClosedException) {
            Logger.info(TAG) { "$mn - Transport closed." }
            val error = ProximityVerificationException(
                "Transport closed unexpectedly during verification",
                mtce,
                code = ProximityVerificationExceptionCode.PVE07()
            )
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.Failed)
                stepCallback?.onVerificationCompleted(
                    ProximityVerification.mapExceptionToOutcome(error)
                )
            }
        } catch (e: MdocTransportException) {
            Logger.error(TAG) { "$mn - Transport exception: ${e.message}" }
            executeSendTerminationCharacterSafely(
                transport = transport,
                methodName = mn,
                reason = "transport exception"
            )
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.Failed)
                stepCallback?.onVerificationCompleted(
                    ProximityVerification.mapExceptionToOutcome(
                        ProximityVerificationException(
                            "Transport failure during verification",
                            e,
                            code = ProximityVerificationExceptionCode.PVE07()
                        )
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            Logger.error(TAG) { "$mn - Exception during verification: ${e.message}" }
            dispatchCallback {
                stepCallback?.onVerificationUpdate(VerificationStep.Failed)
                stepCallback?.onVerificationCompleted(
                    ProximityVerification.mapExceptionToOutcome(
                        ProximityVerificationException(
                            "Exception during verification: ${e.message}",
                            e,
                            code = ProximityVerificationExceptionCode.PVE07()
                        )
                    )
                )
            }
        } finally {
            Logger.info(TAG) { "$mn - Finally closing transport." }
            try {
                Logger.debug(TAG) { "$mn - Trying to close transport..." }
                transport.close()
            } catch (e2: Exception) {
                Logger.warn(TAG) { "$mn - Failed to close transport: ${e2.message}" }
            }
        }
    }

    private suspend fun receiveMessagesLoop(
        transport: MdocTransport,
        sessionEncryption: SessionEncryption,
        allowMultipleRequests: Boolean,
        stepCallback: VerificationCallback? = null
    ): ReceiveResult {
        val mn = "VerificationSession"
        var lastDeviceResponse: ByteArray? = null
        var lastStatus: Long? = null
        var timeOfFirstResponseReceived: Instant? = null

        while (true) {
            val sessionData = transport.waitForMessage()

            Logger.debug(TAG) { "$mn - Received message (${sessionData.size} bytes)" }

            if (sessionData.isEmpty()) {
                Logger.info(TAG) { "$mn - Transport-specific termination (empty payload)" }
                transport.close()
                return ReceiveResult(lastDeviceResponse, TerminationReason.TransportTerminated, lastStatus, timeOfFirstResponseReceived)
            }

            Logger.debug(TAG) { "$mn - Decrypting message..." }
            val (message, status) = sessionEncryption.decryptMessage(sessionData)

            // Capture status
            if (status != null) {
                lastStatus = status
            }

            if (message != null) {
                lastDeviceResponse = message
                // Capture timing: first response received
                if (timeOfFirstResponseReceived == null) {
                    timeOfFirstResponseReceived = Clock.System.now()
                }
                Logger.debug(TAG) { "$mn - Decrypted message (${message.size} bytes)" }
                dispatchCallback {
                    stepCallback?.onVerificationUpdate(VerificationStep.Verifying)
                }
            }

            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                Logger.info(TAG) { "$mn - Session termination received." }
                try {
                    transport.sendMessage(ByteArray(0)) //send termination character(STATE_CHARACTERISTIC_END = 0x02)
                } catch (exc: Exception) {
                    Logger.warn(TAG) { "$mn - Failed to send termination character on transport: ${exc.message}" }
                }
                transport.close()
                return ReceiveResult(
                    lastDeviceResponse,
                    TerminationReason.SessionTerminationReceived,
                    lastStatus,
                    timeOfFirstResponseReceived
                )
            }

            if (!allowMultipleRequests) {
                Logger.debug(TAG) { "$mn - Auto-close enabled. Sending termination." }
                //transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
                transport.sendMessage(ByteArray(0)) //send termination character(STATE_CHARACTERISTIC_END = 0x02)
                transport.close()
                return ReceiveResult(lastDeviceResponse, TerminationReason.AutoCloseSent, lastStatus, timeOfFirstResponseReceived)
            }
        }
    }
}

private const val VERIFY_TAG = "DeviceResponseVerification"

/**
 * Esito della verifica d'integrità di una DeviceResponse.
 *
 * Porta anche i `documentErrors` — il canale con cui il wallet dichiara di non possedere un docType —
 * perché sono già parsati qui dentro: leggerli dalla proprietà tipizzata di `DeviceResponse` una volta
 * sola evita che ogni consumatore interno li ri-scavi dal CBOR grezzo, e fa sì che un rename lato
 * multipaz rompa la compilazione invece di far passare in silenzio una lista vuota.
 *
 * Non sono coperti da alcuna firma: sono asserzioni del wallet, non fatti verificati.
 */
internal data class VerifiedDeviceResponse(
    val documents: List<MdocDocument>,
    val documentErrors: List<Map<String, Int>>,
)

/**
 * Verifica l'integrità dei documenti di una DeviceResponse e li restituisce.
 *
 * `DeviceResponse.verify()` di multipaz è tutto-o-niente: itera su tutti i documenti e solleva
 * `IllegalStateException` sia per fallimenti d'integrità (firma MSO, docType mismatch, digest,
 * `deviceAuth`) sia per un MSO fuori finestra di validità. Collassare tutti i casi in `PVE04` ha
 * due conseguenze indesiderate:
 *
 * 1. l'esito outcome-side previsto per un MSO scaduto (`MSO_VALIDITY = FAILED`, schermata
 *    "Documento non leggibile") non è mai raggiungibile: la finestra di validità **non** deve
 *    produrre un errore di protocollo;
 * 2. in una response multi-documento un solo documento difettoso invalida anche quelli validi.
 *
 * Strategia: si tiene `verify()` aggregata come **fast path** — nel caso nominale il comportamento
 * è identico a prima — e solo in caso di fallimento si entra in un giro per-documento.
 *
 * @param root il `DataItem` della DeviceResponse, così come decodificato dal transport.
 * @param sessionTranscript il session transcript della sessione.
 * @param eReaderKey la chiave eReader, necessaria per `deviceAuth` in modalità MAC.
 * @param now l'orologio con cui valutare la finestra nel fast path (iniettabile per i test).
 * @param onPerDocumentFallback invocato quando si ricade sul giro per-documento (osservabilità/test).
 * @return i documenti verificati e i `documentErrors` dichiarati dal wallet.
 * @throws ProximityVerificationException con codice `PVE04` per fallimenti d'integrità.
 */
internal suspend fun verifyDeviceResponseDocuments(
    root: DataItem,
    sessionTranscript: DataItem,
    eReaderKey: AsymmetricKey?,
    now: Instant = Clock.System.now(),
    onPerDocumentFallback: () -> Unit = {},
): VerifiedDeviceResponse {
    val deviceResponse = DeviceResponse.fromDataItem(root)
    return try {
        deviceResponse.verify(
            sessionTranscript = sessionTranscript,
            eReaderKey = eReaderKey,
            atTime = now
        )
        VerifiedDeviceResponse(deviceResponse.documents, deviceResponse.documentErrors)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        Logger.info(VERIFY_TAG) {
            "Aggregate verify failed (${e.message}); falling back to per-document verification"
        }
        onPerDocumentFallback()
        // Fail-closed: il giro per-documento sa isolare solo i documenti mdoc. Se la response ne
        // contiene di altri tipi, il fallimento aggregato può venire da lì, e verificare i soli mdoc
        // lo trasformerebbe in un successo — una response che conteneva qualcosa di
        // crittograficamente rotto diventerebbe valida. Il ktm non consuma quei documenti, ma non li
        // dichiara nemmeno verificati: resta PVE04, come prima dell'introduzione del fallback.
        requireOnlyMdocDocuments(deviceResponse, root)
        VerifiedDeviceResponse(
            documents = verifyPerDocument(root, sessionTranscript, eReaderKey, now),
            documentErrors = deviceResponse.documentErrors,
        )
    }
}

/**
 * Solleva `PVE04` se la response contiene documenti che il giro per-documento non sa verificare.
 *
 * `zkDocuments` ed `encryptedDocuments` si leggono dalle proprietà tipizzate di [DeviceResponse]: sono
 * già parsate, e un rename lato multipaz romperebbe la compilazione invece di far passare il controllo
 * in silenzio.
 *
 * `otherDocuments` (SD-JWT VC e altri formati) va invece cercato nel CBOR grezzo: multipaz 0.99.0 —
 * la versione consumata — **non lo modella affatto**, quindi non lo parsa e non lo verifica. Un
 * documento del genere in una response resta perciò invisibile all'SDK: dichiarare valida quella
 * response perché i suoi mdoc lo sono significherebbe firmare in bianco una parte che nessuno ha
 * guardato. Quando multipaz verrà aggiornato a una versione che lo modella, questo controllo diventerà
 * anche una protezione contro il silenziamento di un suo fallimento crittografico.
 */
private fun requireOnlyMdocDocuments(deviceResponse: DeviceResponse, root: DataItem) {
    val unsupported = buildList {
        if (deviceResponse.zkDocuments.isNotEmpty()) add("zkDocuments")
        if (deviceResponse.encryptedDocuments.isNotEmpty()) add("encryptedDocuments")
        if (root.getOrNull("otherDocuments")?.asArray?.isNotEmpty() == true) add("otherDocuments")
    }
    if (unsupported.isNotEmpty()) {
        throw ProximityVerificationException(
            "Device response integrity verification failed and the response also contains " +
                "${unsupported.joinToString(", ")}, which cannot be verified per-document",
            code = ProximityVerificationExceptionCode.PVE04()
        )
    }
}

/**
 * Verifica ogni documento in isolamento, ricostruendo una DeviceResponse mono-documento.
 *
 * Le firme di una DeviceResponse sono per-documento (`issuerAuth` firma l'MSO di quel documento;
 * `deviceAuth` è calcolata su `SessionTranscript` + `docType` + `DeviceNameSpacesBytes` di quel
 * documento): estrarre un documento dall'array non invalida nulla di ciò che viene verificato.
 *
 * Rilancia `PVE04` sulle cause d'integrità, esattamente come la verifica aggregata.
 */
private suspend fun verifyPerDocument(
    root: DataItem,
    sessionTranscript: DataItem,
    eReaderKey: AsymmetricKey?,
    now: Instant,
): List<MdocDocument> {
    val rawDocuments = root.getOrNull("documents")?.asArray ?: return emptyList()
    val result = mutableListOf<MdocDocument>()
    for (rawDocument in rawDocuments) {
        // MdocDocument.fromDataItem è pubblica e .mso è leggibile senza verify(): serve solo a
        // conoscere la finestra prima di verificare.
        val probe = MdocDocument.fromDataItem(rawDocument)
        val validFrom = probe.mso.validFrom
        val validUntil = probe.mso.validUntil

        // Clamp deliberato: neutralizza SOLO il controllo di finestra dentro multipaz.
        // La finestra NON viene ignorata: è rivalutata outcome-side da MsoValidityChecker con
        // l'orologio reale (DocumentData.toDocumentVerificationResult), che produce
        // MSO_VALIDITY = FAILED e instrada alla schermata "Documento non leggibile".
        // Firma MSO, docType match, digest e deviceAuth restano verificati integralmente.
        // Una finestra invertita (validFrom > validUntil) è un MSO malformato, non scaduto:
        // coerceIn solleverebbe, quindi si usa l'orologio reale e l'esito è PVE04.
        val atTime = if (validFrom <= validUntil) now.coerceIn(validFrom, validUntil) else now

        // Il DataItem del documento va passato GREZZO: ri-serializzare con MdocDocument.toDataItem()
        // rischia byte diversi negli IssuerSignedItemBytes e invaliderebbe i digest dell'MSO.
        // La mappa è costruita in memoria, senza round-trip Cbor.encode/decode.
        val singleDocumentResponse = DeviceResponse.fromDataItem(
            buildCborMap {
                put("version", root["version"])
                put("status", root["status"])
                putCborArray("documents") { add(rawDocument) }
            }
        )
        try {
            singleDocumentResponse.verify(
                sessionTranscript = sessionTranscript,
                eReaderKey = eReaderKey,
                atTime = atTime
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            throw ProximityVerificationException(
                "Device response integrity verification failed for docType '${probe.docType}': ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE04()
            )
        }
        result.add(singleDocumentResponse.documents.first())
    }
    return result
}
