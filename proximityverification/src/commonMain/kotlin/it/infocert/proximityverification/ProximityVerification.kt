package it.infocert.proximityverification

import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.catalog.BuiltInCredentialConfigurations
import it.infocert.proximityverification.core.catalog.CatalogBridge
import it.infocert.proximityverification.core.catalog.DocumentTypeCatalog
import it.infocert.proximityverification.core.catalog.DocumentTypeCatalogImpl
import it.infocert.proximityverification.core.crypto.IntegrityWarmupManager
import it.infocert.proximityverification.core.crypto.TokenManager
import it.infocert.proximityverification.core.crypto.provideLocalStorage
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.handlers.NfcChecker
import it.infocert.proximityverification.core.handlers.PermissionManager
import it.infocert.proximityverification.core.handlers.DefaultDispatcherProvider
import it.infocert.proximityverification.core.handlers.DispatcherProvider
import it.infocert.proximityverification.core.models.ProtocolFailureReason
import it.infocert.proximityverification.core.models.ReaderCertificateInfo
import it.infocert.proximityverification.core.models.VerificationOutcome
import it.infocert.proximityverification.core.models.engagement.EngagementResult
import it.infocert.proximityverification.core.models.engagement.EngagementType
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.models.initialization.RelyingPartyProviderInfo
import it.infocert.proximityverification.core.models.initialization.RelyingPartyInstanceParams
import it.infocert.proximityverification.core.models.initialization.TransportPreference
import it.infocert.proximityverification.core.transport.ConnectionMethodSelector
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.exceptions.VerificationException
import it.infocert.proximityverification.lifecycle.VerificationStep
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.logging.installMultipazLogBridge
import it.infocert.proximityverification.logging.syncMultipazDebugFlag
import it.infocert.proximityverification.network.RelyingPartyApiImpl
import it.infocert.proximityverification.network.model.ApiResult
import it.infocert.proximityverification.core.crypto.CrlChecker
import it.infocert.proximityverification.crl.CrlPrefetcher
import it.infocert.proximityverification.crl.CrlStore
import it.infocert.proximityverification.crl.model.CrlPrefetchSummary
import it.infocert.proximityverification.offline.model.CachedCrlInfo
import it.infocert.proximityverification.offline.model.CachedStatusListInfo
import it.infocert.proximityverification.offline.model.OfflineCacheSnapshot
import it.infocert.proximityverification.registry.RegistryResolver
import it.infocert.proximityverification.status.StatusListPrefetcher
import it.infocert.proximityverification.status.StatusListStore
import it.infocert.proximityverification.status.model.StatusListPrefetchSummary
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Algorithm
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.mdoc.engagement.DeviceEngagement
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcTransportMdocReader
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.fromBase64Url
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object ProximityVerification {

    private const val TAG = "ProximityVerification"

    /**
     * Catalog of registered document types. Always available, independent of [initialize].
     *
     * Example:
     * ```
     * ProximityVerification.documentTypeCatalog.registerFromJson(myJson)
     * ```
     */
    val documentTypeCatalog: DocumentTypeCatalog = DocumentTypeCatalogImpl().also {
        it.registerFromJson(BuiltInCredentialConfigurations.ITALIAN_DRIVING_LICENSE_JSON)
    }

    private var verificationSession: VerificationSession? = null
    private var resolvedRegistryData: it.infocert.proximityverification.registry.model.ResolvedRegistryData? = null
    private var statusListStore: StatusListStore? = null
    // ── unit-02 (CRL prefetch) — bracketed for unit-04 merge symmetry ───
    private var crlStore: CrlStore? = null
    private var providerInfo: RelyingPartyProviderInfo? = null
    // ────────────────────────────────────────────────────────────────────
    private var sessionAllowedIssuerIds: List<String> = emptyList()
    private var sessionJob: CompletableJob? = null
    private var sessionScope: CoroutineScope? = null
    private var activeVerificationJob: Job? = null
    private var sessionGeneration: Long = 0L
    private var verificationGeneration: Long = 0L
    private var activeVerificationGeneration: Long? = null
    
    private var dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider

    // Test seam — overridable in commonTest to bypass expect-object NativeUtils.
    internal var networkProbe: () -> Boolean = { NativeUtils.isNetworkAvailable() }

    /**
     * Configure dispatcher provider (optional, for advanced use cases).
     * By default, uses platform-appropriate dispatchers.
     * 
     * @param provider Custom dispatcher provider, or null to reset to default
     */
    public fun configureDispatcherProvider(provider: DispatcherProvider?): Unit {
        dispatcherProvider = provider ?: DefaultDispatcherProvider
        verificationSession?.updateDispatcherProvider(dispatcherProvider)
    }
    
    /**
     * Get the current dispatcher provider (for internal use).
     * @return Current DispatcherProvider instance
     */
    internal fun getDispatcherProvider(): DispatcherProvider {
        return dispatcherProvider
    }

    /**
     * Lazily constructs a [CrlChecker] wired to the SDK's persistent
     * [CrlStore] and the user-controlled offline-mode flag (governed
     * by [StatusListStore.isEnabled]).
     *
     * Returns `null` when the offline-cache stores are not initialised
     * (i.e. legacy / non-offline-aware flows) so callers preserve the
     * exact pre-unit-03 behaviour by default.
     */
    internal fun crlCheckerOrNull(): CrlChecker? {
        val store: CrlStore = crlStore ?: return null
        val statusListStoreSnapshot: StatusListStore = statusListStore ?: return null
        return CrlChecker(
            store = store,
            isOfflineEnabled = { statusListStoreSnapshot.isEnabled() },
            isOnline = { NativeUtils.isNetworkAvailable() }
        )
    }

    internal fun mapExceptionToOutcome(
        error: ProximityVerificationException
    ): VerificationOutcome.ProtocolFailed {
        val failureReason: ProtocolFailureReason = ProtocolFailureReason.fromCode(error.code)
        return VerificationOutcome.ProtocolFailed(
            reason = failureReason,
            message = error.message,
            code = error.code.code
        )
    }

    internal fun dispatchProtocolFailureOnCallbackDispatcher(
        methodName: String,
        callback: VerificationCallback,
        error: ProximityVerificationException
    ): Unit {
        val callbackDispatcher = dispatcherProvider.callbackDispatcher
        val dispatchNeeded = callbackDispatcher.isDispatchNeeded(EmptyCoroutineContext)
        Logger.debug(TAG) {
            "$methodName - dispatch protocol failure on callbackDispatcher (dispatchNeeded=$dispatchNeeded, code=${error.code.code})"
        }
        val outcome: VerificationOutcome.ProtocolFailed = mapExceptionToOutcome(error)
        if (dispatchNeeded) {
            callbackDispatcher.dispatch(
                context = EmptyCoroutineContext,
                block = { callback.onVerificationCompleted(outcome) }
            )
            return
        }
        callback.onVerificationCompleted(outcome)
    }


    @OptIn(ExperimentalTime::class)
    @Throws(ProximityVerificationException::class, CancellationException::class)
    private suspend fun initializeInternal(
        methodName: String,
        providerInfo: RelyingPartyProviderInfo,
        providerInfoTypeName: String,
        allowedIssuerIds: List<String> = emptyList()
    ): String {
        installMultipazLogBridge()
        syncMultipazDebugFlag()
        Logger.debug(TAG) { "$methodName - cancel previous session (if any)" }
        cancelSessionScope()
        if (NativeUtils.isAndroid()) {
            MultipazContext.initAppForMultipaz()
        }
        if (providerInfo.baseUrl.isEmpty() || providerInfo.baseUrl.isBlank()) {
            throw ProximityVerificationException(
                "Invalid Init parameters. baseUrl param of $providerInfoTypeName must cannot be empty or blank!",
                code = ProximityVerificationExceptionCode.PVE05()
            )
        }
        TokenManager.storeDeviceInfo(providerInfo.deviceInfo)
        val needsRegistration: Boolean = TokenManager.needsRegistration(providerInfo.baseUrl)
        val isDeviceOnline: Boolean = NativeUtils.isNetworkAvailable()
        if (needsRegistration && isDeviceOnline) {
            Logger.info(TAG) { "$methodName - Registration needed, performing device attestation" }
            TokenManager.clearAttestationData()
            Logger.debug(TAG) { "$methodName - Cleared old attestation data" }
            try {
                val attestationData: String = if (NativeUtils.isAndroid()) {
                    val cloudProjectNumber: String? = providerInfo.cloudProjectNumber
                    if (cloudProjectNumber.isNullOrBlank()) {
                        Logger.error(TAG) { "$methodName - cloudProjectNumber is required for Android but not provided" }
                        throw ProximityVerificationException(
                            "cloudProjectNumber is required for Android device attestation",
                            code = ProximityVerificationExceptionCode.PVE05()
                        )
                    }
                    Logger.debug(TAG) { "$methodName - Using cloudProjectNumber for Android attestation" }
                    cloudProjectNumber
                } else {
                    Logger.debug(TAG) { "$methodName - Fetching nonce from backend for iOS attestation" }
                    val api: RelyingPartyApiImpl = RelyingPartyApiImpl(providerInfo.baseUrl)
                    when (val nonceResult = api.fetchNonce()) {
                        is ApiResult.Success -> {
                            nonceResult.data.nonce ?: ""
                        }
                        is ApiResult.Failure -> {
                            Logger.warn(TAG) { "$methodName - Failed to fetch nonce (HTTP ${nonceResult.code}). Proceeding with empty nonce." }
                            ""
                        }
                        is ApiResult.InternalError -> {
                            Logger.warn(TAG) { "$methodName - Failed to fetch nonce: ${nonceResult.exception.message}. Proceeding with empty nonce." }
                            ""
                        }
                    }
                }
                Logger.debug(TAG) { "$methodName - Calling attestDevice with platform-specific data" }
                attestDevice(attestationData)
                Logger.info(TAG) { "$methodName - Device attestation completed successfully" }
            } catch (e: ProximityVerificationException) {
                Logger.error(TAG) { "$methodName - Device attestation failed: ${e.message}" }
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error(TAG) { "$methodName - Device attestation failed: ${e.message}" }
                throw ProximityVerificationException(
                    "Device attestation failed: ${e.message}",
                    e,
                    code = ProximityVerificationExceptionCode.PVE13()
                )
            }
        } else {
            Logger.info(TAG) { "$methodName - Valid token exists, skipping attestation and registration" }
        }
        val accessToken: String = try {
            TokenManager.ensureValidAccessToken(
                baseUrl = providerInfo.baseUrl
            ).also {
                Logger.info(TAG) { "$methodName - Access token acquired successfully" }
            }
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$methodName - Token acquisition failed: ${e.message}" }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG) { "$methodName - Unexpected error during token acquisition: ${e.message}" }
            throw ProximityVerificationException(
                "Token acquisition failed: ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE08()
            )
        }
        val chosenAlgorithm: Algorithm = Algorithm.ESP256
        val readerMaterial: Utils.ReaderMaterial = try {
            Utils.initAndCreateKey(chosenAlgorithm, providerInfo)
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$methodName - Error during initialization: ${e.message}" }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG) { "$methodName - Error during initialization: ${e.message}" }
            throw ProximityVerificationException(
                "Initialization failed: $e",
                e,
                code = ProximityVerificationExceptionCode.PVE01()
            )
        }
        resolvedRegistryData = try {
            RegistryResolver().resolve(
                federationEntityUrl = providerInfo.federationEntityUrl,
                allowedIssuerIds = allowedIssuerIds,
                catalog = documentTypeCatalog,
                storage = provideLocalStorage()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG) {
                "$methodName - Federation registry resolution failed (continuing with built-in catalog): ${e.message}"
            }
            null
        }
        statusListStore = StatusListStore(
            storage = provideLocalStorage()
        )
        // ── unit-02 (CRL prefetch) — store + cached providerInfo ────────
        crlStore = CrlStore(storage = provideLocalStorage())
        this.providerInfo = providerInfo
        // ────────────────────────────────────────────────────────────────
        sessionAllowedIssuerIds = allowedIssuerIds
        val documentTypeRepository = DocumentTypeRepository()
        for (config in documentTypeCatalog.getRegisteredConfigurations()) {
            documentTypeRepository.addDocumentType(CatalogBridge.toDocumentType(config))
        }
        val trustManager = TrustManager(
            storage = EphemeralStorage()
        )
        try {
            Utils.populateTrustManager(trustManager, providerInfo.trustAnchors)
            Logger.info(TAG) { "$methodName - Populating TrustManager with CA certificate." }
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$methodName - Error populating TrustManager: ${e.message}" }
            throw e
        }
        verificationSession = VerificationSession(
            trustManager = trustManager,
            documentTypeRepository = documentTypeRepository,
            certChain = readerMaterial.certChain!!,
            dispatcherProvider = dispatcherProvider,
            statusListStore = statusListStore,
            crlCheckerProvider = { crlCheckerOrNull() }
        )
        val newSessionJob: CompletableJob = SupervisorJob()
        sessionJob = newSessionJob
        sessionScope = CoroutineScope(newSessionJob + dispatcherProvider.backgroundDispatcher)
        sessionGeneration += 1L
        Logger.debug(TAG) { "$methodName - session scope created (sessionGen=$sessionGeneration)" }
        Logger.info(TAG) { "$methodName - Initialization completed successfully" }
        return accessToken
    }

    /**
     * Initialize a Relying Party instance (V2 API).
     * 
     * This method performs structural/bootstrap configuration that remains stable
     * across multiple verification sessions. It does not include transactional
     * parameters like verification requests, which are provided per-verification.
     * 
     * @param params Parameters for initializing the Relying Party instance
     * @return Access token string
     * @throws ProximityVerificationException if initialization fails
     * @throws CancellationException if the operation is cancelled
     */
    @OptIn(ExperimentalTime::class)
    @Throws(ProximityVerificationException::class, CancellationException::class)
    suspend fun initializeRelyingPartyInstance(params: RelyingPartyInstanceParams): String {
        return initializeInternal(
            methodName = "initializeRelyingPartyInstance",
            providerInfo = params.providerInfo,
            providerInfoTypeName = "RelyingPartyProviderInfo",
            allowedIssuerIds = params.allowedIssuerIds
        )
    }

    /**
     * Initializes the SDK in standalone mode without contacting any backend.
     *
     * Use this method to test verification features independently of the RP backend.
     * The reader key is generated locally and self-signed; no device attestation or
     * access token is required.
     *
     * A subsequent call to [initializeRelyingPartyInstance] will override this
     * standalone session with a full online session.
     *
     * **Limitations (test-only mode):**
     * - The self-signed reader certificate will be rejected by wallets that enforce
     *   strict reader authentication (ISO 18013-5 §9.1.4).
     * - [enableStatusListsForOfflineUse] is not available in standalone mode.
     * - No access token is associated with this session.
     *
     * @param params Standalone configuration. Defaults to builtin mDL catalog + trust-all.
     * @throws ProximityVerificationException if key generation or catalog registration fails.
     * @throws CancellationException if the operation is cancelled.
     */
    @Throws(ProximityVerificationException::class, CancellationException::class)
    suspend fun initializeStandalone(
        params: StandaloneRelyingPartyParams = StandaloneRelyingPartyParams()
    ) {
        initializeStandaloneInternal(params)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun initializeStandaloneInternal(params: StandaloneRelyingPartyParams) {
        val mn = "initializeStandalone"
        installMultipazLogBridge()
        syncMultipazDebugFlag()
        Logger.debug(TAG) { "$mn - cancel previous session (if any)" }
        cancelSessionScope()
        if (NativeUtils.isAndroid()) {
            MultipazContext.initAppForMultipaz()
        }

        // --- Catalog ---
        when (val meta = params.issuerMetadata) {
            null -> Logger.info(TAG) { "$mn - Using built-in catalog (no issuer metadata provided)" }
            is StandaloneIssuerMetadata.FromJson -> {
                Logger.info(TAG) { "$mn - Registering catalog from JSON" }
                documentTypeCatalog.registerFromJson(meta.json)
            }
            is StandaloneIssuerMetadata.FromUrl -> {
                Logger.info(TAG) { "$mn - Registering catalog from URL: ${meta.url}" }
                documentTypeCatalog.registerFromIssuerMetadata(meta.url)
            }
        }

        // --- Reader key (standalone, self-signed) ---
        val readerMaterial: Utils.ReaderMaterial = try {
            Utils.initAndCreateKeyStandalone()
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - Key generation failed: ${e.message}" }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Key generation failed: ${e.message}" }
            throw ProximityVerificationException(
                "Standalone initialization failed: $e",
                e,
                code = ProximityVerificationExceptionCode.PVE01()
            )
        }

        // --- Stores ---
        statusListStore = StatusListStore(storage = provideLocalStorage())
        crlStore = CrlStore(storage = provideLocalStorage())

        // --- Document type repository ---
        val documentTypeRepository = DocumentTypeRepository()
        for (config in documentTypeCatalog.getRegisteredConfigurations()) {
            documentTypeRepository.addDocumentType(CatalogBridge.toDocumentType(config))
        }

        // --- TrustManager (null = trust-all) ---
        val trustManager: TrustManager? = params.trustAnchors?.let { anchors ->
            TrustManager(storage = EphemeralStorage()).also {
                try {
                    Utils.populateTrustManager(it, anchors)
                    Logger.info(TAG) { "$mn - TrustManager populated with ${anchors.size} anchor(s)" }
                } catch (e: ProximityVerificationException) {
                    Logger.error(TAG) { "$mn - Error populating TrustManager: ${e.message}" }
                    throw e
                }
            }
        }

        // --- VerificationSession ---
        verificationSession = VerificationSession(
            trustManager = trustManager,
            documentTypeRepository = documentTypeRepository,
            certChain = readerMaterial.certChain!!,
            dispatcherProvider = dispatcherProvider,
            statusListStore = statusListStore,
            crlCheckerProvider = { null }
        )

        // --- Session scope ---
        val newSessionJob: CompletableJob = SupervisorJob()
        sessionJob = newSessionJob
        sessionScope = CoroutineScope(newSessionJob + dispatcherProvider.backgroundDispatcher)
        sessionGeneration += 1L
        Logger.info(TAG) { "$mn - Standalone initialization completed (sessionGen=$sessionGeneration)" }
    }

    // ── Internal engagement parsing helpers ────────────────────────────

    @Throws(ProximityVerificationException::class)
    internal fun parseQrEngagementInternal(
        encodedDeviceEngagement: String,
        settings: ProximityVerificationSettings
    ): EngagementResult {
        val mn = "parseQrEngagement"
        if (encodedDeviceEngagement.isBlank()) {
            throw VerificationException(
                "Encoded device engagement is empty",
                code = ProximityVerificationExceptionCode.PVE28()
            )
        }
        if (!encodedDeviceEngagement.startsWith("mdoc:")) {
            throw VerificationException(
                "Invalid encoded device engagement format (expected 'mdoc:' prefix)",
                code = ProximityVerificationExceptionCode.PVE28()
            )
        }
        val cleanEncoded: String = encodedDeviceEngagement.removePrefix("mdoc:")
        val deviceEngagementBytes: ByteArray = try {
            cleanEncoded.fromBase64Url()
        } catch (e: Exception) {
            throw VerificationException(
                "Invalid Base64 URL encoding: ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE28()
            )
        }
        Logger.info(TAG) { "$mn - Decoded engagement (${deviceEngagementBytes.size} bytes)" }
        val parsedEngagement: DeviceEngagement = try {
            DeviceEngagement.fromDataItem(Cbor.decode(deviceEngagementBytes))
        } catch (e: Exception) {
            throw VerificationException(
                "Failed to parse device engagement CBOR: ${e.message}",
                e,
                code = ProximityVerificationExceptionCode.PVE28()
            )
        }
        val disambiguatedMethods: List<MdocConnectionMethod> = MdocConnectionMethod.disambiguate(
            parsedEngagement.connectionMethods,
            MdocRole.MDOC_READER
        )
        val enabledMethods: List<MdocConnectionMethod> = ConnectionMethodSelector
            .filterBySettings(disambiguatedMethods, settings)
        ConnectionMethodSelector.requireNonEmpty(enabledMethods, disambiguatedMethods)
        Logger.info(TAG) {
            "$mn - enabledMethods=${enabledMethods.map { it::class.simpleName }}"
        }
        return EngagementResult(
            engagementType = EngagementType.QR,
            encodedDeviceEngagement = deviceEngagementBytes,
            parsedEngagement = parsedEngagement,
            handover = Simple.NULL,
            reusableTransport = null,
            resolvedConnectionMethods = enabledMethods
        )
    }


    // ── Policy-Driven API ──────────────────────────────────────────

    /**
     * Starts proximity verification using a QR engagement string.
     *
     * Parses the device engagement, selects transport based on [settings],
     * and runs the full verification. Progress is reported through [callback].
     *
     * Example:
     * ```
     * val settings = ProximityVerificationSettings(
     *     requests = listOf(request),
     *     preferredTransport = TransportPreference.AUTO,
     * )
     * ProximityVerification.startVerification("mdoc:owBj...", settings, callback, activity)
     * ```
     *
     * @param encodedDeviceEngagement Device engagement string in "mdoc:\<base64url\>" format.
     * @param settings Verification settings including requests, transport preferences, and timeouts.
     * @param callback Callback for verification progress and outcome.
     * @param platformContext Platform context (Activity on Android) required when NFC data transfer
     *        is selected after QR engagement. Null when not needed.
     */
    @Throws(ProximityVerificationException::class)
    fun startVerification(
        encodedDeviceEngagement: String,
        settings: ProximityVerificationSettings,
        callback: VerificationCallback,
        platformContext: Any? = null
    ) {
        val mn = "startVerification(QR)"
        if (settings.requests.isEmpty()) {
            val error = VerificationException(
                "ProximityVerificationSettings.requests cannot be empty",
                code = ProximityVerificationExceptionCode.PVE07()
            )
            Logger.error(TAG) { "$mn - ${error.message}" }
            dispatchProtocolFailureOnCallbackDispatcher(mn, callback, error)
            return
        }
        launchVerification(mn, callback) {
            val resolvedSettings = resolveUsableTransportSettings(settings)
            val engagement: EngagementResult =
                parseQrEngagementInternal(encodedDeviceEngagement, resolvedSettings)
            executeVerificationFlow(mn, engagement, resolvedSettings, callback, platformContext)
        }
    }

    // ── Internal verification orchestration ─────────────────────────

    /**
     * Launches a new verification job inside the session scope with
     * cancel-previous semantics. The [block] receives the coroutine context
     * and should call [executeVerificationFlow] (or equivalent).
     *
     * @return true if launch succeeded, false if session is not initialized.
     */
    internal fun launchVerification(
        mn: String,
        callback: VerificationCallback,
        block: suspend () -> Unit
    ): Boolean {
        val scope: CoroutineScope = sessionScope ?: run {
            val error = VerificationException(
                "Session not initialized. Call initializeRelyingPartyInstance() first.",
                code = ProximityVerificationExceptionCode.PVE07()
            )
            Logger.error(TAG) { "$mn - ${error.message}" }
            dispatchProtocolFailureOnCallbackDispatcher(mn, callback, error)
            return false
        }
        val previousVerificationGeneration: Long? = activeVerificationGeneration
        Logger.debug(TAG) {
            "$mn - cancel previous active verification (prevGen=${previousVerificationGeneration ?: "none"})"
        }
        cancelActiveVerification()
        verificationGeneration += 1L
        val currentVerificationGeneration: Long = verificationGeneration
        activeVerificationGeneration = currentVerificationGeneration
        Logger.debug(TAG) {
            "$mn - active verification started (sessionGen=$sessionGeneration, verificationGen=$currentVerificationGeneration)"
        }
        activeVerificationJob = scope.launch {
            try {
                block()
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                Logger.error(TAG) { "$mn - Verification failed: ${exception.message}" }
                val error: ProximityVerificationException =
                    if (exception is ProximityVerificationException) {
                        exception
                    } else {
                        VerificationException(
                            "Verification failed: ${exception.message}",
                            exception,
                            code = ProximityVerificationExceptionCode.PVE07()
                        )
                    }
                withContext(dispatcherProvider.callbackDispatcher) {
                    callback.onVerificationCompleted(mapExceptionToOutcome(error))
                }
            }
        }
        return true
    }

    /**
     * Core verification flow: permission check → transport resolution → data retrieval.
     * Called inside the coroutine launched by [launchVerification].
     *
     * **Pre-condition:** [settings] must already be resolved via [resolveUsableTransportSettings]
     * by the caller before invoking this function. This function does not perform transport
     * availability checks; it operates on the settings snapshot as given.
     */
    internal suspend fun executeVerificationFlow(
        mn: String,
        engagement: EngagementResult,
        settings: ProximityVerificationSettings,
        callback: VerificationCallback,
        platformContext: Any?
    ) {
        val currentSession: VerificationSession = verificationSession ?: run {
            val error = VerificationException(
                "Session not initialized. Call initializeRelyingPartyInstance() first.",
                code = ProximityVerificationExceptionCode.PVE07()
            )
            Logger.error(TAG) { "$mn - ${error.message ?: "Unknown error"}" }
            withContext(dispatcherProvider.callbackDispatcher) {
                callback.onVerificationCompleted(mapExceptionToOutcome(error))
            }
            return
        }
        withContext(dispatcherProvider.callbackDispatcher) {
            callback.onVerificationUpdate(VerificationStep.EngagementParsed)
        }
        Logger.info(TAG) { "$mn - Check Permissions: start..." }
        PermissionManager.checkPermissionsOrThrow(settings)
        Logger.info(TAG) { "$mn - Check Permissions: completed!" }
        val runtimeConnectionMethods: List<MdocConnectionMethod> =
            ConnectionMethodSelector.filterBySettings(
                engagement.resolvedConnectionMethods,
                settings
            )
        if (runtimeConnectionMethods.isEmpty()) {
            throw VerificationException(
                "No suitable connection method available after runtime transport filtering",
                code = ProximityVerificationExceptionCode.PVE07()
            )
        }
        val resolvedConnectionMethod: MdocConnectionMethod = ConnectionMethodSelector.select(
            runtimeConnectionMethods,
            settings.preferredTransport
        ) ?: runtimeConnectionMethods.first()
        val isNfc: Boolean = resolvedConnectionMethod is MdocConnectionMethodNfc
        val transportLabel: String = if (isNfc) "NFC" else "BLE"
        val transport: MdocTransport = resolveTransport(
            mn = mn,
            engagement = engagement,
            resolvedConnectionMethod = resolvedConnectionMethod,
            isNfcTransport = isNfc,
            settings = settings,
            platformContext = platformContext,
            callback = callback
        ) ?: return
        if (isNfc && engagement.reusableTransport == null) {
            val nfcTransport: NfcTransportMdocReader =
                transport as? NfcTransportMdocReader ?: throw VerificationException(
                    "Resolved NFC transport is not a NfcTransportMdocReader",
                    code = ProximityVerificationExceptionCode.PVE07()
                )
            withContext(dispatcherProvider.callbackDispatcher) {
                callback.onVerificationUpdate(VerificationStep.TransportSelected(transportLabel))
                callback.onVerificationUpdate(VerificationStep.NfcDataTransferReady(settings.nfcTransferMessage))
            }
            executeWithNfcDataTransferTag(
                platformContext = platformContext,
                transport = nfcTransport,
                scanMessage = settings.nfcTransferMessage,
                dispatcherProvider = dispatcherProvider,
                callback = callback
            ) {
                executeResolvedTransportVerification(
                    currentSession = currentSession,
                    engagement = engagement,
                    settings = settings,
                    transport = nfcTransport,
                    callback = callback
                )
            }
        } else {
            withContext(dispatcherProvider.callbackDispatcher) {
                callback.onVerificationUpdate(VerificationStep.TransportSelected(transportLabel))
            }
            executeResolvedTransportVerification(
                currentSession = currentSession,
                engagement = engagement,
                settings = settings,
                transport = transport,
                callback = callback
            )
        }
        Logger.info(TAG) { "$mn - Verification flow execution finished" }
    }

    private suspend fun executeResolvedTransportVerification(
        currentSession: VerificationSession,
        engagement: EngagementResult,
        settings: ProximityVerificationSettings,
        transport: MdocTransport,
        callback: VerificationCallback
    ): Unit {
        currentSession.runVerificationWithResolvedTransport(
            encodedDeviceEngagement = engagement.encodedDeviceEngagement,
            parsedEngagement = engagement.parsedEngagement,
            handover = engagement.handover,
            transport = transport,
            engagementType = engagement.engagementType,
            requests = settings.requests,
            allowMultipleRequests = settings.allowMultipleRequests,
            settings = settings,
            stepCallback = callback
        )
    }

    private suspend fun resolveTransport(
        mn: String,
        engagement: EngagementResult,
        resolvedConnectionMethod: MdocConnectionMethod,
        isNfcTransport: Boolean,
        settings: ProximityVerificationSettings,
        platformContext: Any?,
        callback: VerificationCallback
    ): MdocTransport? {
        val reusable: MdocTransport? = engagement.reusableTransport
        if (reusable != null && isTransportMatchingConnectionMethod(reusable, resolvedConnectionMethod)) {
            if (reusable is NfcTransportMdocReader) {
                reusable.updateDialogMessage(settings.nfcTransferMessage)
            }
            Logger.info(TAG) { "$mn - Reusing existing transport from NFC engagement" }
            return reusable
        }
        if (isNfcTransport && platformContext == null && NativeUtils.isAndroid()) {
            Logger.error(TAG) { "$mn - NFC data transfer selected but platformContext is null" }
            val outcome = VerificationOutcome.ProtocolFailed(
                reason = ProtocolFailureReason.PLATFORM_CONTEXT_REQUIRED,
                message = "NFC data transfer selected after QR engagement but platformContext (Activity) was not provided"
            )
            withContext(dispatcherProvider.callbackDispatcher) {
                callback.onVerificationCompleted(outcome)
            }
            return null
        }
        val newTransport: MdocTransport = MdocTransportFactory.Default.createTransport(
            resolvedConnectionMethod,
            MdocRole.MDOC_READER,
            MdocTransportOptions(
                bleUseL2CAP = settings.bleUseL2CAP,
                bleUseL2CAPInEngagement = settings.bleUseL2CAPInEngagement
            )
        )
        Logger.info(TAG) { "$mn - Transport instantiated: ${newTransport::class.simpleName}" }
        return newTransport
    }

    private fun isTransportMatchingConnectionMethod(
        transport: MdocTransport,
        connectionMethod: MdocConnectionMethod
    ): Boolean {
        return when {
            transport is NfcTransportMdocReader && connectionMethod is MdocConnectionMethodNfc -> true
            transport !is NfcTransportMdocReader && connectionMethod !is MdocConnectionMethodNfc -> true
            else -> false
        }
    }

    @Throws(ProximityVerificationException::class, CancellationException::class)
    private suspend fun attestDevice(data: String): String {
        val mn = "attestDevice"

        val isDeviceOnline = NativeUtils.isNetworkAvailable()

        if (data.isEmpty() || data.isBlank() || !isDeviceOnline) {
            val keyTagStorage = provideLocalStorage()
            val keyTag:String = if (NativeUtils.isAndroid()) Constants.ANDROID_DEVICE_CHECK_KEY_ALIAS
            else Constants.IOS_DEVICE_CHECK_KEY_ALIAS
            val tagExists = keyTagStorage.getElement(keyTag) != null

            if (!tagExists) {
                when {
                    data.isEmpty() || data.isBlank() -> {
                        throw ProximityVerificationException(
                            message = "Initialization failed: 'data' is null, empty, or blank, and no tag is stored.",
                            code = ProximityVerificationExceptionCode.PVE05()
                        )
                    }

                    !isDeviceOnline -> {
                        throw ProximityVerificationException(
                            message = "Attest device aborted: device is offline and no tag is stored.",
                            code = ProximityVerificationExceptionCode.PVE13()
                        )
                    }
                }
            }

            IntegrityWarmupManager.alreadyWarmedUp = true
            if (NativeUtils.isAndroid()) {
                return data
            }

            val storedBlob = keyTagStorage.getElement(Constants.IOS_DEVICE_CHECK_ATTESTATION_OBJ)

            return storedBlob ?: data
        }

        val warmUpRes = IntegrityWarmupManager.warmUp(data)

        if (!IntegrityWarmupManager.alreadyWarmedUp) {
            throw ProximityVerificationException(
                "Fallback Play Integrity failed",
                code = ProximityVerificationExceptionCode.PVE12()
            )
        }

        // Store attestation data for later use in token registration
        TokenManager.storeAttestationData(warmUpRes)
        Logger.info(TAG) { "$mn - Attestation data stored for token registration" }

        return warmUpRes
    }
    /**
     * Clears all SDK-managed local storage:
     * - issued tokens and attestation data
     * - federation registry cache
     * - Status List prefetch cache (`PROX_VER_STATUS_LIST_CACHE`)
     * - offline-verification flag (`PROX_VER_OFFLINE_ENABLED`) — resets to default
     *   `false`, equivalent to invoking [disableStatusListsForOfflineUse] without
     *   logging the transition.
     *
     * Best-effort: errors are logged and never propagated.
     */
    suspend fun clearLibraryLocalStorage() {
        val mn = "clearLibraryLocalStorage"
        Logger.info(TAG) { "$mn - Start clearing local storage..." }
        try {
            val keyTagStorage = provideLocalStorage()
            keyTagStorage.clear()
            try {
                keyTagStorage.clearElement(
                    StatusListStore.STORAGE_KEY
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(TAG) { "$mn - Failed to clear status list cache: ${e.message}" }
            }
            // ── unit-02 (CRL prefetch) — best-effort wipe of CRL cache ──
            try {
                keyTagStorage.clearElement(
                    CrlStore.STORAGE_KEY
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(TAG) { "$mn - Failed to clear CRL cache: ${e.message}" }
            }
            // ────────────────────────────────────────────────────────────
            IntegrityWarmupManager.alreadyWarmedUp = false
            Logger.info(TAG) { "$mn - Local storage cleared successfully (including tokens, attestation data, status list cache, CRL cache, and offline-verification flag)." }
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            Logger.error(TAG) { "$mn - Error clearing local storage: ${e.message}" }
        }
    }

    /**
     * Enables offline Token Status List verification.
     *
     * Pre-fetches Token Status Lists for the given docTypes so that MSO
     * revocation can be validated offline, then persists the offline-verification
     * flag (`PROX_VER_OFFLINE_ENABLED = "true"`). On structurally successful return
     * the flag is set even if the summary contains partial failures.
     *
     * The SDK navigates `ResolvedRegistryData` to map each docType to the issuers
     * that publish it, calls each issuer's `status_list_aggregation_endpoint`
     * (Token Status List draft-13 §9), downloads each referenced Status List,
     * validates its freshness via multipaz (`StatusList.fromCwt`/`fromJwt`),
     * and persists the payload in the SDK `LocalStorage`.
     *
     * @param docTypes docTypes to prefetch. Empty list = all docTypes resolved
     *   by the registry. Each docType must be present in the resolved catalog.
     * @return [StatusListPrefetchSummary] with per-list / per-issuer outcomes.
     * @throws ProximityVerificationException with `PVE01` when called before init,
     *   `PVE38` when a requested docType is unknown, `PVE37` when no issuer in the
     *   resolved registry exposes a `status_list_aggregation_endpoint` for the
     *   requested docTypes.
     */
    @Throws(ProximityVerificationException::class, CancellationException::class)
    suspend fun enableStatusListsForOfflineUse(
        docTypes: List<String> = emptyList()
    ): StatusListPrefetchSummary {
        val mn = "enableStatusListsForOfflineUse"
        if (verificationSession == null) {
            throw ProximityVerificationException(
                "$mn called before initializeRelyingPartyInstance",
                code = ProximityVerificationExceptionCode.PVE01()
            )
        }
        if (!networkProbe()) {
            throw ProximityVerificationException(
                "$mn requires network connectivity to prefetch status lists and CRLs; " +
                    "the device is currently offline",
                code = ProximityVerificationExceptionCode.PVE40()
            )
        }
        val registryData: it.infocert.proximityverification.registry.model.ResolvedRegistryData =
            resolvedRegistryData ?: run {
                if (sessionAllowedIssuerIds.isEmpty()) {
                    throw ProximityVerificationException(
                        "$mn requires a resolved registry; the federation registry chain did not produce data",
                        code = ProximityVerificationExceptionCode.PVE37()
                    )
                }
                Logger.info(TAG) {
                    "$mn - resolvedRegistryData is null; attempting direct-federation fallback " +
                        "for ${sessionAllowedIssuerIds.size} configured issuer(s)"
                }
                val fallback = try {
                    RegistryResolver().resolveDirectFederationFallback(
                        federationEntityUrl = providerInfo?.federationEntityUrl ?: "",
                        allowedIssuerIds = sessionAllowedIssuerIds,
                        catalog = documentTypeCatalog,
                        storage = provideLocalStorage()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Logger.warn(TAG) { "$mn - direct-federation fallback failed: ${e.message}" }
                    null
                }
                if (fallback != null) {
                    resolvedRegistryData = fallback
                }
                fallback ?: throw ProximityVerificationException(
                    "$mn requires a resolved registry; the federation registry chain did not produce data",
                    code = ProximityVerificationExceptionCode.PVE37()
                )
            }
        val store = statusListStore ?: StatusListStore(
            storage = provideLocalStorage()
        ).also { statusListStore = it }
        val trustManager = verificationSession?.trustManager ?: throw ProximityVerificationException(
            "$mn requires an initialized session with a populated TrustManager " +
                "to anchor status list signing certificates",
            code = ProximityVerificationExceptionCode.PVE01()
        )
        val prefetcher = StatusListPrefetcher(trustManager = trustManager)
        Logger.info(TAG) { "$mn - starting enable (docTypes=$docTypes)" }
        val summary = prefetcher.prefetch(docTypes = docTypes, registryData = registryData, store = store)

        // ── unit-02 (CRL prefetch) — run after status list, before flag ──
        // A hard CRL throw must NOT prevent the offline flag from being set
        // (see U02-SC-11: status list and CRL outcomes are independent).
        val crls: CrlStore = crlStore ?: CrlStore(
            storage = provideLocalStorage()
        ).also { crlStore = it }
        val crlPrefetcher = CrlPrefetcher(store = crls)
        val crlSummary: CrlPrefetchSummary? = try {
            crlPrefetcher.prefetch(
                trustAnchors = providerInfo?.trustAnchors ?: emptyList(),
                registryData = registryData
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(TAG) {
                "$mn - CRL prefetch failed (continuing with offline flag set): " +
                    "${e::class.simpleName}: ${e.message}"
            }
            null
        }
        val combined: StatusListPrefetchSummary = summary.copy(crlSummary = crlSummary)
        // ────────────────────────────────────────────────────────────────

        try {
            store.setEnabled(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.error(TAG) { "$mn - failed to persist enabled=true: ${e.message}" }
        }
        Logger.info(TAG) {
            "$mn - done; enabled=true, requested=${summary.requestedDocTypes.size}, " +
                "issuers=${summary.resolvedIssuers.size}, " +
                "fetched=${summary.fetchedLists.size}, " +
                "failedLists=${summary.failedLists.size}, " +
                "failedIssuers=${summary.failedIssuers.size}, " +
                "certsConsidered=${crlSummary?.certsConsidered ?: 0}, " +
                "crlsFetched=${crlSummary?.crlsFetched?.size ?: 0}, " +
                "crlsFailed=${crlSummary?.crlsFailed?.size ?: 0}, " +
                "x5cParseFailures=${crlSummary?.parseFailureCount ?: 0}"
        }
        return combined
    }

    /**
     * Disables offline Token Status List verification.
     *
     * Persists `PROX_VER_OFFLINE_ENABLED = "false"` and clears the cache of
     * pre-fetched Status Lists. Idempotent and best-effort: storage I/O errors
     * are logged and never propagated. Callable any time, including before
     * [initializeRelyingPartyInstance].
     */
    suspend fun disableStatusListsForOfflineUse() {
        val mn = "disableStatusListsForOfflineUse"
        val store = statusListStore ?: StatusListStore(
            storage = provideLocalStorage()
        ).also { statusListStore = it }
        try {
            store.setEnabled(false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.error(TAG) { "$mn - failed to persist enabled=false: ${e.message}" }
        }
        store.clearEntries()
        // ── unit-02 (CRL prefetch) — symmetric clear of CRL cache ───────
        val crls: CrlStore = crlStore ?: CrlStore(
            storage = provideLocalStorage()
        ).also { crlStore = it }
        crls.clearEntries()
        // ────────────────────────────────────────────────────────────────
        Logger.info(TAG) { "$mn - done; enabled=false, status list cache cleared, CRL cache cleared" }
    }

    /**
     * Returns whether offline Token Status List verification is currently
     * enabled (default `false` on a fresh install or on read failure).
     *
     * Reflects the persisted state under `PROX_VER_OFFLINE_ENABLED` and is
     * callable any time, including before [initializeRelyingPartyInstance].
     */
    suspend fun isStatusListsForOfflineUseEnabled(): Boolean {
        val store = statusListStore ?: StatusListStore(
            storage = provideLocalStorage()
        ).also { statusListStore = it }
        return store.isEnabled()
    }

    /**
     * Returns a point-in-time projection of the SDK's persistent offline
     * caches (Token Status Lists + X.509 CRLs) for consumer rendering and
     * diagnostics.
     *
     * The returned [OfflineCacheSnapshot] is a pure data structure with no
     * payload bytes: each entry exposes its provenance metadata, freshness
     * timestamps, and a computed staleness flag (`usability` for Status Lists,
     * `isStale` for CRLs).
     *
     * **When to call:**
     * - After [enableStatusListsForOfflineUse] to populate an offline-aware UI.
     * - Before showing a credential picker, to filter out entries with
     *   `usability != Fresh` from the offline-usable set.
     * - Periodically from a diagnostics screen to surface cache health.
     *
     * **Semantics:**
     * - Idempotent: two consecutive calls produce the same `statusLists` /
     *   `crls` (only `generatedAtEpochMillis` differs).
     * - Reads only the persistent caches; no network I/O, no signing-key
     *   resolution.
     * - Safe to call any time, **including before**
     *   [initializeRelyingPartyInstance]: returns
     *   `OfflineCacheSnapshot(offlineEnabled = false, statusLists = [],
     *   crls = [], generatedAtEpochMillis > 0)` on a fresh install with
     *   nothing persisted yet. Never throws on the I/O path: `LocalStorage`
     *   read failures are logged at WARN and degraded to empty lists /
     *   `offlineEnabled = false`. Only `CancellationException` is propagated.
     *
     * **Caveats:**
     * - `CachedStatusListInfo.docTypes` is **best-effort per-issuer**, not
     *   URI-granular: the aggregation response groups status lists by
     *   `credential_type`, so a URI may serve a credential type that maps to
     *   only a subset of the listed docTypes.
     * - `CachedStatusListInfo.usability` is `Fresh` or `StaleByTtl`. The
     *   `Expired` variant is reserved for the use-time `multipaz.validateXxx`
     *   rejection path and is never populated by the snapshot.
     * - No atomicity guarantee against concurrent
     *   [enableStatusListsForOfflineUse] writers; the snapshot reflects
     *   what was persisted at the moment of the call.
     *
     * **Example (Kotlin):**
     * ```
     * val snapshot = ProximityVerification.getOfflineCacheSnapshot()
     * if (!snapshot.offlineEnabled) {
     *     hideOfflineBanner()
     *     return
     * }
     * val freshCount = snapshot.statusLists.count {
     *     it.usability == Usability.Fresh
     * }
     * val staleCrls = snapshot.crls.filter { it.isStale }
     * ```
     *
     * @return a point-in-time [OfflineCacheSnapshot]; never `null`.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getOfflineCacheSnapshot(): OfflineCacheSnapshot {
        val sls: StatusListStore = statusListStore ?: StatusListStore(
            storage = provideLocalStorage()
        ).also { statusListStore = it }
        val crls: CrlStore = crlStore ?: CrlStore(
            storage = provideLocalStorage()
        ).also { crlStore = it }
        val nowMillis: Long = Clock.System.now().toEpochMilliseconds()
        return try {
            buildOfflineCacheSnapshot(sls = sls, crls = crls, nowMillis = nowMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(TAG) {
                "getOfflineCacheSnapshot - degrading to empty snapshot: " +
                    "${e::class.simpleName}: ${e.message}"
            }
            OfflineCacheSnapshot(
                offlineEnabled = false,
                generatedAtEpochMillis = nowMillis,
                statusLists = emptyList(),
                crls = emptyList()
            )
        }
    }

    /**
     * Returns the reader/access certificate obtained from the relying-party backend during
     * initialization, or null if not provisioned yet (e.g. before a successful
     * [initializeRelyingPartyInstance]). Read-only, callable any time (reads from persistent
     * storage); never throws.
     */
    suspend fun getReaderCertificate(): ReaderCertificateInfo? =
        Utils.readReaderCertificateInfo()

    /**
     * Test-only helper that builds an [OfflineCacheSnapshot] from the given
     * stores using the supplied wall-clock. Extracted from
     * [getOfflineCacheSnapshot] so unit tests can inject in-memory-backed
     * stores (sidestepping the `provideLocalStorage()` indirection).
     */
    internal suspend fun buildOfflineCacheSnapshot(
        sls: StatusListStore,
        crls: CrlStore,
        nowMillis: Long
    ): OfflineCacheSnapshot {
        val nowSeconds: Long = nowMillis / 1000
        val statusEntries = sls.listAll()
        val crlEntries = crls.listAll()
        return OfflineCacheSnapshot(
            offlineEnabled = sls.isEnabled(),
            generatedAtEpochMillis = nowMillis,
            statusLists = statusEntries.map { e ->
                CachedStatusListInfo(
                    uri = e.uri,
                    format = e.format,
                    issuerEntityId = e.issuerEntityId,
                    credentialTypes = e.credentialTypes,
                    docTypes = e.docTypes,
                    iatEpochSeconds = e.iat,
                    ttlSeconds = e.ttlSeconds,
                    expiresAtEpochSeconds = if (e.iat != null && e.ttlSeconds != null) {
                        e.iat + e.ttlSeconds
                    } else {
                        null
                    },
                    fetchedAtEpochMillis = e.fetchedAtEpochMillis,
                    usability = sls.usability(e, nowSeconds)
                )
            },
            crls = crlEntries.map { e ->
                CachedCrlInfo(
                    crlUri = e.crlUri,
                    subjectDn = e.subjectDn,
                    issuerDn = e.issuerDn,
                    serialHex = e.serialHex,
                    thisUpdateEpochSeconds = e.thisUpdateEpochSeconds,
                    nextUpdateEpochSeconds = e.nextUpdateEpochSeconds,
                    fetchedAtEpochMillis = e.fetchedAtEpochMillis,
                    isStale = crls.isStale(e, nowSeconds)
                )
            }
        )
    }


    /**
     * Get the current verification session (for internal use by NFC extensions).
     * @return VerificationSession or null if not initialized
     */
    internal fun getVerificationSession(): VerificationSession? {
        return verificationSession
    }

    internal fun cancelActiveVerification(): Unit {
        activeVerificationJob?.cancel()
        activeVerificationJob = null
        activeVerificationGeneration = null
    }

    internal fun getSessionScope(): CoroutineScope? {
        return sessionScope
    }

    /**
     * Test-only helper that injects (or clears) the internal [VerificationSession] so
     * commonTest code can drive lifecycle-dependent guards (e.g. the connectivity
     * pre-check in [enableStatusListsForOfflineUse]) without standing up a full
     * registry/federation harness. Mirrors the [configureDispatcherProvider]/
     * [createSessionScopeForTesting] pattern.
     */
    internal fun setVerificationSessionForTesting(session: VerificationSession?): Unit {
        verificationSession = session
    }

    /**
     * Test-only helper that creates a new session scope using the current dispatcher provider.
     * Any existing session scope is cancelled before creating the new one.
     */
    internal fun createSessionScopeForTesting(): Job {
        cancelSessionScope()
        val newSessionJob: CompletableJob = SupervisorJob()
        sessionJob = newSessionJob
        sessionScope = CoroutineScope(newSessionJob + dispatcherProvider.backgroundDispatcher)
        sessionGeneration += 1L
        Logger.debug(TAG) { "createSessionScopeForTesting - session scope created (sessionGen=$sessionGeneration)" }
        return newSessionJob
    }

    internal fun setActiveVerificationJob(job: Job): Unit {
        activeVerificationJob = job
        verificationGeneration += 1L
        activeVerificationGeneration = verificationGeneration
    }

    internal fun getSessionGeneration(): Long {
        return sessionGeneration
    }

    internal fun getActiveVerificationGeneration(): Long? {
        return activeVerificationGeneration
    }

    /**
     * Cancels all in-flight library work and returns the library to uninitialized state.
     *
     * Safe to call multiple times.
     */
    fun close(): Unit {
        Logger.debug(TAG) {
            "close - cancelling session scope (sessionGen=$sessionGeneration, activeVerificationGen=${activeVerificationGeneration ?: "none"})"
        }
        cancelSessionScope()
        dispatcherProvider = DefaultDispatcherProvider
        Logger.debug(TAG) { "close - completed (uninitialized)" }
    }

    private fun cancelSessionScope(): Unit {
        cancelActiveVerification()
        sessionJob?.cancel()
        sessionJob = null
        sessionScope = null
        verificationSession = null
        resolvedRegistryData = null
        statusListStore = null
        // ── unit-02 (CRL prefetch) — null out lifecycle siblings ────────
        crlStore = null
        providerInfo = null
        // ────────────────────────────────────────────────────────────────
        // Il catalogo è stato di processo (singleton) e, a differenza di
        // resolvedRegistryData, non veniva mai azzerato: le docType risolte da
        // un registry restavano tra un init e l'altro. Lo riporto al baseline
        // builtin così ogni init lo ricostruisce da builtin + registry corrente
        // (RP: RegistryResolver.resolve; standalone: issuerMetadata). Senza
        // questo, cambiando ambiente il catalogo mostrava docType stale.
        resetCatalogToBuiltIn()
    }

    /**
     * Riporta il [documentTypeCatalog] al solo contenuto builtin (mDL ISO
     * 18013-5), scartando ogni docType registrata dalla sessione precedente
     * (registry di federazione o issuer metadata standalone).
     */
    private fun resetCatalogToBuiltIn(): Unit {
        documentTypeCatalog.clear()
        documentTypeCatalog.registerFromJson(
            BuiltInCredentialConfigurations.ITALIAN_DRIVING_LICENSE_JSON
        )
    }
}

// ── Policy-Driven NFC API ─────────────────────────────────────────

/**
 * Starts proximity verification via NFC engagement.
 *
 * Scans NFC engagement, selects transport based on [settings], and
 * runs the full verification. Progress is reported through [callback].
 *
 * Example:
 * ```
 * val settings = ProximityVerificationSettings(
 *     requests = listOf(request),
 *     preferredTransport = TransportPreference.BLE,
 * )
 * ProximityVerification.startVerificationWithNfc(settings, callback, activity)
 * ```
 *
 * @param settings Verification settings including requests, transport preferences, and NFC scan options.
 * @param callback Callback for verification progress and outcome.
 * @param activity Platform context (Activity on Android, null on iOS).
 */
expect fun ProximityVerification.startVerificationWithNfc(
    settings: ProximityVerificationSettings,
    callback: VerificationCallback,
    activity: Any?
)

@Throws(ProximityVerificationException::class, CancellationException::class)
expect fun ProximityVerification.resetNfcState(activity: Any?)
