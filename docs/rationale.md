# Decision Log

Decisioni non ovvie, in forma ADR: decisione, rationale, trade-off, alternative scartate. Registra il
**perché**, che il codice non può ricostruire — non il *cosa*, che si legge nel codice e nei doc
componente.

Gestito da Lumen dal 2026-08-03 (prima manutenzione manuale, con l'ultima voce ferma al 2026-05-20).
Le voci sono in ordine cronologico crescente, tranne quelle marcate `<!-- Ingested -->` che sono state
inserite in testa dal processo di ingest.

<!-- Ingested: 2026-05-09 -->
### [2026-05-09] - CRL Persistence Parallel a StatusListStore (Single Offline Flag)
- **Fase:** Planning + Implementation
- **Decisione:** Introdurre `CrlStore` modellato 1:1 su `StatusListStore` (envelope JSON unico, `LocalStorage` key `PROX_VER_CRL_CACHE`, semantica try/catch identica), ma **senza** API `setEnabled/getEnabled` propria. Il toggle offline resta governato da `StatusListStore.setEnabled(...)` tramite la chiave condivisa estratta in `it.infocert.proximityverification.offline.OfflineModeFlag.STORAGE_KEY = "PROX_VER_OFFLINE_ENABLED"`. `enableStatusListsForOfflineUse` orchestra prefetch status list + prefetch CRL in sequenza; un throw nel prefetch CRL viene assorbito (`Logger.warn`) e produce `crlSummary = null`, ma il flag offline si attiva comunque (status list prefetch indipendente). `disableStatusListsForOfflineUse` pulisce entrambi gli store. `clearLibraryLocalStorage` rimuove anche `PROX_VER_CRL_CACHE` (best-effort).
- **Rationale:** Due cache distinte con due flag separati avrebbero permesso stati incoerenti ("status list offline ma CRL no"). Single source of truth sul flag elimina la classe di bug. Riusare il pattern envelope già provato (`StatusListStore`) riduce rischio implementativo: stessa serializzazione, stesso pattern errori, stessa simmetria enable/disable. La `OfflineModeFlag` constant condivisa è preferita a un duplicato in `CrlStore` per evitare drift di valore tra i due store (manterrebbero lo stesso byte stream sullo storage ma con due dichiarazioni indipendenti).
- **Trade-off / Vincoli:**
  - Failure CRL non aborta status list prefetch (e viceversa) — desideratum, ma il consumer deve interpretare `crlSummary = null` come "CRL prefetch fallito" e non come "non richiesto". Documentato in KDoc.
  - Cache pre-esistenti senza i nuovi campi `issuerEntityId/credentialTypes/docTypes` su `CachedStatusList`: leggibili grazie a `Json { ignoreUnknownKeys = true }` già configurato. Nessuna migrazione richiesta.
  - `StatusListStore.STORAGE_KEY_ENABLED` ora delega a `OfflineModeFlag.STORAGE_KEY` — refactor di costante, valore stringa preservato byte-identico.
- **Alternative scartate:**
  - `CrlStore.setEnabled` separato — scartato: stato divergente catastrofico tra status list e CRL.
  - Envelope unico per status list + CRL — scartato: granularità clear/listAll richiede chiavi separate; stesso flag già garantisce coerenza.
  - Concorrenza nei prefetch (status list + CRL in parallelo) — scartato: pattern sequential per `LocalStorage` write serial già fissato in design `2026-05-04_offline-status-list-concurrency-fixes.md`.

### [2026-05-09] - CrlChecker Decision Matrix con CrlCheckStatus a 6 Stati
- **Fase:** Planning + Implementation
- **Decisione:** Sostituire l'helper `TrustUtils.isCertRevoked` (catch-all → `false`) con una nuova classe `CrlChecker` in `core/crypto/` che implementa una **decision matrix esplicita 7-righe** parametrizzata su `(online, offline_enabled, cache_state)`. L'esito per-cert `CertCrlOutcome(isRevoked, status: CrlCheckStatus, error)` propaga al `RevocationResult.crlCheckStatus` (nuovo campo, default `VERIFIED`). Aggregazione chain via `worse(a, b)` con ordine di severità pinned dall'**ordinale enum**: `VERIFIED < REFRESHED < STALE_CACHE < CACHE_MISS < FETCH_FAILED < OFFLINE_NO_DATA`. Offline + cache miss → `OFFLINE_NO_DATA + isRevoked=false + warn` (non hard-fail). Online + cache stale → refresh inline (5s timeout via `DefaultCrlHttpFetcher`); su success `REFRESHED` + entry rimpiazzata, su failure `STALE_CACHE` + entry preservata. Wiring: `ProximityVerification.crlCheckerOrNull()` → `VerificationSession.crlCheckerProvider: () -> CrlChecker?` lazy lambda → `DocumentData.toDocumentVerificationResult(crlChecker = ...)`.
- **Rationale:** Il consumer (UI Flutter via plugin) non aveva modo di distinguere "CRL verificata pulita" da "CRL non verificabile, procediamo per default". Il vecchio `isCertRevoked` mascherava il caso CRL_UNREACHABLE come `isRevoked = false` indistinguibile dal caso CRL_FETCHED_AND_CLEAN. Esporre lo stato granulare permette UX informativa (banner "verifica trust limitata"), audit logging conforme, e differenzia hard-fail da "soft-pass con warning". Il pattern severità-per-ordinale è autodocumentante e rende `worse(a, b)` un one-liner; un test pairwise truth-table su 36 coppie + assert sull'ordinale previene regressioni accidentali da reorder durante refactor.
- **Trade-off / Vincoli:**
  - `RevocationResult` retrocompatibile: `crlCheckStatus` è l'**ultimo** campo, default `VERIFIED` — costruzione positionale a 3 argomenti continua a compilare.
  - `CrlChecker` è `internal class` quindi il nuovo overload `verifyNoneOfCertificateIsRevoked(chain, crlChecker)` è anch'esso `internal`. Esposizione pubblica via `RevocationResult.crlCheckStatus` (consumer-visible). Legacy public `verifyNoneOfCertificateIsRevoked(chain)` preservato come `@Deprecated` con `replaceWith`.
  - `CrlCheckStatus.Expired` non esposto: l'enum ha solo i 6 valori effettivi della decision matrix.
  - `clock` di `CrlChecker` ritorna **millis** (consistent con `CrlStore.clock`); divisione `/ 1000` al call site di `CrlStore.isStale(entry, nowSeconds)` (seconds-based). Fissato per evitare drift tra i due clock nei test.
  - Wiring via lambda provider invece di valore eager: `crlStore` è lazy-init dopo enable; il provider rilegge lo stato corrente, evitando closure stale quando offline mode viene toggled durante la sessione.
- **Alternative scartate:**
  - Estendere `isCertRevoked` con un parametro `cache: CrlStore?` — scartato: il branching diventerebbe nested-when impenetrabile; meglio una nuova classe single-responsibility.
  - Fare hard-fail su offline + cache miss — scartato: rompe consumer in scenario degraded-network conclamato; il policy "non revoked + warning + status esposto" è coerente con il pattern offline policy esistente (sez. 14.3 di AGENTS.md).
  - Esporre `worse(a, b)` come API pubblica — scartato: premature abstraction; il consumer non ne ha bisogno (legge solo `RevocationResult.crlCheckStatus` aggregato).
  - Severità via `when`-block esplicito — scartato: 6×6 = 36 branch da mantenere; ordinale è O(1) e self-documenting via ordine dichiarazione.

### [2026-05-09] - getOfflineCacheSnapshot: Friendly Pre-Init + Best-Effort docTypes per-Issuer
- **Fase:** Planning + Implementation
- **Decisione:** Aggiungere `ProximityVerification.getOfflineCacheSnapshot(): OfflineCacheSnapshot` come public suspend API che ritorna una proiezione delle due cache (status list + CRL) in un unico aggregato. Pre-init / errori I/O degradano a snapshot vuoto con `offlineEnabled=false` (mai throw, eccetto `CancellationException` re-thrown). Internamente usa `internal suspend fun buildOfflineCacheSnapshot(sls, crls, nowMillis)` per testability con storage in-memory. Tipi pubblici: `OfflineCacheSnapshot`, `CachedStatusListInfo` (con `usability`, `expiresAtEpochSeconds = iat + ttlSeconds`, context `issuerEntityId/credentialTypes/docTypes`), `CachedCrlInfo` (con `isStale` derivato da `nextUpdateEpochSeconds`). Tutti `@Serializable` per marshalling diretto verso Flutter via `ProximityVerificationPlugin`. `docTypes` su `CachedStatusListInfo` è **best-effort per-issuer** (lista di tutti i docType pubblicati dall'issuer), non per-URI: la granularità per-URI non è disponibile nelle aggregation response IT-Wallet correnti.
- **Rationale:** Il consumer non aveva modo di costruire UI offline-aware (banner di scadenza, hide credenziali con cache inservibile) perché `enableStatusListsForOfflineUse` ritorna solo URI fetched/failed e `RevocationResult` non espone freshness. Lo snapshot è la singola API che aggrega tutte le info che il consumer potrebbe consumare per decisioni UX. Friendly pre-init (snapshot vuoto vs throw) è coerente col pattern delle altre API offline-aware ed evita che il consumer Flutter debba sequenzializzare `initialize → getOfflineCacheSnapshot` (utile per UI iniziali che mostrano "cache vuota" prima dell'auth).
- **Trade-off / Vincoli:**
  - `docTypes` per-URI sarebbe più preciso ma richiederebbe modifiche al protocollo aggregation upstream (non disponibili oggi). Documentato come limitazione in KDoc + CHANGELOG `Notes`. Follow-up: estendere `StatusListPrefetcher` per persistere mapping `uri → docType` quando l'aggregation response lo permette.
  - `Usability.Expired` non popolato dallo snapshot: la decisione `Expired` resta use-time in multipaz (rejection su `exp` claim). Snapshot espone solo `Fresh`/`StaleByTtl`.
  - `CachedStatusList` esteso con tre campi opzionali (`issuerEntityId, credentialTypes, docTypes`) — `ignoreUnknownKeys = true` rende leggibili gli envelope da SDK precedenti senza migrazione. `StatusListStore.put` esteso con tre params opzionali in coda (default-arg, no break).
  - Concorrenza enable+snapshot: lo snapshot riflette ciò che era persistito al momento della chiamata; nessuna garanzia atomica documentata. Pattern coerente con resto dell'SDK.
- **Alternative scartate:**
  - Estendere `StatusListPrefetchSummary` con tutti i campi snapshot — scartato: il summary è output di prefetch (write), non lettura cache. Mescolare le due semantiche confonde il consumer.
  - Snapshot suspend → tornare un `Flow<OfflineCacheSnapshot>` — scartato: i consumer Flutter consumano via plugin sync; un Flow richiederebbe wrapping aggiuntivo. Snapshot one-shot è sufficiente.
  - Hard-throw su pre-init — scartato: rompe UX iniziali "cache vuota mostrata prima del login". Friendly degrade è policy-coerente.

### [2026-05-08] - URI-Based credential_type Filter Best-Effort (IPZS Convention)
- **Fase:** Planning
- **Decisione:** `StatusListPrefetcher` filtra le URI restituite dall'aggregation endpoint **solo** quando l'URI assoluta ha `statuslists` come **primo path component** (`{scheme}://{host}[:port]/statuslists/{credential_type}/{*}`). In tal caso il `credential_type` viene estratto e confrontato col set derivato da `registryData.schemas` (correlazione `(format=mso_mdoc, docType ∈ effectiveDocTypes) → credentialType`). URI fuori-pattern restano sempre incluse nel download (lenient, keep-as-is). URI match-pattern con `credential_type` non rilevante vengono droppate silenziosamente (non finiscono in `failedLists`). `StatusListPrefetchSummary` è invariato.
- **Rationale:** Issuer reali (es. IPZS) pubblicano aggregation con tutte le Status List dell'issuer per qualsiasi credenziale, non solo per i `docTypes` richiesti. Sfruttare il pattern dell'URI come euristica path-based riduce traffico, occupazione cache (no-eviction by design — RATIONALE 2026-04-28) e tempi di prefetch, senza richiedere cambi alla spec o nuovi campi nel registry. Best-effort per-URI evita di disabilitare l'ottimizzazione per un singolo outlier in un mare di URI conformi al pattern.
- **Trade-off / Vincoli:**
  - Il pattern è una **convention IPZS**, non una spec: mai droppare aggressivamente per "pattern unrecognized", o issuer fuori-IPZS perderebbero prefetch.
  - Il confronto `credential_type` ↔ `credentialType` è case-sensitive coerentemente con IT-Wallet.
  - Eventuali URI già in cache da prefetch precedenti restano (no eviction). Cleanup esplicito via `disableStatusListsForOfflineUse()` o `clearLibraryLocalStorage()`.
  - `expectedCredentialTypes` vuoto (Schema Registry assente o nessuna correlazione) ⇒ filtro disabilitato, comportamento legacy.
  - Le metriche di filtro sono solo log `info`, non esposte sul `StatusListPrefetchSummary` (coerente con RATIONALE 2026-04-28 *Logging-Only in Phase 1*; estensione additiva non-breaking se richiesta in futuro).
- **Alternative scartate:**
  - Filtro strict (drop URI non-pattern) — scartato: rompe issuer fuori-IPZS senza beneficio spec.
  - Filtro per-issuer all-or-nothing — scartato: granularità per-URI è strettamente migliore.
  - Hint dichiarativo da Schema Registry (`status_list_uri_template`) — scartato per ora: non esiste oggi nel Schema Registry IT-Wallet; estensione futura retrocompatibile.
  - Filtro al use-time in `MsoRevocationChecker` — scartato: il problema è "scaricare e persistere troppo", al use-time il guadagno è nullo.

### [2026-05-08] - Offline Status List Verification Is Opt-In
- **Fase:** Planning + Implementation
- **Decisione:** Sostituito il modello implicito (cache popolata anche senza azione esplicita del consumer, tramite write-through di `MsoRevocationChecker` su ogni verifica online) con un toggle persistente esplicito (`PROX_VER_OFFLINE_ENABLED`). Default `false` su install fresca. `MsoRevocationChecker` consulta il flag tramite un lambda `isOfflineEnabled` e salta sia `cacheLookup` sia `cachePut` quando è `false`.
- **Rationale:** Alcuni deployment richiedono che la revoca MSO sia validata solo su rete realmente disponibile, mai via cache. Il modello implicito precedente non permetteva di garantirlo: anche un consumer che non chiamava mai `prefetchStatusListsForOfflineUse` accumulava una cache utilizzabile come effetto collaterale del write-through. Il toggle separa "feature attiva" da "ho cache" e rende la disattivazione un'azione esplicita.
- **Trade-off / Vincoli:**
  - Default conservativo `false` ⇒ chi aggiorna da 0.5.x a 0.6.0 senza cambiare codice perde la modalità offline finché non chiama `enableStatusListsForOfflineUse(...)`. Documentato nel CHANGELOG.
  - Flag in chiave `LocalStorage` separata (`PROX_VER_OFFLINE_ENABLED`) anziché dentro l'envelope cache: `isEnabled()` non deserializza la mappa entries, e il gate in `MsoRevocationChecker` può controllare il flag prima di qualunque I/O su cache.
  - `disableStatusListsForOfflineUse()` è due-step (`setEnabled(false)` poi `clearEntries()`): la finestra di race è innocua perché la gate è già `false`.
  - Nessun `Mutex` aggiunto: invariata l'assunzione single-caller di RATIONALE [2026-03-16].
  - Rinomina pubblica `prefetchStatusListsForOfflineUse → enableStatusListsForOfflineUse` senza alias deprecato: cambia semantica (ora flippa il flag) e mantenere il vecchio nome inganneresti i lettori.

### [2026-02-23] - Risk 2 Session Scope Lifecycle
- **Fase:** Planning
- **Decisione:** Adottare scope per-sessione in `ProximityVerification` (creazione in `initialize()`, cancellazione su nuovo `initialize()` e `close()`), con `cancel-previous` sulle verifiche via `activeVerificationJob`.
- **Rationale:** Il ciclo naturale SDK è `init -> N verifiche -> teardown`; il modello per-sessione minimizza job orfani, preserva ownership del lifecycle e mantiene coerenza con `TokenManager` nel scope chiamante.
- **Trade-off / Vincoli:** Esclusi da questo piano Risk 3/4/5; mantenuto `withContext(Dispatchers.Main)` solo sui punti NFC Android per non regredire Risk 1; rimosso `SdkNfcScope`.
- **Alternative scartate:** Scope libreria unico persistente e modello doppio (libreria + sessione), scartati per maggiore complessità e minor allineamento al ciclo d’uso reale.

### [2026-02-23] - Risk 2 Review Notes And Merge Criteria
- **Fase:** Revision
- **Decisione:** Non bloccare su `configureDispatcherProvider` (oggi non usato dai consumer né dai flow interni), accettare copertura lifecycle parziale e consentire scope creep sample iOS senza regressioni funzionali.
- **Rationale:** Priorità a stabilità e merge sicuro: i path completi `startVerification*` dipendono da Bluetooth/NFC difficili da simulare in modo affidabile nel contesto corrente.
- **Trade-off / Vincoli:** Resta monitoraggio futuro se i consumer useranno configurazione runtime dei dispatcher; criterio finale operativo: "MERGE OK CON NOTE".
- **Alternative scartate:** Rendere bloccante la copertura end-to-end hardware-dependent prima del merge (scartata: costo alto e bassa affidabilità nel setup locale).

### [2026-02-23] - Risk 3 Cancellation End-to-End
- **Fase:** Planning
- **Decisione:** Preservare `CancellationException` come control flow in tutti i boundary (`RelyingPartyApiImpl`, `initialize`, `VerificationSession`, cleanup), senza conversione in errori di dominio/callback failure.
- **Rationale:** `CancellationException` è il segnale cooperativo standard di cancellazione del `Job` nelle coroutine; non è un errore funzionale. Trasformarla in errore di dominio rompe la structured concurrency, produce falsi failure callback/retry e impedisce di distinguere stop intenzionale da errore reale.
- **Trade-off / Vincoli:** Nessun redesign architetturale; fix chirurgiche sui catch esistenti con copertura test focalizzata.
- **Alternative scartate:** Introdurre `ApiResult.Cancelled` (scartata: over-engineering, semantica già coperta dalla cancellation cooperativa).

### [2026-02-23] - Stabilizzazione Network Tests Locali
- **Fase:** Revision
- **Decisione:** Rendere i test network indipendenti da runtime Android/JVM non mockato con logger delegate no-op, checker rete iniettabile e `HttpTimeout` nei client mock.
- **Rationale:** Elimina falsi negativi locali non legati al dominio e mantiene il comportamento runtime invariato grazie ai default compatibili.
- **Trade-off / Vincoli:** Aggiunto minimo test plumbing nel costruttore API; asserzioni test riallineate al contratto reale (`InternalError` su HTTP non-success).
- **Alternative scartate:** Ignorare test network e validare solo subset lifecycle (scartata: riduce affidabilità del gate `:proximityverification:build`).

### [2026-02-23] - Risk 3 Review: Copertura Test Cancellation
- **Fase:** Revision
- **Decisione:** Non bloccare il merge sul finding di copertura cancellation end-to-end; mantenere il test lifecycle attuale.
- **Rationale:** La catena reale `startVerification* -> VerificationSession -> transport/backend` non è facilmente testabile in `commonTest` in modo deterministico senza aumentare fragilità.
- **Trade-off / Vincoli:** Accettata copertura parziale rispetto al DoD ideale; confidenza inferiore sui casi profondi di propagazione cancellation.
- **Alternative scartate:** Introdurre test end-to-end più invasivi con mocking/infrastruttura aggiuntiva, scartati per complessità e affidabilità limitata nel contesto corrente.

### [2026-02-23] - Risk 4 Blocking NFC Coroutine Flows
- **Fase:** Revision
- **Decisione:** Eliminato `NfcReaderHandler.android.kt` (dead code, zero istanziazioni) e documentato il contratto `runBlocking` in `NfcReaderCallback` con KDoc esplicito.
- **Rationale:** `runBlocking` è imposto dall'API Android NFC: `onTagDiscovered()` gira su binder thread dedicato e il handle `IsoDep` diventa invalido al ritorno del callback. L'isolamento dalla cancellation del session scope è accettato per preservare l'integrità del protocollo APDU; i risultati stale vengono scartati al boundary `suspendCancellableCoroutine`. Pattern identico al multipaz upstream.
- **Trade-off / Vincoli:** Zero modifiche a runtime; `runBlocking` rimane disconnesso dal session scope (cancellation non propagata durante tag interaction). Doc cleanup in `AGENTS.md`/`CURRENT_STATE.md` rimandato a intervento separato.
- **Alternative scartate:** Consolidare `NfcReaderHandler` in `NfcReaderCallback` (scartata: handler è dead code, nessun merge necessario); wrappare `runBlocking` con cancellation wiring (scartata: violerebbe il contratto Android NFC e rischierebbe stato tag inconsistente).

### [2026-02-24] - Verification Outcome Unificato SDK→Plugin→App
- **Fase:** Planning
- **Decisione:** Introdotto `VerificationOutcome` come callback unico (`onVerificationCompleted`) nel KTM, con bridge plugin JSON (`document_received`/`protocol_failed`) e adattamento app con policy su check ISO + adapter legacy per UI esistente.
- **Rationale:** Elimina il doppio path success/failure, rende espliciti gli esiti strutturali ISO (`PASSED/FAILED/NOT_DETERMINED`) e centralizza il mapping errori infrastrutturali senza leak di logica nel consumer.
- **Trade-off / Vincoli:** Breaking change API KTM/plugin; layer compatibilità in app (`VerificationOutcome -> VerificationResult`) mantenuto per evitare refactor completo delle schermate documento in questo step.
- **Alternative scartate:** Mantenere callback duali con warning flat e sola logica app-side (scartata: ambiguità semantica e alta probabilità di policy divergenti tra consumer).

### [2026-03-11] - V1 Legacy Hard-Cut Removal
- **Decisione:** Rimossa definitivamente tutta la superficie V1 (`VerificationParams`, `ApplicationProviderInfo`, `ProviderInfoMapper`, metodi `initialize`/`startVerification*` a firma legacy, `actual` NFC legacy). Nessun deprecation period aggiuntivo.
- **Rationale:** I consumer downstream erano già completamente migrati a V2 nello step precedente; la compatibility shim (Step A) aveva già comunicato la deprecazione. Un ulteriore ciclo avrebbe aggiunto rumore senza valore.
- **Trade-off / Vincoli:** Breaking change intenzionale e coordinato. `initializeInternal()` lasciata invariata (parametro `providerInfoTypeName` non rimosso) per evitare refactoring speculativo.
- **Alternative scartate:** `DeprecationLevel.ERROR` per un ciclo intermedio (scartata: consumer già migrati, nessun beneficio pratico).

### [2026-03-11] - Step 2 Hard-Cut Legacy Removal
- **Fase:** Revision
- **Decisione:** Completato hard-cut end-to-end rimuovendo superficie legacy (`VerificationResult`, `DocumentInfoCode`, `DocumentWarningCode`) in KTM/plugin e migrando app/example a consumo diretto di `VerificationOutcome` (`DocumentReceived`/`ProtocolFailed`).
- **Rationale:** Una sola semantica risultato su tutto lo stack riduce ambiguità, elimina adapter duplicati e mantiene parità Android/iOS/Flutter sui check strutturali.
- **Trade-off / Vincoli:** Breaking change interno coordinato tra repository; alcuni mapping verso codici errore storici restano solo per telemetria/event logging.
- **Alternative scartate:** Mantenere bridge di compatibilità nel plugin/app per un ciclo aggiuntivo (scartata: debito tecnico persistente e doppio percorso runtime).

### [2026-03-13] - Transport Prerequisite Check in VerificationSession (not ProximityVerification)
> ⚠️ **Decisione rivista in [2026-05-20]** — il pattern descritto qui non è più quello in uso.
- **Decisione originale:** Il pre-flight check sui prerequisiti hardware (BT, NFC) viene eseguito all'interno di `VerificationSession.runVerification*()`, non in `ProximityVerification.startVerification*()`. BLE viene verificato solo se richiesto dalla transport config effettiva; NFC viene verificato solo se richiesto dalla transport config effettiva.
- **Rationale originale:** `VerificationSession` è il proprietario naturale di `defaultTransportConfig`. La config effettiva è già disponibile lì senza dover esporre getter aggiuntivi verso il layer interfaccia. Questo consente di allineare i check hardware al trasporto realmente richiesto, evitando errori falsi positivi come `PVE09` su flow NFC-only.
- **Perché superata:** La motivazione era valida per i flussi QR, ma non considerava il comportamento di `multipaz`: `scanMdocReader` pre-inizializza BLE (`CBPeripheralManager`/`CBCentralManager` via `transport.advertise()`) **prima** della scansione NFC, indipendentemente dal tipo di handover. Con BT spento e metodi BLE in `negotiatedHandoverConnectionMethods`, multipaz lancia `"Excepted poweredOn, got 4"` prima che `executeVerificationFlow` venga mai invocato. Vedere entry [2026-05-20] per la decisione corrente.

### [2026-05-20] - Transport Prerequisite Check Spostato nei startVerification*() Caller
- **Fase:** Bugfix + Refactor
- **Decisione:** `resolveUsableTransportSettings` viene eseguita in tutti i `ProximityVerification.startVerification*()` (QR e NFC), non dentro `executeVerificationFlow`. `executeVerificationFlow` riceve settings già risolti e opera come executor puro.
- **Rationale:** La decisione [2026-03-13] assumeva che `executeVerificationFlow` fosse il primo punto utile per risolvere i settings. Questa assunzione è stata invalidata dal comportamento di `multipaz` (confermato da `identity-credential/multipaz/src/`): `scanMdocReader` chiama `transport.advertise()` su ogni metodo BLE in `negotiatedHandoverConnectionMethods` **prima** di iniziare la scansione NFC (`scanMdocReader.kt:50-63`). Con BT spento, `CBPeripheralManager.waitForPowerOn()` riceve stato 4 (poweredOff) e lancia `"Excepted poweredOn, got 4"` — prima che `executeVerificationFlow` venga mai raggiunto. La risoluzione deve quindi precedere `HandoverBuilder.buildNegotiatedHandoverConnectionMethods`, che è in `scanNfcEngagementInternal`. Per coerenza, lo stesso pattern è applicato al flusso QR. La motivazione originale di [2026-03-13] (evitare PVE09 su flow NFC-only) non è più rilevante: `resolveUsableTransportSettings` gestisce correttamente i flow NFC-only senza lanciare PVE09.
- **Trade-off / Vincoli:** `executeVerificationFlow` mantiene il safety net `runtimeConnectionMethods.isEmpty()` → PVE07. Il suo KDoc documenta il pre-requisito: settings devono essere pre-risolti dal caller.
- **Alternative scartate:** Flag `settingsAlreadyResolved: Boolean` su `executeVerificationFlow` (scartato: complessità non necessaria). Doppio call documentato come idempotente (scartato: checker NFC invocati due volte, RATIONALE contraddetto).

### [2026-03-13] - NET_CAPABILITY_VALIDATED in Android isNetworkAvailable()
- **Decisione:** `NativeUtils.isNetworkAvailable()` su Android richiede sia `NET_CAPABILITY_INTERNET` che `NET_CAPABILITY_VALIDATED`.
- **Rationale:** `NET_CAPABILITY_INTERNET` è dichiarato dal tipo di rete (WiFi, LTE) ma non verificato. `NET_CAPABILITY_VALIDATED` indica che Android ha eseguito un probe HTTP e confermato la connettività reale (nessun captive portal bloccante). Play Integrity richiede internet validato; un check più conservativo evita false positives PVE15 che mascherano il vero errore.
- **Trade-off / Vincoli:** Su alcune configurazioni VPN aziendali, `VALIDATED` può essere `false` anche con internet funzionante. In quei casi il flusso offline (token reuse, RATIONALE.md `14.3`) si attiva correttamente. iOS non è coinvolto.
- **Alternative scartate:** HTTP HEAD probe prima di Play Integrity (scartata: latenza aggiuntiva, dipendenza da endpoint esterno, over-engineering).

### [2026-03-17] - isNetworkAvailable as SDK Usable-Internet Guard
- **Fase:** Revision
- **Decisione:** Rispetto a `develop`, `NativeUtils.isNetworkAvailable()` viene interpretata come guard SDK-wide di "internet realmente usabile per operazioni backend/REST", non come semplice presenza di una rete agganciata.
- **Rationale:** Tutti i call site correnti la usano per decidere se iniziare chiamate backend, attestation, refresh token, registrazione o CRL check; una semantica più stretta è coerente con il requisito prodotto e riduce false positives su reti non realmente utilizzabili.
- **Trade-off / Vincoli:** Android usa `NET_CAPABILITY_VALIDATED`; iOS usa `SCNetworkReachabilityCreateWithName(..., "apple.com")` come approssimazione best-effort, quindi non esiste oggi parità semantica perfetta tra piattaforme.
- **Alternative scartate:** Separare "rete presente" da "internet usabile" (scartata: aggiunge ambiguità e complessità non necessarie, dato che il requisito SDK richiede già connettività realmente utilizzabile).

### [2026-03-17] - ProtocolFailureReason Semantic Routing
- **Fase:** Revision
- **Decisione:** `ProtocolFailureReason` viene esteso con `BLUETOOTH_UNAVAILABLE`, `ATTESTATION_FAILED` e `NETWORK_UNAVAILABLE`; il mapping PVE09 / PVE10-13 / PVE14-15 smette di convergere su `BACKEND_ERROR`.
- **Rationale:** I failure correnti non sono tutti errori backend; il mapping precedente era semanticamente falso, fuorviava la UI consumer e impediva supporto operativo corretto.
- **Trade-off / Vincoli:** Cambio additivo su enum pubblica; richiede coordinamento downstream con plugin/app prima del rilascio finale agli utenti.
- **Alternative scartate:** Mantenere `BACKEND_ERROR` e migliorare solo i messaggi (scartata: non risolve l'ambiguità semantica né l'error routing lato consumer).

### [2026-03-17] - Verification Error Message Clarity
- **Fase:** Revision
- **Decisione:** I catch generici che producono `PVE07` preservano `exception.message`; i messaggi `PVE15` vengono resi neutrali rispetto al tipo di check di connettività sottostante.
- **Rationale:** Il branch introduce preflight e mapping più precisi; senza messaggi diagnostici coerenti, il troubleshooting resta dipendente dai log device e si perde parte del valore della feature.
- **Trade-off / Vincoli:** Il codice errore resta invariato; il contenuto del messaggio resta informativo e non deve essere usato per control flow dai consumer.
- **Alternative scartate:** Lasciare messaggi generici ("Verification failed", "device is not online") (scartata: bassa diagnosabilità e wording incoerente con i nuovi check).


### [2026-03-13] - ClaimDescriptor Data Type Resolution: Heuristic Fallback per ISO 18013-5
- **Fase:** Revision
- **Decisione:** Aggiunto campo opzionale `dataType` a `ClaimDescriptor` (default `"string"`) e lookup table `KNOWN_ELEMENT_TYPES` in `CatalogBridge` con mapping identifier→`DocumentAttributeType` per elementi ISO 18013-5 noti (`birth_date`→Date, `portrait`→Picture, `driving_privileges`→ComplexType, `age_over_*`→Boolean, etc.). Priorità di risoluzione: `value_type` esplicito nel JSON > lookup per identifier > fallback String.
- **Rationale:** OID4VCI §B.2 non prevede un campo tipo per i claim; i metadati issuer esterni non contengono informazioni sul tipo CBOR. Senza heuristica, `MdocDataElement.renderValue()` fallisce su tagged dates (CBOR 1004), byte strings (portrait) e array (driving_privileges), causando fallback a diagnostics per tutti i campi non-stringa.
- **Trade-off / Vincoli:** La lookup table è limitata ai data element ISO 18013-5 noti; document type futuri con identifier non standard ricadono su String. Il campo `value_type` nei built-in JSON resta come override esplicito ma non è atteso dai metadati OID4VCI esterni.
- **Alternative scartate:** (A) Solo `value_type` nel JSON senza heuristica — scartata: inutile per metadati issuer reali che non lo includono. (B) `@ObjCName` per risolvere il conflitto `namespace` su iOS — scartata: `exact=true` non applicabile a proprietà, variante senza `exact` non sovrascrive il mangling del compiler K/N; rinominato in `ns`.

### [2026-03-13] - Built-In Credential Configurations: Costanti Embedded vs Resource Files
- **Fase:** Planning
- **Decisione:** Mantenere i JSON built-in come costanti Kotlin embedded in `BuiltInCredentialConfigurations.kt`, senza file in `resources/` e senza `expect/actual` per il caricamento nativo.
- **Rationale:** La libreria è distribuita sia via CocoaPods che come `.framework` standalone; un framework statico Kotlin/Native non include risorse automaticamente, quindi il consumer dovrebbe bundlare manualmente un `.bundle` separato. Le costanti embedded eliminano questo rischio di crash runtime per risorse mancanti.
- **Trade-off / Vincoli:** I JSON vivono nel bytecode Kotlin (minore separazione dati/codice); manutenzione accettabile dato il numero ridotto di claim (16 mDL + 4 WalletAttestation, allineati al CDDL italiano).
- **Alternative scartate:** (B) `expect/actual` ResourceReader con file JSON nativi — scartata per fragilità nella distribuzione manuale del `.framework` iOS. (C) Ibrido resource + fallback a costanti — scartata per complessità aggiuntiva senza beneficio proporzionale.

### [2026-03-16] - Multi-Step API Code Review: dispatchOutcomeOnCallbackDispatcher raw dispatch
- **Fase:** Revision
- **Decisione:** Accettato l'uso di `CoroutineDispatcher.dispatch()` raw in `dispatchOutcomeOnCallbackDispatcher` e `dispatchProtocolFailureOnCallbackDispatcher` per i path fail-fast pre-coroutine (session not initialized, invalid transport selection).
- **Rationale:** Questi path eseguono al di fuori di un contesto coroutine (non c'è scope in cui lanciare); il dispatch raw è l'unico meccanismo disponibile per onorare il `callbackDispatcher` configurato dal consumer. Pattern già stabilito nel codebase.
- **Trade-off / Vincoli:** Eccezioni nel callback vanno all'uncaught handler del dispatcher, non al chiamante. Accettato: il callback è consumer code, non library code.
- **Alternative scartate:** Lanciare una coroutine effimera con `CoroutineScope(callbackDispatcher).launch { ... }` (scartata: introduce scope orfano senza SupervisorJob, peggiora il problema).

### [2026-03-17] - Fix QR→NFC Data Transfer: Structured Concurrency Deadlock in executeTransportOperationWithTimeout
- **Fase:** Revision
- **Decisione:** Riscritto `executeTransportOperationWithTimeout` in `VerificationSession` per lanciare l'operazione in una coroutine separata (figlia del session scope) e attendere solo il suo *ritorno* via `CompletableDeferred`, non i suoi figli.
- **Rationale:** `NfcTransportMdocReader.open()` (multipaz) lancia un `ioJob` long-lived via `CoroutineScope(currentCoroutineContext()).launch`. Se wrappato in `withTimeoutOrNull`, l'ioJob diventa figlio del timeout scope; structured concurrency impedisce il completamento fino a quando tutti i figli terminano — ma l'ioJob è un loop infinito su `writingQueue.receive()`. Risultato: timeout di 30s che uccide il trasporto prima di qualsiasi scambio APDU con il wallet.
- **Trade-off / Vincoli:** L'ioJob vive nel session scope e non è più soggetto al timeout di `open()`; la pulizia dipende da `MdocTransport.close()` nel teardown della sessione. Accettato: il close è già garantito dal finally block in `runVerificationWithResolvedTransport`.
- **Alternative scartate:** (A) Rimuovere il timeout solo per NFC transport — scartata: non generalizzabile, crea branching per tipo di trasporto. (B) Modificare `NfcTransportMdocReader.open()` in multipaz per detachare l'ioJob — scartata: libreria esterna non sotto il nostro controllo.

### [2026-03-16] - Multi-Step API Code Review: parseQrEngagement thread-safety e single-caller assumption
- **Fase:** Revision
- **Decisione:** `parseQrEngagement()` accede a `verificationSession` sul singleton senza sincronizzazione. Documentato come vincolo d'uso: la libreria assume un singolo caller thread per le API pubbliche.
- **Rationale:** Coerente con il modello pre-esistente di tutti i metodi del singleton `ProximityVerification`. Introdurre locking fine-grained su un object singleton con state mutabile richiederebbe un redesign architetturale fuori scope.
- **Trade-off / Vincoli:** Se un consumer chiama API da thread concorrenti, il comportamento è undefined. Accettato: i consumer reali (Flutter plugin, sample app) operano da un singolo flow sequenziale.
- **Alternative scartate:** `Mutex` su tutte le proprietà mutabili del singleton (scartata: over-engineering per un vincolo già implicito in tutta la codebase).

### [2026-03-12] - Multi-Step Engagement API per ISO 18013-5 Full Coverage
- **Fase:** Planning
- **Decisione:** Introdotto flusso multi-step (parseEngagement → inspect → proceed) nella public API della libreria per coprire tutte le combinazioni engagement/transport ISO 18013-5 dal ruolo mdoc reader. `EngagementResult` unifica i path QR e NFC come tipo di ritorno comune; transport selection esposto all'app con modello "library recommends, app confirms or overrides"; `platformContext: Any?` abilita NFC data transfer dopo QR engagement.
- **Rationale:** L'API one-shot attuale non consente di gestire il caso QR→NFC transport (mancano notifica all'app e platform context); la separazione in fasi riflette il modello ISO (engagement phase → data retrieval phase) e abilita consumer flexibility senza breaking change immediata grazie ai wrapper V1 di compatibilità.
- **Trade-off / Vincoli:** Session Establishment during Negotiated Handover (ISO 8.2.2.4) documentato ma differito; Wi-Fi Aware escluso per scope; `platformContext: Any?` sacrifica type-safety per coerenza col pattern `expect/actual` esistente; wrapper one-shot V1 mantengono il limite attuale (QR→NFC-only fallisce) — fix disponibile solo via multi-step API.
- **Alternative scartate:** API solo multi-step senza wrapper (scartata: breaking change per tutti i consumer senza gradualità); transport selection solo automatica senza override app (scartata: non copre caso QR→NFC dove l'app deve preparare UI e passare platformContext); `expect/actual` overload per `proceed` con tipo platform-specific (scartata: più superficie API per beneficio marginale di type-safety).

### [2026-03-20] - Branch-Aware Build Versioning
- **Fase:** Planning
- **Decisione:** La stringa dichiarata in `proximityverification/build.gradle.kts` viene trattata come `baseVersion`, mentre la versione effettiva degli artifact viene risolta a build time in base al branch corrente: `main -> baseVersion`, `develop -> baseVersion-dev`, altri branch -> `baseVersion-<shortSha>`.
- **Rationale:** Il progetto deve mantenere un solo source of truth leggibile nel file Gradle, ma allo stesso tempo produrre artifact distinguibili tra mainline, develop e branch di lavoro. Centralizzare la regola in Gradle evita duplicazioni tra build diretta via `./gradlew` e flusso manuale via script shell.
- **Trade-off / Vincoli:** La versione nel file sorgente non coincide sempre con la versione finale pubblicata o salvata in `Products/`; i build script devono interrogare Gradle per ottenere la `resolvedVersion` invece di parsare staticamente `version = "..."`. Tag discovery e modelli `projectVersionLast/projectVersionNext` sono esplicitamente fuori scope per questo feature.
- **Alternative scartate:** Calcolare i suffissi solo negli script shell (scartata: `./gradlew` diretto divergerebbe dal flusso scriptato); usare tag discovery o `git describe` come source of truth (scartata: cambia la semantica richiesta, che resta branch-driven); riscrivere i file versionati prima della build (scartata: sporca il working tree e rende fragile il processo).

### [2026-04-07] - Supersede Transport Ownership Assumption
- **Fase:** Revision
- **Decisione:** `TransportConfiguration`, `defaultTransportConfig`, e `transportConfigOverride` sono rimossi dal public/runtime model. `ProximityVerificationSettings` diventa l'unica fonte di verita per policy di trasporto e prompt NFC a runtime.
- **Rationale:** L'assunzione del 2026-03-13 ("ownership naturale in VerificationSession/defaultTransportConfig") non e piu valida con API policy-driven: duplicava il contratto pubblico e generava drift tra configurazione esposta e comportamento effettivo.
- **Trade-off / Vincoli:** Breaking change esplicita su API; campi trasporto consolidati in settings flatten e builder con default paritari.
- **Alternative scartate:** Mantenere override avanzato come secondo livello di configurazione (scartata: reintroduce doppia ownership).

### [2026-04-10] - All-or-Nothing ISO Integrity Verification via DeviceResponse.verify()
- **Fase:** Implementation
- **Decisione:** `DeviceResponse.verify()` sostituisce `DeviceResponseParser` per la verifica ISO 18013-5. Qualsiasi fallimento di integrità (firma issuer, digest, periodo di validità, device authentication) genera `PVE04` e blocca l'estrazione del documento. I quattro `VerificationCheckType` corrispondenti (`ISSUER_SIGNATURE`, `DIGEST_INTEGRITY`, `VALIDITY_PERIOD`, `DEVICE_AUTHENTICATION`) sono stati rimossi.
- **Rationale:** `DeviceResponse.verify()` non espone risultati per-check: lancia `IllegalStateException` su qualsiasi fallimento. Un documento con qualsiasi fallimento di integrità non deve essere estratto né presentato al consumer. Mantenere i quattro check type avrebbe richiesto di hardcodare `PASSED` dopo `verify()`, il che è fuorviante. L'ISO 18013-5 intende la verifica come atomica.
- **Trade-off / Vincoli:** Breaking change downstream (plugin Flutter, app Verifier). Tutti i repository downstream sono sotto controllo del team; il rilascio coordinato mitiga il rischio. Il messaggio di errore da multipaz include il dettaglio del fallimento specifico.
- **Alternative scartate:** Mantenere i 4 check type hardcodando `PASSED` (scartata: fuorviante, aumenta superficie di manutenzione senza valore); parsare il messaggio di `IllegalStateException` per granularità (scartata: fragile, multipaz non garantisce messaggi stabili).

### [2026-04-10] - TrustUtils fetchBytes Consolidation
- **Fase:** Implementation
- **Decisione:** `fetchCrlBytes` e `fetchRevocationPayload` in `TrustUtils` delegano a un unico helper privato `fetchBytes(uri: String)` invece di duplicare il corpo HTTP.
- **Rationale:** I due metodi sono funzionalmente identici. Mantenere corpi separati crea rischio di divergenza silenziosa in caso di future modifiche (es. timeout, header, engine). Il private helper centralizza l'unico punto di manutenzione senza impatto sulla superficie pubblica.
- **Trade-off / Vincoli:** Nessuno. `fetchCrlBytes` e `fetchRevocationPayload` restano pubblici per retrocompatibilità e per consentire test doubles nei consumer che li iniettano via lambda.
- **Alternative scartate:** Fondere i due metodi in uno solo (scartata: nomi semanticamente distinti aiutano la leggibilità nei call site; la deduplicazione è interna).

### [2026-04-24] - Federation Registry Metadata Resolution: federation_resolve over Direct Issuer Queries

<!-- Ingested: 2026-04-24, updated: 2026-04-27 (catalog scope correction) -->

- **Fase:** Implementation
- **Status:** accepted (implemented)
- **Decisione:** Per risolvere metadati credenziali (claims+namespace, display name, schema, status_list_aggregation_endpoint), l'SDK interroga esclusivamente gli endpoint della Federation (resolve/fetch) del Trust Anchor, mai gli endpoint applicativi degli issuer direttamente.
- **Rationale:** L'SDK è una componente mobile della RP che riceve il riferimento al Trust Anchor dal proprio backend. La Entity Configuration dell'issuer, ottenuta via `federation_resolve?sub=<issuer_id>`, contiene la struttura completa dei claim, namespaces, display metadata e `status_list_aggregation_endpoint`. Questa è la fonte canonica secondo la specifica IT-Wallet §7.6.3. Interrogare endpoint applicativi dell'issuer richiederebbe trust e authorization aggiuntivi fuori dal perimetro SDK mobile.
- **Trade-off / Vincoli:** Il Digital Credentials Catalog (§7.6.2) è **lightweight**: fornisce solo `credential_type` e `issuers[].id`. Non contiene `formats`, `docType` né `schema_uri` — queste informazioni risiedono nella Entity Configuration dell'issuer (§7.6.3). Il filtro `mso_mdoc` e l'estrazione del `docType` avvengono in step 5 (federation_resolve dell'issuer). Per il tipo dato dei claim, si usa il Claims Registry (§7.3).
- **Alternative scartate:** (A) Interrogare direttamente gli endpoint issuer — scartata: fuori dal perimetro di trust dell'SDK mobile, richiede authorization non disponibile. (B) Usare solo il Credential Catalog senza federation_resolve — scartata: il catalogo non contiene claims+namespace, display name né `status_list_aggregation_endpoint` nel modello decentralizzato. (C) Filtrare per formato `mso_mdoc` nel catalogo — scartata: il catalogo non espone il campo `formats` (§7.6.2 Table 7.6).

### [2026-04-10] - MSO Revocation Returns NOT_DETERMINED When Offline
- **Fase:** Implementation
- **Decisione:** `MsoRevocationChecker.check()` restituisce `NOT_DETERMINED` quando il dispositivo è offline, quando `revocationStatus` è null, quando il formato è sconosciuto (`RevocationStatus.Unknown`), o quando il fetch/parsing del payload fallisce per qualsiasi eccezione non-cancellation.
- **Rationale:** Comportamento coerente con `ISSUER_CERT_REVOCATION` (CRL check): l'impossibilità di verificare non equivale a revoca. `NativeUtils.isNetworkAvailable()` è il guard SDK-level per internet utilizzabile. Eccezioni di fetch/parsing non devono bloccare la verifica se la revoca non è determinabile.
- **Trade-off / Vincoli:** `CancellationException` è sempre re-thrown per preservare la structured concurrency. Il caching dei payload revoca è differito (YAGNI: una fetch per sessione è accettabile).
- **Alternative scartate:** Restituire `FAILED` in caso di errore di fetch (scartata: assimila impossibilità di verifica a revoca confermata, semantica errata); bloccare la verifica se la revoca non è determinabile (scartata: over-strict, non allineato al comportamento CRL).

### [2026-04-28] - Status List Cache Trust: Use-Time Signature Validation
- **Fase:** Planning
- **Decisione:** Le Token Status List pre-fetched dalla nuova API `prefetchStatusListsForOfflineUse(...)` vengono persistite **senza** verifica di firma al prefetch (`StatusList.fromXxx(..., publicKey = null)`); la firma viene validata al *use time* dentro `MsoRevocationChecker.checkStatusList(...)` usando la chiave pubblica estratta dal `status.certificate` dell'MSO della credenziale verificata, riusando il pattern già in produzione.
- **Rationale:** Al prefetch non è disponibile il `status.certificate` (legato a una specifica credenziale, non all'issuer in astratto). Validare al prefetch richiederebbe portare avanti le JWKs dell'issuer dalla Federation (cache di trust state aggiuntivo, gestione rotation), peggiorando il rapporto costo/beneficio. Il use-time check è già condizione necessaria del flusso esistente: una cache poisoning da aggregation endpoint malevolo risulta bounded a "lista buttata via dal use-time signature check", quindi DoS-shape e non integrity-shape.
- **Trade-off / Vincoli:** La cache può contenere blob non verificati; il `MsoRevocationChecker` deve continuare a invocare `StatusList.fromXxx` con `publicKey = status.certificate?.ecPublicKey` per ogni verifica. Se in futuro l'MSO non porterà più `status.certificate`, il modello va rivisto.
- **Alternative scartate:** (A) Validare la firma al prefetch con le JWKs dell'issuer dalla Federation — scartata: aggiunge superficie trust non motivata dal threat model dell'SDK mobile. (B) Rifiutare al prefetch e validare solo al use time anche le liste fresche — scartata: avrebbe ricreato lo stesso identico controllo già fatto.

### [2026-04-28] - Token Status List Format Support: CWT + JWT End-to-End
- **Fase:** Planning
- **Decisione:** L'SDK estende il supporto Token Status List a **entrambi** i formati registrati dal draft-13 §6 (`application/statuslist+cwt`, `application/statuslist+jwt`), sia nel nuovo prefetch sia nel use-time `MsoRevocationChecker.checkStatusList(...)`. Multipaz espone già `StatusList.fromCwt` e `StatusList.fromJwt`; il dispatch avviene per `Content-Type` al prefetch e per `format` cached al use time.
- **Rationale:** L'aderenza piena alla spec è un requisito esplicito di prodotto. Il use-time precedente era CWT-only di fatto (chiamata diretta a `StatusList.fromCwt`), creando una limitazione silente che avrebbe causato `NOT_DETERMINED` su credenziali con MSO in formato JWT. Estendere il use-time è chirurgico (un `when` aggiuntivo) e atterra insieme al prefetch per evitare asimmetrie tra cache e path online.
- **Trade-off / Vincoli:** Il modello dati cache deve portare un discriminatore `CachedFormat (CWT|JWT)`; `StatusListHttp` deve preservare `Content-Type` nella risposta. Costo aggiuntivo limitato; nessun impatto sulle credenziali con MSO in formato CWT (path invariato).
- **Alternative scartate:** Phase 1 CWT-only con follow-up JWT — scartata: avrebbe formalizzato un'asimmetria spec-divergente; il delta JWT è troppo piccolo per giustificare uno split. Espansione del solo prefetch a JWT senza toccare il use-time — scartata: cache che non viene mai consumata = morta.

### [2026-04-28] - Status List Cache: No Eviction Policy in Phase 1
- **Fase:** Planning
- **Decisione:** Il `StatusListStore` persiste tutte le Status List scaricate sotto un singolo blob JSON nel `LocalStorage` (chiave `PROX_VER_STATUS_LIST_CACHE`) senza alcuna policy di eviction (no LRU, no size cap, no TTL globale di cache). L'unica scadenza riconosciuta è quella spec-defined (`exp` enforced da multipaz, `iat + ttl` come soft-stale). Cleanup esplicito disponibile via `clearLibraryLocalStorage()`.
- **Rationale:** Per la scala IT-Wallet attesa (pochi issuer × poche liste × decine-centinaia di KB ciascuna) il footprint resta nei MB. Aggiungere eviction senza dati di produzione sarebbe over-engineering. Single-blob single-tenant è coerente con `RegistryResolver.STORAGE_KEY` (rationale 2026-04-24).
- **Trade-off / Vincoli:** Se issuer pubblicano partizioni numerose o liste grandi, il cache può crescere. La summary del prefetch resta osservabile e in futuro può alimentare telemetria; l'aggiunta di una policy LRU o size-cap è additiva e non rompe il contratto attuale.
- **Alternative scartate:** Eviction LRU al momento (scartata: complessità senza dati che la giustifichino); chiavi per-issuer separate (scartata: divergenza dal pattern esistente single-tenant); TTL di cache globale aggressivo (scartata: contraddice lo scopo di "andare offline e fidarsi del cache" voluto dal requisito).

### [2026-04-28] - Status List Validation Mode: Logging-Only in Phase 1
- **Fase:** Planning
- **Decisione:** `MsoRevocationChecker.check()` traccia internamente il *modo di validazione* effettivamente usato (`ONLINE_LIVE`, `ONLINE_FALLBACK_CACHE_FRESH`, `ONLINE_FALLBACK_CACHE_STALE`, `OFFLINE_CACHE_FRESH`, `OFFLINE_CACHE_STALE`, `NOT_DETERMINED_*`) e lo loggha a `info`/`warn`. **Non** lo espone su `VerificationOutcome` in Phase 1; l'estensione della superficie pubblica è tracciata come Phase 2 dedicata.
- **Rationale:** Esporre il modo di validazione su `VerificationOutcome` è un'estensione di API utile (evidenza al consumer del fatto che la verifica è avvenuta su cache stale-by-ttl, ad esempio), ma comporta coordinamento downstream (plugin Flutter, app Verifier) coerente con il pattern visto in 2026-02-24 e 2026-03-17. Spostarla a Phase 2 separa la "feature offline" (autosufficiente) dalla "evolutiva di outcome" (che richiede coordinamento). Il logging in Phase 1 garantisce diagnosabilità senza locking della superficie pubblica.
- **Trade-off / Vincoli:** Il modo di validazione resta interno; chi vuole UX diversa per i casi cache stale deve attendere Phase 2 oppure derivare l'informazione dai log device. Il modello dati è già strutturato come enum tipato per non richiedere refactor in Phase 2.
- **Alternative scartate:** Esporre il modo già in Phase 1 (scartata: forza coordinamento downstream nello stesso ciclo della feature offline, allunga il lead time); non tracciarlo affatto (scartata: perdita di osservabilità; debugging cieco delle decisioni `NOT_DETERMINED`).

### [2026-04-28] - IdentifierList Out of Scope for Offline Prefetch
- **Fase:** Planning
- **Decisione:** Il prefetch offline copre **solo** `RevocationStatus.StatusList` (formato bit-based di Token Status List). `RevocationStatus.IdentifierList` mantiene il comportamento online-only attuale (`NOT_DETERMINED` quando offline).
- **Rationale:** Il `status_list_aggregation_endpoint` (Token Status List draft-13 §9) referenzia esclusivamente Status List bit-based; non esiste un canale di aggregazione equivalente per IdentifierList. Implementare un prefetch IdentifierList richiederebbe un meccanismo di discovery diverso o una decisione applicativa proprietaria fuori spec.
- **Trade-off / Vincoli:** Le credenziali il cui MSO referenzia un `IdentifierList` non beneficiano della modalità offline; restano `NOT_DETERMINED` quando offline come oggi. Documentato come limitazione esplicita nel design e nel CHANGELOG.
- **Alternative scartate:** Definire un canale aggregation proprietario per IdentifierList (scartata: out of spec, fuori dal perimetro IT-Wallet); usare l'URI singolo dell'IdentifierList come sorgente di prefetch (scartata: ogni IdentifierList è per-credenziale, non discoverable in anticipo via issuer).

### [2026-05-04] - Status List Prefetch Sequential by Simplicity
- **Fase:** Revision
- **Decisione:** `StatusListPrefetcher.prefetch(...)` esegue **fully sequential** (per-issuer, per-list); rimossi `async`, `coroutineScope`, `Semaphore`, `withContext(Dispatchers.Default)` e la costante `MAX_CONCURRENT_ISSUERS`. `StatusListStore.put(...)` non richiede `Mutex` perché non c'è più scrittura concorrente.
- **Rationale:** La fan-out parallela documentata in `2026-04-28_offline-status-list-prefetch.md` §4 era un'ottimizzazione di latenza, non un requisito funzionale. Alla scala IT-Wallet attesa (handful of issuers × few lists × 5 s timeout per chiamata) la prefetch sequenziale resta ampiamente sotto la finestra UX della consumer app (spinner). La rimozione elimina alla radice tre bug di concorrenza emersi in code review (race read-modify-write su `LocalStorage`, mutazione concorrente di `MutableMap`/`MutableList`, `Dispatchers.Default` introdotto in `commonMain` in violazione del DoD del piano).
- **Trade-off / Vincoli:** Latenza prefetch = somma dei tempi per-issuer invece che max parallelo; se in futuro emergessero issuer con molte liste pesanti, il fan-out può rientrare ma dovrà passare per `Mutex` interno allo store o per un modello "outcome puro + merge sequenziale". `MAX_CONCURRENT_ISSUERS` rimosso dalla superficie pubblica del componente.
- **Alternative scartate:** (A) Mantenere `async` e introdurre `Mutex` interno allo `StatusListStore` (scartata: l'utente ha esplicitamente preferito la soluzione più semplice senza nuovi sync primitive). (B) Parallel HTTP fetch + sequential write con merge per-issuer outcome (scartata: aggiunge moving parts in conflitto con la direttiva "il più semplice possibile").

---

### [2026-05-15] Direct-Federation Fallback per allowed issuers

- **Decisione:** Quando la catena registry IT-Wallet (Steps 2–4 in `RegistryResolver.resolveOnline()`) è irraggiungibile e `allowedIssuerIds` è non-vuoto, il resolver prova un terzo path: federation discovery (Step 1 per ottenere `resolveEndpoint`) + federation resolve diretto per ciascun `allowedIssuerId` (Step 5 equivalente), producendo un `ResolvedRegistryData` sintetico con `schemas = []` e `claimTypes = []`.
- **Rationale:** Gli `allowedIssuerIds` contengono federation entity ID — URL che federano verso il Trust Anchor. La risoluzione diretta via `federation_resolve` rispetta la chain di trust (il Trust Anchor rimane l'anchor); non bypassa la sicurezza, bypassa solo il catalog di aggregazione. Il sintetico manca di arricchimento schema/claim ma è sufficiente per `StatusListPrefetcher` (che usa `issuerMetadata.statusListAggregationEndpoint` e `credentialConfigurations.docType`).
- **Conseguenza:** `enableStatusListsForOfflineUse()` può funzionare anche quando il registry IT-Wallet non è mai stato raggiunto, purché la federation sia up e gli issuer siano configurati.
- **Vincolo rispettato:** Il SDK continua a non interrogare mai gli endpoint dell'issuer direttamente — la risoluzione avviene tramite il `federation_resolve` endpoint del Trust Anchor [RATIONALE 2026-04-24].
- **Cache:** Il sintetico viene persistito sotto la stessa chiave `PROX_VER_REGISTRY_RESOLVED_DATA` e viene sovrascritto al prossimo init online con risoluzione completa.
- **Limitazione nota:** `CatalogCredential.credentialType` nel sintetico usa il `docType` come proxy (es. `"org.iso.18013.5.1.mDL"` invece di `"mDL"`). Questo differisce dal formato breve del catalog IT-Wallet. Consumer che dipendono da `credentialType` per matching devono gestire entrambe le forme.

### [2026-07-31] - Fallback di Verifica Per-Documento sopra `DeviceResponse.verify()`
- **Fase:** Planning + Implementation
- **Decisione:** Mantenere `deviceResponse.verify()` aggregata come **fast path** e, solo in caso di `IllegalStateException`, entrare in un giro per-documento che ricostruisce una `DeviceResponse` mono-documento dal `DataItem` **grezzo** e la verifica con `atTime` clampato dentro la finestra dell'MSO di quel documento (`now.coerceIn(validFrom, validUntil)`). Il giro è **fail-closed** su ciò che non sa isolare: se la response contiene `zkDocuments`, `encryptedDocuments` o `otherDocuments` resta `PVE04`.
- **Rationale:** `verify()` di multipaz è tutto-o-niente e collassa due categorie distinte nello stesso errore: fallimenti d'integrità (firma MSO, docType mismatch, digest, `deviceAuth`) e MSO fuori finestra di validità. Il ktm mappava tutto su `PVE04`, con due conseguenze indesiderate. Primo: l'esito outcome-side previsto per un MSO scaduto (`MSO_VALIDITY = FAILED` → schermata "Documento non leggibile") era **irraggiungibile**, perché la finestra produceva un errore di protocollo. Secondo: in una response multi-documento un solo documento difettoso invalidava anche quelli validi. Il clamp neutralizza **solo** il controllo di finestra dentro multipaz; la finestra è rivalutata da `MsoValidityChecker` con l'orologio reale, quindi non viene ignorata ma spostata dove produce l'esito giusto. Le firme di una `DeviceResponse` sono per-documento (`issuerAuth` firma l'MSO di quel documento, `deviceAuth` è calcolata su `SessionTranscript` + `docType` + `DeviceNameSpacesBytes` di quel documento), quindi estrarre un documento dall'array non invalida nulla di ciò che viene verificato.
- **Trade-off / Vincoli:**
  - Il `DataItem` del documento va passato **grezzo**: ri-serializzare con `MdocDocument.toDataItem()` rischia byte diversi negli `IssuerSignedItemBytes` e invaliderebbe i digest dell'MSO.
  - Una finestra invertita (`validFrom > validUntil`) è un MSO malformato, non scaduto: `coerceIn` solleverebbe, quindi si usa l'orologio reale e l'esito è `PVE04`.
  - Rischio residuo accettato: un documento con integrità genuinamente compromessa abbatte comunque la sessione, anche se l'altro documento è valido.
  - `otherDocuments` è cercato nel CBOR grezzo perché multipaz 0.99.0 non lo modella affatto: non lo parsa e non lo verifica, motivo in più per non dichiarare valida una response che lo contiene.
- **Alternative scartate:** Verificare i soli mdoc e dichiarare successo in presenza di documenti non-mdoc — scartata: trasformerebbe una response contenente qualcosa di crittograficamente rotto in una response valida. Ignorare la finestra di validità dentro multipaz senza rivalutarla outcome-side — scartata: perderebbe il controllo invece di spostarlo.

### [2026-08-03] - `RequestOutcome`: l'SDK Produce l'Inventario, il Consumer il Verdetto
- **Fase:** Planning + Implementation
- **Decisione:** Esporre `VerificationOutcome.DocumentReceived.requestOutcome` — una voce per docType richiesto (`RETURNED` / `DENIED` / `ABSENT`) e, sotto ciascuna, lo stato di ogni claim (`RETURNED` / `WITHHELD` / `ABSENT` / `UNREQUESTED`) — costruito da un `RequestReconciler` puro che confronta la richiesta con i data element **issuer-signed effettivamente presenti**. Nessun `VerificationCheckType` nuovo, nessun verdetto: l'obbligatorietà dei claim resta policy del consumer, dimostrata nei sample da `RequiredClaims`.
- **Rationale:** ISO/IEC 18013-5 **non permette di imporre** un claim: nel `DeviceRequest` l'unico modificatore per elemento è `IntentToRetain`, che è una dichiarazione del *reader* sulle proprie intenzioni di conservazione e non impone nulla al wallet; simmetricamente la risposta è progettata per essere legittimamente parziale (`errors` per elemento, `documentErrors` per documento). L'obbligatorietà esiste quindi solo come regola applicata *a posteriori* dal verificatore, e per applicarla servono i fatti — che prima non arrivavano affatto al consumer: `documentErrors` era solo loggato, `MdocDocument.errors` non era mai letto, e un claim trattenuto era indistinguibile da uno mai richiesto. Non ci si limita a riportare quelle due mappe perché **popolarle è facoltativo** per il wallet (multipaz stesso porta un `TODO` sul tema in `DeviceResponseGenerator`): un'API costruita solo su di esse funzionerebbe contro i wallet ben educati e cieca proprio nei casi che contano, cioè l'omissione silenziosa. Confermato sul campo: l'IT-Wallet non popola nessuna delle due, quindi un claim deselezionato dall'utente arriva `ABSENT` e non `WITHHELD`.
- **Trade-off / Vincoli:**
  - **`WITHHELD` vs `ABSENT` è diagnostica, non probatoria.** Nessuna firma copre `errors`/`documentErrors`: l'MSO firma i digest dei data element *presenti*, `deviceAuth` copre `SessionTranscript` + `docType` + `DeviceNameSpacesBytes`. L'enforcement deve poggiare su `RETURNED` vs non-`RETURNED`, che è il fatto autenticato. Il vincolo è nel KDoc pubblico, non solo qui.
  - Un data element **device-signed** è riportato `ABSENT` perché l'SDK non lo espone in `DocumentVerificationResult.data`: dichiararlo ottenuto prometterebbe un valore introvabile. Il log emette un `WARN` quando ce ne sono, così un `ABSENT` di quel tipo resta visibile.
  - Invariante **non** modificata e resa più evidente: una response senza documenti — vuota, o con solo uno `status` diverso da `0` — resta un `DocumentReceived`, non un `ProtocolFailed`. Osservato sul campo con un wallet che risponde ISO `10` perché non si fida della CA del reader: la causa sta solo nel log del wallet (`CertPathValidatorException`), quindi nessuna modifica lato reader può diagnosticarla. Mappare uno status non-zero su un fallimento di protocollo è un design a sé.
  - Con `allowMultipleRequests` la ktm parsa solo `lastDeviceResponse`: la riconciliazione descrive l'ultima response, non l'unione della sessione. Comportamento preesistente, dichiarato nel KDoc.
- **Alternative scartate:** Esporre solo `errors`/`documentErrors` grezzi — scartata: non rileva l'omissione silenziosa. Un `VerificationCheckType.CLAIM_COMPLETENESS` — scartata: richiederebbe che la libreria sappia quali claim sono obbligatori, cioè policy di prodotto dentro l'SDK. Match alias-aware sui nomi dei claim — scartata: la lookup del registry non è raggiungibile al momento della risposta e funzionerebbe solo in modalità RP con registry risolto, introducendo due comportamenti diversi per ambiente; il match esatto su `(nameSpace, identifier)` è inoltre la lettura corretta del protocollo, dato che il wallet risponde con gli stessi identificatori ricevuti.

### [2026-08-03] - Nomi dei Campi Pubblici vs Export Objective-C (`nameSpace`)
- **Fase:** Revision
- **Decisione:** Il campo di `ClaimOutcome` si chiama **`nameSpace`**, come il `NameSpace` della CDDL ISO/IEC 18013-5, e non `namespace`. Regola generale adottata: prima di pubblicare un campo su un tipo KMP, controllare lo `swift_name` nell'header generato (`./gradlew :proximityverification:linkPodReleaseFrameworkIosArm64`, poi leggere `proximityverification.framework/Headers/proximityverification.h`).
- **Rationale:** Kotlin/Native esporta una proprietà `namespace` in Objective-C come `namespace_` — l'header generato può essere incluso da Objective-C++, dove è parola chiave — e ogni consumatore iOS, plugin Flutter incluso, avrebbe dovuto usare quel nome, che non compare da nessuna parte nel Kotlin e non si spiega da sé. Rinominato in review, **prima** del rilascio della 0.6.9: dopo sarebbe stato un breaking change.
- **Trade-off / Vincoli:** Il repo ha ora due grafie per lo stesso concetto su tipi pubblici diversi — `ClaimDescriptor.ns` (scelto a marzo per la stessa ragione, vedi [2026-03-12]) e `ClaimOutcome.nameSpace`. Uniformarli sarebbe un breaking change su `ClaimDescriptor` e non è stato fatto; la discrepanza è dichiarata qui perché non si deduca che una delle due sia un errore.
- **Alternative scartate:** `@ObjCName` — scartata già a marzo: `exact = true` non è applicabile alle proprietà, e la variante senza `exact` non sovrascrive il mangling del compilatore K/N. Lasciare `namespace` e documentare `namespace_` per i consumatori iOS — scartata: sposta il costo su ogni consumatore, per sempre, per risparmiare un rename fatto una volta prima del rilascio.
