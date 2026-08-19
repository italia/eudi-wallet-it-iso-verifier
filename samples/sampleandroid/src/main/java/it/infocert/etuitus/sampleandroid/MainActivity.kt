package it.infocert.etuitus.sampleandroid

import CrashlyticsLogger
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import it.infocert.proximityverification.ProximityVerification
import it.infocert.proximityverification.StandaloneIssuerMetadata
import it.infocert.proximityverification.StandaloneRelyingPartyParams
import it.infocert.proximityverification.VerificationRequest
import it.infocert.proximityverification.core.catalog.VerificationRequestBuilder
import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheckType
import it.infocert.proximityverification.core.models.ProtocolFailureReason
import it.infocert.proximityverification.core.models.ReaderCertificateInfo
import it.infocert.proximityverification.core.models.VerificationOutcome
import it.infocert.proximityverification.core.models.document.DocumentValueMimeType
import it.infocert.proximityverification.core.models.initialization.CSRParam
import it.infocert.proximityverification.core.models.initialization.ProximityVerificationSettings
import it.infocert.proximityverification.core.models.initialization.RelyingPartyInstanceParams
import it.infocert.proximityverification.core.models.initialization.RelyingPartyProviderInfo
import it.infocert.proximityverification.core.models.request.RequestOutcome
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.lifecycle.VerificationStep
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.resetNfcState
import it.infocert.proximityverification.startVerificationWithNfc
import it.infocert.proximityverification.status.model.FailureReason
import it.infocert.proximityverification.status.model.StatusListPrefetchSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tag dei log del sample per la corroborazione del ritratto.
 *
 * Il verdetto lo emette il consumer, non l'SDK: `proximity-verification-ktm` logga solo i fatti della
 * sessione (docType e claim richiesti, elementi immagine per documento, docType negati dal wallet) e
 * non conosce questa feature.
 */
private const val CORROBORATION_TAG = "SampleCorroboration"

enum class InitializationState {
    UNINITIALIZED,
    INITIALIZING,
    INITIALIZED,
    ERROR
}

data class UiKeyValue(
    val key: String,
    val textValue: String?,
    val mimeType: DocumentValueMimeType,
    val imageData: ByteArray?
)

data class UiVerificationCheck(
    // Enum dell'SDK, non stringhe: rinominare o rimuovere un caso rompe la compilazione del sample
    // invece di far passare in silenzio un confronto che non matcha piu'.
    val type: VerificationCheckType,
    val status: CheckStatus,
    val details: String? = null
)

data class UiValidityInfo(
    val validFrom: String,
    val validUntil: String,
    val signedAt: String,
    val expectedUpdate: String?
)

data class UiSessionTimingInfo(
    val nfcTapToEngagementMs: Long?,
    val engagementToRequestMs: Long,
    val requestToResponseMs: Long,
    val scanningTimeMs: Long?
)

data class UiSessionInfo(
    val engagementType: String,
    val connectionMethod: String,
    val requestSize: Long?,
    val responseSize: Long?,
    val deviceResponseStatus: Long?,
    val timing: UiSessionTimingInfo
)

data class UiProtocolFailure(
    val reason: String,
    val message: String
)

data class UiPrefetchSummary(
    val requestedDocTypes: List<String>,
    val resolvedIssuers: List<String>,
    val fetchedLists: List<String>,
    val failedLists: Map<String, String>,
    val failedIssuers: Map<String, String>,
    val persistedAtEpochMillis: Long
)

data class UiDocumentResult(
    val docType: String,
    val trustPointName: String?,
    val validity: UiValidityInfo,
    val checks: List<UiVerificationCheck>,
    val kvPairs: List<UiKeyValue>
)

// TODO: Refactor — this file is monolithic (~1200 lines). Extract UI composables, verification logic, and settings into separate files.
class MainActivity : ComponentActivity(), VerificationCallback {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var requestConfigRepository: RequestConfigRepository
    private var initializationState by mutableStateOf(InitializationState.UNINITIALIZED)
    private var statusText by mutableStateOf("Status: idle")
    private var currentStepText by mutableStateOf("-")
    private var currentErrorText by mutableStateOf<String?>(null)
    private var settingsState by mutableStateOf(SampleSettings())
    private var requestConfigState by mutableStateOf(RequestConfig())
    private var sdkPreferencesSnapshot by mutableStateOf<Map<String, String>>(emptyMap())
    private var sessionInfoState by mutableStateOf<UiSessionInfo?>(null)
    private var protocolFailureState by mutableStateOf<UiProtocolFailure?>(null)
    private val resultsState = mutableStateListOf<UiDocumentResult>()

    /**
     * Cosa si è chiesto in materia di corroborazione del ritratto, risolto **al momento della
     * richiesta**: il verdetto lato risposta va letto contro ciò che si è chiesto, non contro la
     * configurazione corrente, che l'utente può avere cambiato nel frattempo.
     */
    private var corroborationRequestState by mutableStateOf<PortraitCorroboration.Request>(
        PortraitCorroboration.Request.NotRequested
    )

    /**
     * L'inventario di ciò che si è chiesto e di ciò che è tornato, come lo riporta l'SDK: la policy di
     * obbligatorietà del sample ([RequiredClaims]) è calcolata da qui, non dall'SDK.
     */
    private var requestOutcomeState by mutableStateOf<RequestOutcome?>(null)
    private var readerCertState by mutableStateOf<ReaderCertificateInfo?>(null)
    private var offlineToggleState by mutableStateOf(false)
    private var offlineToggleInFlight by mutableStateOf(false)
    private var prefetchSummaryState by mutableStateOf<UiPrefetchSummary?>(null)
    private var prefetchErrorText by mutableStateOf<String?>(null)

    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val allGranted = granted.values.all { it }
            if (!allGranted) {
                Toast.makeText(this, "BLE permissions denied — Bluetooth verification unavailable", Toast.LENGTH_LONG).show()
            }
        }

    private val qrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }
        val encoded = result.data?.getStringExtra("encoded_b64url")
        if (encoded.isNullOrBlank()) {
            statusText = "Verification Error: no QR payload found"
            return@registerForActivityResult
        }
        val settings: ProximityVerificationSettings = buildVerificationSettings() ?: return@registerForActivityResult
        sessionInfoState = null
        protocolFailureState = null
        resultsState.clear()
        ProximityVerification.startVerification(encoded, settings, this@MainActivity, this@MainActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissionsIfNeeded()
        settingsRepository = SettingsRepository(this)
        settingsState = settingsRepository.load()
        requestConfigRepository = RequestConfigRepository(this)
        requestConfigState = requestConfigRepository.load()
            ?: RequestConfig.defaultForCatalog(ProximityVerification.documentTypeCatalog)
        Logger.setLogLevel(LogLevel.TRACE)
        LoggerConfig.delegateLogger = CrashlyticsLogger(logAllInfo = true)
        refreshSdkPreferences()
        refreshReaderCertificate()

        setContent {
            MaterialTheme {
                SampleAppRoot(
                    initializationState = initializationState,
                    statusText = statusText,
                    currentStepText = currentStepText,
                    currentErrorText = currentErrorText,
                    sessionInfo = sessionInfoState,
                    protocolFailure = protocolFailureState,
                    results = resultsState.toList(),
                    corroborationRequest = corroborationRequestState,
                    requestOutcome = requestOutcomeState,
                    readerCert = readerCertState,
                    offlineToggleState = offlineToggleState,
                    offlineToggleInFlight = offlineToggleInFlight,
                    prefetchSummary = prefetchSummaryState,
                    prefetchErrorText = prefetchErrorText,
                    settings = settingsState,
                    requestConfig = requestConfigState,
                    sdkPreferences = sdkPreferencesSnapshot,
                    catalog = ProximityVerification.documentTypeCatalog,
                    onInitializeClick = { initializeLibrary() },
                    onInitializeStandaloneClick = { showStandaloneInitDialog() },
                    onQrVerifyClick = { startQrVerification() },
                    onNfcVerifyClick = { startNfcVerification() },
                    onOfflineToggle = { newValue -> onOfflineToggle(newValue) },
                    onCloseClick = { closeLibrary() },
                    onClearStorageClick = { clearStorage() },
                    onSettingsChanged = { updateSettings(it) },
                    onResetDefaultsClick = { resetSettingsToDefaults() },
                    onRefreshDebugClick = { refreshSdkPreferences() },
                    onRequestConfigChanged = { updateRequestConfig(it) }
                )
            }
        }
    }

    private fun updateSettings(updatedSettings: SampleSettings) {
        settingsState = updatedSettings
        settingsRepository.save(updatedSettings)
    }

    private fun resetSettingsToDefaults() {
        settingsState = settingsRepository.resetToDefaults()
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    private fun updateRequestConfig(config: RequestConfig) {
        requestConfigState = config
        requestConfigRepository.save(config)
    }

    /**
     * Scrive il verdetto sulla corroborazione nei log **del sample**: la feature è nostra, non
     * dell'SDK, e i log dell'SDK riportano solo i fatti della sessione.
     *
     * Chiamata una volta all'arrivo dell'esito, non dalla UI: un log dentro un composable si
     * ripeterebbe a ogni ricomposizione.
     */
    private fun logCorroborationVerdict() {
        when (val verdict = PortraitCorroboration.evaluate(corroborationRequestState, resultsState.toList())) {
            is PortraitCorroboration.Verdict.NotRequested -> Unit

            is PortraitCorroboration.Verdict.Satisfied -> {
                Log.i(CORROBORATION_TAG, "SATISFIED: portrait from ${verdict.docType}")
                if (verdict.failedTier0Checks.isNotEmpty()) {
                    Log.w(
                        CORROBORATION_TAG,
                        "Production would hide this photo: " +
                            verdict.failedTier0Checks.joinToString(", ") { it.name } + " FAILED"
                    )
                }
            }

            is PortraitCorroboration.Verdict.NotSatisfied ->
                Log.i(CORROBORATION_TAG, "NOT SATISFIED: ${verdict.reason}")

            is PortraitCorroboration.Verdict.Broken ->
                Log.w(CORROBORATION_TAG, "BROKEN: ${verdict.reason}")
        }
    }

    private fun buildVerificationSettings(): ProximityVerificationSettings? {
        val catalog = ProximityVerification.documentTypeCatalog
        val builder = VerificationRequestBuilder(catalog)
        val requestsList: List<VerificationRequest> = requestConfigState.docTypeConfigs
            .filter { (docType, cfg) -> cfg.enabled && catalog.isRegistered(docType) }
            .mapNotNull { (docType, cfg) ->
                val selectedClaims = cfg.claimSelections.filter { it.value.selected }
                if (selectedClaims.isEmpty()) return@mapNotNull null
                val scope = builder.forDocType(docType)
                selectedClaims.forEach { (key, entry) ->
                    val parts = key.split("::")
                    if (parts.size == 2) {
                        scope.requestClaim(parts[0], parts[1], entry.intentToRetain)
                    }
                }
                scope.build()
            }
        if (requestsList.isEmpty()) {
            initializationState = InitializationState.ERROR
            statusText = "No documents or claims selected. Configure in Request Config."
            return null
        }
        corroborationRequestState = PortraitCorroboration.resolveRequest(
            config = requestConfigState,
            registeredDocTypes = catalog.getRegisteredConfigurations().map { it.docType }.toSet()
        ).also { request ->
            // La diagnostica non deve poter sparire in silenzio: se la corroborazione era dichiarata
            // ma la richiesta non ha piu' la forma attesa, lo si dice subito e ad alta voce.
            when (request) {
                is PortraitCorroboration.Request.DeclaredButUnrecognised ->
                    Log.w(CORROBORATION_TAG, "Request shape broken: ${request.reason}")

                is PortraitCorroboration.Request.Declared -> Log.i(
                    CORROBORATION_TAG,
                    "Requested: primary=${request.shape.primaryDocTypes.joinToString(", ")}, " +
                        "corroborating=${request.shape.corroboratingDocType} (portrait only)"
                )

                is PortraitCorroboration.Request.NotRequested -> Unit
            }
        }
        return settingsState.buildVerificationSettings(requestsList)
    }

    private fun initializeLibrary() {
        initializationState = InitializationState.INITIALIZING
        statusText = "Initializing..."
        currentErrorText = null
        currentStepText = "-"
        sessionInfoState = null
        protocolFailureState = null
        resultsState.clear()

        activityScope.launch(Dispatchers.IO) {
            try {
                val params = RelyingPartyInstanceParams(
                    providerInfo = RelyingPartyProviderInfo(
                        baseUrl = "https://pre.api.gateway.ipzs.it/t/itwallet.api/verifier-proxy/1.0/v1",
                        deviceInfo = it.infocert.proximityverification.network.model.common.DeviceInfo(
                            platform = "android",
                            appVersion = "4.4.1",
                            buildVersion = "190",
                            lang = "it"
                        ),
                        csrParam = CSRParam(
                            commonName = "Valida Documenti",
                            organization = "IPZS - Istituto Poligrafico e Zecca dello Stato",
                            organizationalUnit = "IT-Wallet Relying Party",
                            country = "IT"
                        ),
                        federationEntityUrl = "https://pre.verifier.wallet.ipzs.it",
                        trustAnchors = listOf(Constants.SS_IOT_ECDSA_ROOT, Constants.PRE_ROOT),
                        // Set `pv.cloudProjectNumber` in the root `local.properties` to point
                        // the sample at your own Google Cloud project; see the sample README.
                        cloudProjectNumber = BuildConfig.CLOUD_PROJECT_NUMBER
                    )
                )
                ProximityVerification.initializeRelyingPartyInstance(params)
                val initialOfflineState = ProximityVerification.isStatusListsForOfflineUseEnabled()
                withContext(Dispatchers.Main) {
                    initializationState = InitializationState.INITIALIZED
                    statusText = "Initialized - Token acquired"
                    currentErrorText = null
                    offlineToggleState = initialOfflineState
                    refreshSdkPreferences()
                    refreshReaderCertificate()
                }
            } catch (exception: ProximityVerificationException) {
                withContext(Dispatchers.Main) {
                    initializationState = InitializationState.ERROR
                    statusText = "Initialization error: ${exception.message}"
                    currentErrorText = "Code: ${exception.code.code}"
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    initializationState = InitializationState.ERROR
                    statusText = "Initialization error: ${exception.message}"
                    currentErrorText = null
                }
            }
        }
    }

    private fun initializeStandalone(issuerMeta: StandaloneIssuerMetadata?) {
        initializationState = InitializationState.INITIALIZING
        statusText = "Initializing standalone..."
        currentErrorText = null
        currentStepText = "-"
        sessionInfoState = null
        protocolFailureState = null
        resultsState.clear()

        activityScope.launch(Dispatchers.IO) {
            try {
                ProximityVerification.initializeStandalone(
                    StandaloneRelyingPartyParams(issuerMetadata = issuerMeta)
                )
                withContext(Dispatchers.Main) {
                    initializationState = InitializationState.INITIALIZED
                    statusText = "Initialized (standalone mode — self-signed cert, no backend)"
                    currentErrorText = null
                    offlineToggleState = false
                    refreshSdkPreferences()
                    refreshReaderCertificate()
                }
            } catch (exception: ProximityVerificationException) {
                withContext(Dispatchers.Main) {
                    initializationState = InitializationState.ERROR
                    statusText = "Standalone init error: ${exception.message}"
                    currentErrorText = "Code: ${exception.code.code}"
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    initializationState = InitializationState.ERROR
                    statusText = "Standalone init error: ${exception.message}"
                    currentErrorText = null
                }
            }
        }
    }

    private fun showStandaloneInitDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Issuer metadata URL (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Initialize Standalone")
            .setMessage("Optionally provide an issuer metadata URL to load credential types. Leave empty to use the built-in mDL catalog.")
            .setView(input)
            .setPositiveButton("Initialize") { _, _ ->
                val urlText = input.text.toString().trim()
                val issuerMeta = if (urlText.isNotEmpty()) StandaloneIssuerMetadata.FromUrl(urlText) else null
                initializeStandalone(issuerMeta)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startQrVerification() {
        if (initializationState != InitializationState.INITIALIZED) {
            Toast.makeText(this, "Initialize first", Toast.LENGTH_SHORT).show()
            return
        }
        sessionInfoState = null
        protocolFailureState = null
        resultsState.clear()
        qrLauncher.launch(Intent(this, QrScanActivity::class.java))
    }

    private fun startNfcVerification() {
        if (initializationState != InitializationState.INITIALIZED) {
            Toast.makeText(this, "Initialize first", Toast.LENGTH_SHORT).show()
            return
        }
        val settings: ProximityVerificationSettings = buildVerificationSettings() ?: return
        sessionInfoState = null
        protocolFailureState = null
        resultsState.clear()
        ProximityVerification.resetNfcState(this@MainActivity)
        ProximityVerification.startVerificationWithNfc(settings, this@MainActivity, this@MainActivity)
    }


    private fun closeLibrary() {
        ProximityVerification.close()
        initializationState = InitializationState.UNINITIALIZED
        statusText = "Library closed"
        currentStepText = "-"
        currentErrorText = null
        sessionInfoState = null
        protocolFailureState = null
        resultsState.clear()
        offlineToggleState = false
        offlineToggleInFlight = false
        prefetchSummaryState = null
        prefetchErrorText = null
        readerCertState = null
    }

    private fun onOfflineToggle(enabled: Boolean) {
        if (initializationState != InitializationState.INITIALIZED) {
            Toast.makeText(this, "Initialize first", Toast.LENGTH_SHORT).show()
            return
        }
        if (offlineToggleInFlight) return
        if (enabled) enableOfflineStatusLists() else disableOfflineStatusLists()
    }

    private fun enableOfflineStatusLists() {
        offlineToggleInFlight = true
        prefetchSummaryState = null
        prefetchErrorText = null
        statusText = "Enabling offline mode..."

        activityScope.launch(Dispatchers.IO) {
            try {
                val summary: StatusListPrefetchSummary =
                    ProximityVerification.enableStatusListsForOfflineUse()
                withContext(Dispatchers.Main) {
                    prefetchSummaryState = summary.toUi()
                    offlineToggleState = true
                    offlineToggleInFlight = false
                    statusText = "Offline mode enabled: ${summary.fetchedLists.size} list(s) cached, " +
                        "${summary.failedLists.size} failed list(s), " +
                        "${summary.failedIssuers.size} failed issuer(s)"
                }
            } catch (exception: ProximityVerificationException) {
                val refreshed = ProximityVerification.isStatusListsForOfflineUseEnabled()
                withContext(Dispatchers.Main) {
                    offlineToggleInFlight = false
                    offlineToggleState = refreshed
                    prefetchErrorText = "${exception.code.code}: ${exception.message}"
                    statusText = "Offline enable error: ${exception.code.code}"
                }
            } catch (exception: Exception) {
                val refreshed = ProximityVerification.isStatusListsForOfflineUseEnabled()
                withContext(Dispatchers.Main) {
                    offlineToggleInFlight = false
                    offlineToggleState = refreshed
                    prefetchErrorText = exception.message
                    statusText = "Offline enable error: ${exception.message}"
                }
            }
        }
    }

    private fun disableOfflineStatusLists() {
        offlineToggleInFlight = true
        prefetchErrorText = null
        statusText = "Disabling offline mode..."

        activityScope.launch(Dispatchers.IO) {
            ProximityVerification.disableStatusListsForOfflineUse()
            withContext(Dispatchers.Main) {
                offlineToggleState = false
                offlineToggleInFlight = false
                prefetchSummaryState = null
                statusText = "Offline mode disabled"
            }
        }
    }

    private fun clearStorage() {
        activityScope.launch(Dispatchers.IO) {
            ProximityVerification.clearLibraryLocalStorage()
            val refreshed = ProximityVerification.isStatusListsForOfflineUseEnabled()
            withContext(Dispatchers.Main) {
                offlineToggleState = refreshed
                prefetchSummaryState = null
                prefetchErrorText = null
                readerCertState = null
                refreshSdkPreferences()
                statusText = "Storage cleared"
            }
        }
    }

    private fun refreshSdkPreferences() {
        val prefs = getSharedPreferences(Constants.KEY_TAGS_FILE_NAME, MODE_PRIVATE)
        val allEntries = prefs.all.entries.associate { entry -> entry.key to entry.value.toString() }
        sdkPreferencesSnapshot = allEntries
    }

    private fun refreshReaderCertificate() {
        activityScope.launch(Dispatchers.IO) {
            val cert = ProximityVerification.getReaderCertificate()
            withContext(Dispatchers.Main) { readerCertState = cert }
        }
    }

    override fun onVerificationUpdate(step: VerificationStep) {
        runOnUiThread {
            currentStepText = step.toReadableStep()
            statusText = step.toStatusMessage()
            if (step is VerificationStep.Starting) {
                sessionInfoState = null
                protocolFailureState = null
                resultsState.clear()
                requestOutcomeState = null
            }
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    override fun onVerificationCompleted(outcome: VerificationOutcome) {
        runOnUiThread {
            when (outcome) {
                is VerificationOutcome.DocumentReceived -> {
                    currentErrorText = null
                    statusText = "Success: ${outcome.documents.size} document(s)"
                    protocolFailureState = null
                    requestOutcomeState = outcome.requestOutcome
                    sessionInfoState = UiSessionInfo(
                        engagementType = outcome.sessionInfo.engagementType.name,
                        connectionMethod = outcome.sessionInfo.connectionMethod.toString(),
                        requestSize = outcome.sessionInfo.requestSize,
                        responseSize = outcome.sessionInfo.responseSize,
                        deviceResponseStatus = outcome.sessionInfo.deviceResponseStatus,
                        timing = UiSessionTimingInfo(
                            nfcTapToEngagementMs = outcome.sessionInfo.timing.nfcTapToEngagement?.inWholeMilliseconds,
                            engagementToRequestMs = outcome.sessionInfo.timing.engagementReceivedToRequestSent.inWholeMilliseconds,
                            requestToResponseMs = outcome.sessionInfo.timing.requestSentToResponseReceived.inWholeMilliseconds,
                            scanningTimeMs = outcome.sessionInfo.timing.scanningTime?.inWholeMilliseconds
                        )
                    )
                    resultsState.clear()
                    outcome.documents.forEach { document ->
                        resultsState.add(
                            UiDocumentResult(
                                docType = document.docType,
                                trustPointName = document.issuerCertInfo?.trustPointName,
                                validity = UiValidityInfo(
                                    validFrom = document.validityInfo.validFrom.toString(),
                                    validUntil = document.validityInfo.validUntil.toString(),
                                    signedAt = document.validityInfo.signedAt.toString(),
                                    expectedUpdate = document.validityInfo.expectedUpdate?.toString()
                                ),
                                checks = document.checks.map { check ->
                                    UiVerificationCheck(
                                        type = check.type,
                                        status = check.status,
                                        details = formatCheckMetadata(check.metadata)
                                    )
                                },
                                kvPairs = document.data.map { pair ->
                                    UiKeyValue(
                                        key = pair.key,
                                        textValue = pair.textValue,
                                        mimeType = pair.mimeType,
                                        imageData = pair.imageData
                                    )
                                }
                            )
                        )
                    }
                    logCorroborationVerdict()
                }

                is VerificationOutcome.ProtocolFailed -> {
                    sessionInfoState = null
                    resultsState.clear()
                    requestOutcomeState = null
                    statusText = "Verification Error: ${outcome.message}"
                    currentErrorText = "Reason: ${outcome.reason.name}"
                    protocolFailureState = UiProtocolFailure(
                        reason = outcome.reason.name,
                        message = outcome.message
                    )
                    when (outcome.reason) {
                        ProtocolFailureReason.NFC_NOT_SUPPORTED -> {
                            Toast.makeText(this, "This device doesn't support NFC", Toast.LENGTH_LONG).show()
                        }

                        ProtocolFailureReason.NFC_NOT_ENABLED -> {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("NFC Disabled")
                                .setMessage("Please enable NFC in settings to use this feature")
                                .setPositiveButton("Settings") { _, _ ->
                                    startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }

                        ProtocolFailureReason.NFC_TAG_LOST -> {
                            Toast.makeText(this, "NFC connection lost. Please try again.", Toast.LENGTH_SHORT).show()
                        }

                        ProtocolFailureReason.INVALID_ENGAGEMENT_DATA -> {
                            Toast.makeText(this, "Invalid NFC data. Ensure the other device is ready.", Toast.LENGTH_SHORT).show()
                        }

                        ProtocolFailureReason.NFC_SCAN_CANCELLED -> {
                            Toast.makeText(this, "NFC scan cancelled by user.", Toast.LENGTH_SHORT).show()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun requestBlePermissionsIfNeeded() {
        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            blePermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

private fun StatusListPrefetchSummary.toUi(): UiPrefetchSummary {
    return UiPrefetchSummary(
        requestedDocTypes = requestedDocTypes,
        resolvedIssuers = resolvedIssuers,
        fetchedLists = fetchedLists,
        failedLists = failedLists.mapValues { (_, reason: FailureReason) -> reason.name },
        failedIssuers = failedIssuers.mapValues { (_, reason: FailureReason) -> reason.name },
        persistedAtEpochMillis = persistedAtEpochMillis
    )
}

private fun VerificationStep.toReadableStep(): String {
    return when (this) {
        is VerificationStep.NfcScanningStarted -> "NfcScanningStarted"
        is VerificationStep.NfcTagDetected -> "NfcTagDetected"
        is VerificationStep.NfcEngagementReading -> "NfcEngagementReading"
        is VerificationStep.NfcEngagementCompleted -> "NfcEngagementCompleted"
        is VerificationStep.EngagementParsed -> "EngagementParsed"
        is VerificationStep.TransportSelected -> "TransportSelected: ${this.transportType}"
        is VerificationStep.NfcDataTransferReady -> "NfcDataTransferReady"
        is VerificationStep.Starting -> "Starting"
        is VerificationStep.Connecting -> "Connecting"
        is VerificationStep.SendingRequest -> "SendingRequest"
        is VerificationStep.Authenticating -> "Authenticating"
        is VerificationStep.Verifying -> "Verifying"
        is VerificationStep.Completed -> "Completed"
        is VerificationStep.Failed -> "Failed"
    }
}

private fun VerificationStep.toStatusMessage(): String {
    return when (this) {
        is VerificationStep.NfcScanningStarted -> "NFC Ready: ${this.message}"
        is VerificationStep.NfcTagDetected -> "NFC tag detected"
        is VerificationStep.NfcEngagementReading -> "Reading NFC engagement"
        is VerificationStep.NfcEngagementCompleted -> "NFC engagement completed"
        is VerificationStep.EngagementParsed -> "Engagement parsed"
        is VerificationStep.TransportSelected -> "Transport: ${this.transportType}"
        is VerificationStep.NfcDataTransferReady -> "Bring device near holder: ${this.message}"
        is VerificationStep.Starting -> "Starting verification process..."
        is VerificationStep.Connecting -> "Connecting to mDL holder..."
        is VerificationStep.SendingRequest -> "Sending request..."
        is VerificationStep.Authenticating -> "Authenticating..."
        is VerificationStep.Verifying -> "Verifying response..."
        is VerificationStep.Completed -> "Verification completed successfully"
        is VerificationStep.Failed -> "Verification failed"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleAppRoot(
    initializationState: InitializationState,
    statusText: String,
    currentStepText: String,
    currentErrorText: String?,
    sessionInfo: UiSessionInfo?,
    protocolFailure: UiProtocolFailure?,
    results: List<UiDocumentResult>,
    corroborationRequest: PortraitCorroboration.Request,
    requestOutcome: RequestOutcome?,
    readerCert: ReaderCertificateInfo?,
    offlineToggleState: Boolean,
    offlineToggleInFlight: Boolean,
    prefetchSummary: UiPrefetchSummary?,
    prefetchErrorText: String?,
    settings: SampleSettings,
    requestConfig: RequestConfig,
    sdkPreferences: Map<String, String>,
    catalog: it.infocert.proximityverification.core.catalog.DocumentTypeCatalog,
    onInitializeClick: () -> Unit,
    onInitializeStandaloneClick: () -> Unit,
    onQrVerifyClick: () -> Unit,
    onNfcVerifyClick: () -> Unit,
    onOfflineToggle: (Boolean) -> Unit,
    onCloseClick: () -> Unit,
    onClearStorageClick: () -> Unit,
    onSettingsChanged: (SampleSettings) -> Unit,
    onResetDefaultsClick: () -> Unit,
    onRefreshDebugClick: () -> Unit,
    onRequestConfigChanged: (RequestConfig) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "main"

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            "settings" -> "Settings"
                            "debug" -> "Debug"
                            "request_config" -> "Request Config"
                            else -> "Sample mDL Reader"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },

                navigationIcon = {
                    if (currentRoute != "main") {
                        IconButton(
                            // torna a main in un colpo solo (evita "premere back mille volte")
                            onClick = { navController.popBackStack("main", inclusive = false) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },

                actions = {
                    if (currentRoute == "main") {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Request Config") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate("request_config") {
                                        launchSingleTop = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Debug") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate("debug") {
                                        launchSingleTop = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable("main") {
                MainScreen(
                    initializationState = initializationState,
                    statusText = statusText,
                    currentStepText = currentStepText,
                    currentErrorText = currentErrorText,
                    sessionInfo = sessionInfo,
                    protocolFailure = protocolFailure,
                    results = results,
                    corroborationRequest = corroborationRequest,
                    requestOutcome = requestOutcome,
                    readerCert = readerCert,
                    offlineToggleState = offlineToggleState,
                    offlineToggleInFlight = offlineToggleInFlight,
                    prefetchSummary = prefetchSummary,
                    prefetchErrorText = prefetchErrorText,
                    onInitializeClick = onInitializeClick,
                    onInitializeStandaloneClick = onInitializeStandaloneClick,
                    onQrVerifyClick = onQrVerifyClick,
                    onNfcVerifyClick = onNfcVerifyClick,
                    onOfflineToggle = onOfflineToggle,
                    onCloseClick = onCloseClick,
                    onClearStorageClick = onClearStorageClick
                )
            }
            composable("request_config") {
                RequestConfigScreen(
                    catalog = catalog,
                    requestConfig = requestConfig,
                    onConfigChanged = onRequestConfigChanged
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                    onResetDefaultsClick = onResetDefaultsClick
                )
            }
            composable("debug") {
                DebugScreen(
                    sdkPreferences = sdkPreferences,
                    onRefreshClick = onRefreshDebugClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    initializationState: InitializationState,
    statusText: String,
    currentStepText: String,
    currentErrorText: String?,
    sessionInfo: UiSessionInfo?,
    protocolFailure: UiProtocolFailure?,
    results: List<UiDocumentResult>,
    corroborationRequest: PortraitCorroboration.Request,
    requestOutcome: RequestOutcome?,
    readerCert: ReaderCertificateInfo?,
    offlineToggleState: Boolean,
    offlineToggleInFlight: Boolean,
    prefetchSummary: UiPrefetchSummary?,
    prefetchErrorText: String?,
    onInitializeClick: () -> Unit,
    onInitializeStandaloneClick: () -> Unit,
    onQrVerifyClick: () -> Unit,
    onNfcVerifyClick: () -> Unit,
    onOfflineToggle: (Boolean) -> Unit,
    onCloseClick: () -> Unit,
    onClearStorageClick: () -> Unit
) {
    val qrAndNfcEnabled = initializationState == InitializationState.INITIALIZED
    val closeEnabled = initializationState == InitializationState.INITIALIZED
    val initializeEnabled = initializationState != InitializationState.INITIALIZING
    val toggleEnabled = initializationState == InitializationState.INITIALIZED && !offlineToggleInFlight
    var showCertSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (readerCert != null) {
            item { ReaderCertificateBadgeCard(readerCert) { showCertSheet = true } }
        }
        if (offlineToggleState) {
            item { OfflineModeBanner() }
        }
        item {
            Text("Initialization State: $initializationState", style = MaterialTheme.typography.titleMedium)
            Text("Current Step: $currentStepText", style = MaterialTheme.typography.bodyMedium)
            Text("Status: $statusText", style = MaterialTheme.typography.bodyMedium)
            if (currentErrorText != null) {
                Text("Error: $currentErrorText", color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Button(
                onClick = onInitializeClick,
                enabled = initializeEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (initializationState == InitializationState.INITIALIZING) "Initializing..." else "Initialize")
            }
        }
        item {
            OutlinedButton(
                onClick = onInitializeStandaloneClick,
                enabled = initializeEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Initialize Standalone")
            }
        }
        item {
            Button(
                onClick = onQrVerifyClick,
                enabled = qrAndNfcEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("QR Verification")
            }
        }
        item {
            Button(
                onClick = onNfcVerifyClick,
                enabled = qrAndNfcEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("NFC Verification")
            }
        }
        item {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Offline status list verification", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (offlineToggleInFlight) "Updating…"
                            else if (offlineToggleState) "Active — cached lists are used when offline"
                            else "Inactive — verification only succeeds when online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (offlineToggleInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Switch(
                            checked = offlineToggleState,
                            onCheckedChange = onOfflineToggle,
                            enabled = toggleEnabled
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onCloseClick,
                enabled = closeEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close Library")
            }
        }
        item {
            OutlinedButton(
                onClick = onClearStorageClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Storage")
            }
        }
        if (prefetchErrorText != null) {
            item {
                OutcomeSectionCard(title = "Offline mode error") {
                    Text(prefetchErrorText, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (prefetchSummary != null) {
            item {
                OutcomeSectionCard(title = "Offline Status List Prefetch") {
                    OutcomeLabelValue(
                        label = "Requested docTypes",
                        value = if (prefetchSummary.requestedDocTypes.isEmpty()) "(none)"
                        else prefetchSummary.requestedDocTypes.joinToString()
                    )
                    OutcomeLabelValue(
                        label = "Resolved issuers",
                        value = prefetchSummary.resolvedIssuers.size.toString()
                    )
                    OutcomeLabelValue(
                        label = "Lists fetched",
                        value = prefetchSummary.fetchedLists.size.toString()
                    )
                    OutcomeLabelValue(
                        label = "Lists failed",
                        value = prefetchSummary.failedLists.size.toString(),
                        valueColor = if (prefetchSummary.failedLists.isEmpty()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
                    )
                    OutcomeLabelValue(
                        label = "Issuers failed",
                        value = prefetchSummary.failedIssuers.size.toString(),
                        valueColor = if (prefetchSummary.failedIssuers.isEmpty()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
                    )
                    OutcomeLabelValue(
                        label = "Persisted at (epoch ms)",
                        value = prefetchSummary.persistedAtEpochMillis.toString()
                    )
                    if (prefetchSummary.failedLists.isNotEmpty()) {
                        Divider()
                        Text(
                            "Failed lists",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        prefetchSummary.failedLists.forEach { (uri, reason) ->
                            OutcomeLabelValue(label = uri, value = reason)
                        }
                    }
                    if (prefetchSummary.failedIssuers.isNotEmpty()) {
                        Divider()
                        Text(
                            "Failed issuers",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        prefetchSummary.failedIssuers.forEach { (issuer, reason) ->
                            OutcomeLabelValue(label = issuer, value = reason)
                        }
                    }
                }
            }
        }
        val hasOutcome = protocolFailure != null || sessionInfo != null || results.isNotEmpty()
        if (hasOutcome) {
            item {
                Text("Verification Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (protocolFailure != null) {
            item {
                OutcomeSectionCard(title = "Protocol Failure") {
                    OutcomeLabelValue(label = "Reason", value = protocolFailure.reason, valueColor = MaterialTheme.colorScheme.error)
                    OutcomeLabelValue(label = "Message", value = protocolFailure.message)
                }
            }
        }
        if (sessionInfo != null) {
            item {
                OutcomeSectionCard(title = "Session Info") {
                    OutcomeLabelValue(label = "Engagement Type", value = sessionInfo.engagementType)
                    OutcomeLabelValue(label = "Connection Method", value = sessionInfo.connectionMethod)
                    OutcomeLabelValue(label = "Request Size", value = formatNullableBytes(sessionInfo.requestSize))
                    OutcomeLabelValue(label = "Response Size", value = formatNullableBytes(sessionInfo.responseSize))
                    OutcomeLabelValue(label = "Device Response Status", value = sessionInfo.deviceResponseStatus?.toString() ?: "N/A")
                    Divider()
                    Text("Timing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    OutcomeLabelValue(label = "NFC Tap -> Engagement", value = formatMillis(sessionInfo.timing.nfcTapToEngagementMs))
                    OutcomeLabelValue(label = "Engagement -> Request", value = formatMillis(sessionInfo.timing.engagementToRequestMs))
                    OutcomeLabelValue(label = "Request -> Response", value = formatMillis(sessionInfo.timing.requestToResponseMs))
                    OutcomeLabelValue(label = "Scanning Time", value = formatMillis(sessionInfo.timing.scanningTimeMs))
                }
            }
        }
        if (results.isNotEmpty()) {
            val corroboration = PortraitCorroboration.evaluate(corroborationRequest, results)
            if (corroboration !is PortraitCorroboration.Verdict.NotRequested) {
                item { PortraitCorroborationOutcomeCard(verdict = corroboration, documents = results) }
            }
        }
        if (requestOutcome != null) {
            item { RequiredClaimsOutcomeCard(outcome = requestOutcome) }
        }
        itemsIndexed(results) { index, document ->
            OutcomeSectionCard(title = "Document #${index + 1}") {
                Text("Identity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutcomeLabelValue(label = "Document Type", value = document.docType)
                Divider()
                Text("Issuer Certificate Info", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutcomeLabelValue(label = "Trust Point", value = document.trustPointName ?: "Not available")
                Divider()
                Text("Validity Info", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutcomeLabelValue(label = "Valid From", value = document.validity.validFrom)
                OutcomeLabelValue(label = "Valid Until", value = document.validity.validUntil)
                OutcomeLabelValue(label = "Signed At", value = document.validity.signedAt)
                OutcomeLabelValue(label = "Expected Update", value = document.validity.expectedUpdate ?: "Not set")
                Divider()
                Text("Verification Checks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (document.checks.isEmpty()) {
                    Text("No checks available", style = MaterialTheme.typography.bodySmall)
                } else {
                    document.checks.forEach { check ->
                        VerificationCheckRow(check = check)
                    }
                }
                Divider()
                Text("Document Data", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                document.kvPairs.forEach { pair ->
                    if (pair.mimeType == DocumentValueMimeType.IMAGE && pair.imageData != null) {
                        val bitmap = BitmapFactory.decodeByteArray(pair.imageData, 0, pair.imageData.size)
                        Text(pair.key, fontWeight = FontWeight.SemiBold)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = pair.key,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    } else {
                        OutcomeLabelValue(label = pair.key, value = pair.textValue.orEmpty())
                    }
                }
            }
        }
    }
    if (showCertSheet && readerCert != null) {
        ReaderCertificateSheet(readerCert) { showCertSheet = false }
    }
}

@Composable
private fun OfflineModeBanner() {
    Surface(
        color = Color(0xFFC8E6C9),
        contentColor = Color(0xFF1B5E20),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Offline verification mode active",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Cached status lists are being used when the device is offline.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OutcomeSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun OutcomeLabelValue(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/**
 * Esito della corroborazione del ritratto, ricalcolato in-app dai documenti ricevuti: è lo stesso
 * verdetto che l'SDK scrive nei log su `VerificationSession:RESP`, qui reso visibile.
 *
 * Il sample mostra la foto anche quando un check di Tier 0 del documento corroborante è fallito —
 * è uno strumento di diagnostica — ma lo dichiara: in produzione la policy dell'app non la
 * mostrerebbe.
 */
@Composable
private fun PortraitCorroborationOutcomeCard(
    verdict: PortraitCorroboration.Verdict,
    documents: List<UiDocumentResult>
) {
    OutcomeSectionCard(title = "Portrait Corroboration") {
        when (verdict) {
            is PortraitCorroboration.Verdict.NotRequested -> Unit

            is PortraitCorroboration.Verdict.NotSatisfied -> {
                OutcomeLabelValue(
                    label = "Outcome",
                    value = "NOT SATISFIED",
                    valueColor = Color(0xFFE65100)
                )
                OutcomeLabelValue(label = "Reason", value = verdict.reason)
                Text(
                    "The requirement says a missing photo must not block the verification: " +
                        "the primary credential above is unaffected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is PortraitCorroboration.Verdict.Broken -> {
                OutcomeLabelValue(
                    label = "Outcome",
                    value = "BROKEN REQUEST",
                    valueColor = MaterialTheme.colorScheme.error
                )
                OutcomeLabelValue(label = "Reason", value = verdict.reason)
                Text(
                    "Corroboration was declared in Request Config but the request did not have the " +
                        "expected shape. Re-apply the preset, or clear the declaration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is PortraitCorroboration.Verdict.Satisfied -> {
                OutcomeLabelValue(
                    label = "Outcome",
                    value = "SATISFIED",
                    valueColor = Color(0xFF2E7D32)
                )
                OutcomeLabelValue(label = "Portrait from", value = verdict.docType)
                if (verdict.failedTier0Checks.isNotEmpty()) {
                    OutcomeLabelValue(
                        label = "Production would hide this photo",
                        value = verdict.failedTier0Checks.joinToString(", ") + " FAILED",
                        valueColor = MaterialTheme.colorScheme.error
                    )
                }
                val portrait = documents
                    .firstOrNull { it.docType == verdict.docType }
                    ?.let { PortraitCorroboration.portraitOf(it) }
                if (portrait != null) {
                    val bitmap = BitmapFactory.decodeByteArray(portrait, 0, portrait.size)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Corroborated portrait",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Esito della policy di obbligatorietà, ricalcolato in-app dal `RequestOutcome`: è lo stesso
 * inventario che l'SDK scrive nei log su `VerificationSession:RESP`, qui reso visibile.
 *
 * Mostra la distinzione fra claim negato e claim omesso in silenzio come **diagnostica**: il verdetto
 * di [RequiredClaims] non la usa, perché non è coperta da firma.
 */
@Composable
private fun RequiredClaimsOutcomeCard(outcome: RequestOutcome) {
    OutcomeSectionCard(title = "Required Claims") {
        when (val verdict = RequiredClaims.evaluate(outcome)) {
            is RequiredClaims.Verdict.Complete ->
                OutcomeLabelValue(
                    label = "Outcome",
                    value = "COMPLETE",
                    valueColor = Color(0xFF2E7D32)
                )

            is RequiredClaims.Verdict.Incomplete -> {
                OutcomeLabelValue(
                    label = "Outcome",
                    value = "INCOMPLETE",
                    valueColor = Color(0xFFE65100)
                )
                verdict.missingDocuments.forEach { document ->
                    OutcomeLabelValue(
                        label = "Missing document",
                        value = "${document.docType} — ${document.status}" +
                            (document.errorCode?.let { " (error $it)" } ?: "")
                    )
                }
                verdict.missingClaims.forEach { claim ->
                    OutcomeLabelValue(
                        label = "Missing claim",
                        value = "${claim.namespace}/${claim.identifier} — ${claim.status}" +
                            (claim.errorCode?.let { " (error $it)" } ?: "")
                    )
                }
                Text(
                    "The sample requires every claim it asked for. WITHHELD means the wallet " +
                        "declared it refused the claim, ABSENT means it just did not send it — a " +
                        "distinction taken from unsigned data, shown for diagnostics only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VerificationCheckRow(check: UiVerificationCheck) {
    val statusColor = when (check.status) {
        CheckStatus.PASSED -> Color(0xFF2E7D32)
        CheckStatus.FAILED -> MaterialTheme.colorScheme.error
        CheckStatus.NOT_DETERMINED -> Color(0xFFE65100)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(check.type.name, style = MaterialTheme.typography.bodyMedium)
            Text(check.status.name, style = MaterialTheme.typography.bodyMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
        }
        check.details?.let { details ->
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Rende leggibili i metadata additivi introdotti con i check arricchiti della libreria
 * (`CheckMetadata`): stato di revoca + freschezza della status list, esito CRL, finestra di
 * validità tecnica dell'MSO, scadenza amministrativa del documento.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun formatCheckMetadata(metadata: CheckMetadata?): String? = when (metadata) {
    null -> null
    is CheckMetadata.StatusListRevocation ->
        "state=${metadata.state.name} · mode=${metadata.mode.name} · freshness=${metadata.usability.name}"
    is CheckMetadata.CrlRevocation ->
        "CRL: ${metadata.crlCheckStatus.name}"
    is CheckMetadata.MsoValidity ->
        "valid ${metadata.validFrom} → ${metadata.validUntil}"
    is CheckMetadata.DocumentExpiry ->
        metadata.expiryDate?.let { "expiry: $it" } ?: "expiry: N/A"
}

private fun formatMillis(value: Long?): String {
    return value?.let { "${it} ms" } ?: "N/A"
}

private fun formatNullableBytes(value: Long?): String {
    return value?.let { "${it} B" } ?: "N/A"
}

@Composable
private fun SettingsScreen(
    settings: SampleSettings,
    onSettingsChanged: (SampleSettings) -> Unit,
    onResetDefaultsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("NFC Negotiated Handover Transports", style = MaterialTheme.typography.titleMedium)
        Text("Controls which transports the reader advertises during NFC negotiated handover. Does not affect QR engagement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch("BLE (mdoc central client mode)", settings.bleCentralClientModeEnabled) { value ->
            onSettingsChanged(settings.copy(bleCentralClientModeEnabled = value))
        }
        SettingSwitch("BLE (mdoc peripheral server mode)", settings.blePeripheralServerModeEnabled) { value ->
            onSettingsChanged(settings.copy(blePeripheralServerModeEnabled = value))
        }
        SettingSwitch("NFC Data Transfer", settings.nfcEnabled) { value -> onSettingsChanged(settings.copy(nfcEnabled = value)) }
        Divider()

        Text("Transport Selection", style = MaterialTheme.typography.titleMedium)
        Text("Priority when multiple transports are available (applies to both QR and NFC flows).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TransportPreferenceDropdown(
            current = settings.preferredTransport,
            onChanged = { value -> onSettingsChanged(settings.copy(preferredTransport = value)) }
        )
        Divider()

        Text("Transport Options", style = MaterialTheme.typography.titleMedium)
        SettingSwitch("Use L2CAP if available", settings.bleUseL2Cap) { value -> onSettingsChanged(settings.copy(bleUseL2Cap = value)) }
        SettingSwitch("Use L2CAP in engagement if available", settings.bleUseL2CapInEngagement) { value ->
            onSettingsChanged(settings.copy(bleUseL2CapInEngagement = value))
        }
        SettingSwitch("Keep connection open after first request", settings.allowMultipleRequests) { value ->
            onSettingsChanged(settings.copy(allowMultipleRequests = value))
        }
        Divider()

        Text("NFC Messages", style = MaterialTheme.typography.titleMedium)
        SettingTextField("Scan Message", settings.nfcScanMessage) { value ->
            onSettingsChanged(settings.copy(nfcScanMessage = value))
        }
        SettingTextField("Transfer Message", settings.nfcTransferMessage) { value ->
            onSettingsChanged(settings.copy(nfcTransferMessage = value))
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onResetDefaultsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to Defaults")
        }
    }
}

@Composable
private fun DebugScreen(
    sdkPreferences: Map<String, String>,
    onRefreshClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SDK Local Storage", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefreshClick) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Card(modifier = Modifier.fillMaxSize()) {
            if (sdkPreferences.isEmpty()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("No preferences stored yet")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sdkPreferences.entries.toList(), key = { item -> item.key }) { entry ->
                        Column {
                            Text(entry.key, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(entry.value, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

@Composable
private fun SettingTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportPreferenceDropdown(
    current: String,
    onChanged: (String) -> Unit
) {
    val options = listOf("AUTO", "BLE", "NFC")
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Preferred Transport", modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = current,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().width(130.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChanged(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
