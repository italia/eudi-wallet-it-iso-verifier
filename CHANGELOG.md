# Changelog

Tutte le modifiche rilevanti a questo progetto, a partire dalla versione 0.3.5, verranno documentate in questo file.

Il formato si basa su Keep a Changelog [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
e questo progetto aderisce alla Semantic Versioning [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Versione 0.6.9

### Added
- `ProximityVerification.getReaderCertificate(): ReaderCertificateInfo?` — API read-only che espone il reader/access certificate ottenuto dal verifier backend (subject/issuer, validità, serial, SHA-256 fingerprint, PEM), riletto dal SecureArea; `null` se non registrato.
- `ReaderCertificateInfo` (core/models), con factory `from(X509Cert)`.
- `VerificationOutcome.DocumentReceived.requestOutcome` (`RequestOutcome`, additivo): inventario di ciò che è stato chiesto e di ciò che il wallet ha restituito — una voce per docType richiesto (`RETURNED`/`DENIED`/`ABSENT`) e, sotto ciascuna, lo stato di ogni claim (`RETURNED`/`WITHHELD`/`ABSENT`/`UNREQUESTED`) con il codice grezzo eventualmente dichiarato dal wallet. Abilita lato consumer una policy di obbligatorietà dei claim, che ISO 18013-5 non permette di imporre.
- Nuovi tipi pubblici in `core/models/request`: `RequestOutcome`, `DocumentRequestOutcome`, `DocumentRequestStatus`, `ClaimOutcome`, `ClaimStatus`.
- `errors` e `documentErrors` non sono coperti da firma: una decisione di obbligatorietà va fondata su `RETURNED` vs non-`RETURNED`. Un data element device-signed è riportato `ABSENT`, perché l'SDK non lo espone.
- Log di forma della sessione su `VerificationSession:REQ` e `VerificationSession:RESP` (solo osservabilità): documenti e claim richiesti, documenti ricevuti con numero di data element e quali sono immagini, docType negati, riepilogo del `RequestOutcome`, `WARN` sui data element device-signed. Solo nomi di schema e conteggi, mai valori di data element.
- Sample Android e iOS: `RequiredClaims`, policy di obbligatorietà dimostrativa costruita sul `RequestOutcome`, con card "Required Claims". Vive nei sample, non nell'SDK.

### Changed
- `VerificationOutcome.DocumentReceived` ha un terzo parametro (`requestOutcome`), costruito solo dall'SDK: i consumer lo leggono, non lo costruiscono.

### Fixed
- Un MSO fuori finestra di validità non produce più `PVE04`: `DeviceResponse.verify()` di multipaz è tutto-o-niente, e la verifica ora ricade su un giro per-documento quando quella aggregata fallisce, così la finestra è rivalutata da `MsoValidityChecker` e produce `MSO_VALIDITY = FAILED`. In una response multi-documento un documento difettoso non invalida più quelli validi. Firma MSO, docType, digest e `deviceAuth` continuano a produrre `PVE04`, e il giro per-documento è fail-closed su `zkDocuments`, `encryptedDocuments` e `otherDocuments`.

## Versione 0.6.8

### Fixed
- `MSO_REVOCATION` (token-status-list): una credenziale realmente revocata veniva riportata come `NOT_DETERMINED` invece di `FAILED`. Al momento della verifica (use time) `MsoRevocationChecker` invocava `StatusList.fromJwt/fromCwt` senza chiave di firma: per uno `status_list` in formato JWT il campo `certificate` è opzionale e assente (caso reale IPZS, che veicola la catena firmante nell'`x5c` dell'header del token, come raccomandato da Token Status List draft §11.3 quando Issuer == Status Issuer). Con `publicKey == null` la `require(publicKey != null || caValidated)` di multipaz lanciava (la validazione base della catena non stabilisce fiducia da sola), l'eccezione veniva inghiottita e l'esito collassava a `NOT_DETERMINED`, non applicando la revoca. Ora `decideStatusList` risolve la chiave dall'`x5c`/`x5chain` del token ancorandola ai trust anchor configurati (stesso `TrustManager` della catena issuer, via `StatusListTrust.resolveSigningKey` — già usato dal prefetch), poi legge lo stato. Se la catena non è ancorabile e non c'è certificato embedded → `NOT_DETERMINED` esplicito (nessun throw silenzioso). L'estrazione rispetta il campo `bits` del token (1/2/4/8): verificato con `bits: 4` su `idx` revocato.
- `documentTypeCatalog` non veniva mai azzerato tra un init e l'altro: essendo stato di processo (singleton) e solo additivo (con prune eseguito unicamente sul ramo online-success di `RegistryResolver`), le docType risolte da un registry sopravvivevano a un successivo init. Cambiando ambiente (es. da uno con federation risolta a uno senza), `getRegisteredCredentialConfigurations` mostrava le docType stale del vecchio ambiente mentre `enableStatusListsForOfflineUse` — che legge `resolvedRegistryData`, azzerato a ogni init — falliva con `PVE37`. Ora `cancelSessionScope()` riporta il catalogo al baseline builtin (mDL ISO 18013-5); ogni init lo ricostruisce da builtin + registry corrente (RP via `RegistryResolver.resolve`, standalone via `issuerMetadata`). Nessun impatto sui flussi single-environment.

## Versione 0.6.7

### Added
- `CheckMetadata` (sealed): metadata opzionale e additivo su `VerificationCheck` — `StatusListRevocation` (mode/usability/state), `CrlRevocation` (crlCheckStatus), `MsoValidity`, `DocumentExpiry`.
- `RevocationState` (VALID/REVOKED/SUSPENDED/UNKNOWN) derivato dai bit della token-status-list.
- Check `MSO_VALIDITY` (validità tecnica della credenziale: `validFrom`/`validUntil` firmati nell'MSO).
- Check `DOCUMENT_EXPIRY` (scadenza amministrativa dal claim `expiry_date`, doctype-aware; assente o non parsabile → check non emesso, non bloccante).
- `VerificationOutcome.ProtocolFailed.code` (`String?`, nullable, additivo): codice `PVExx` grezzo allegato in `mapExceptionToOutcome`. Preserva l'identità del codice oltre la `reason` (collassante) per diagnostica e disambiguazione fine lato consumer. `null` per i `ProtocolFailed` costruiti senza eccezione (es. `PLATFORM_CONTEXT_REQUIRED`).

### Changed
- `MsoRevocationChecker.check` ritorna `MsoRevocationOutcome` (status + metadata) invece di `CheckStatus`.
- `ValidationMode` ora è public API.
- `VerificationCheckType` ha due nuovi case (`MSO_VALIDITY`, `DOCUMENT_EXPIRY`).

### Fixed
- Android: un ingaggio NFC annullato/fallito (scan che ritorna `null` → `PVE26`, o prompt NFC chiuso → `PVE29`) non fa più crashare il processo. La scansione girava in un `CoroutineScope(currentCoroutineContext() + …).async { }.await()` che condivide il `Job` padre: l'eccezione veniva sì rilanciata da `await()` (e intercettata da `launchVerification`) ma propagava **anche** lungo la gerarchia del `Job` come eccezione non gestita, uccidendo l'app. Ora la scansione gira in `withContext(promptModel) { … }` (concorrenza strutturata) e l'errore viene rilanciato in modo pulito e recapitato alla callback come fallimento di protocollo. Sistemati entrambi i path NFC (`scanNfcEngagementInternal` ed `executeWithNfcDataTransferTag`).

## Versione 0.6.6

### Fixed
- `CredentialConfigurationParser`: il display di credenziali e claim ora legge `name` con fallback a `label`. Alcuni issuer (es. IPZS pre, giu 2026) emettono il campo `label` (nome SD-JWT-VC) invece del `name` previsto da OID4VCI / IT-Wallet §10.2.5 per il display per-claim: senza fallback le etichette dei claim risultavano vuote nella UI (numero corretto, testo assente), su tutti i consumer. Mitigazione temporanea lato SDK in attesa della correzione lato issuer; quando l'issuer tornerà a `name`, `name` ha la precedenza.

### Added
- Forwarding dei log interni di multipaz (`org.multipaz.util.Logger`) verso il delegate della KTM: gli internals dell'ingaggio/handover NFC (reader mode, `onTagDiscovered`, tag-lost, handover) ora confluiscono nello stesso export in-app dei log KTM invece di restare solo su logcat. Solo osservabilità, gated dal livello KTM: in prod (`INFO`) i log `DEBUG` di multipaz non vengono né prodotti né inoltrati.

### Changed
- Aggiornata la libreria `org.multipaz:multipaz` da `0.98.0` a `0.99.0`. Nessun cambiamento di API pubblica della KTM. Adeguato l'unico punto impattato dalla nuova firma del builder `DocumentType.Builder.addMdocAttribute` (introdotto il parametro `sensitivity: DocumentAttributeSensitivity = PII` prima di `icon`): in `CatalogBridge` gli argomenti `icon`/`sampleValue` sono ora passati per nome, lasciando il default `PII` alla sensitivity. Tutti i target (Android, iOS) compilano e i test unitari passano invariati.

## Versione 0.6.5

### Added
- `ProximityVerification.initializeStandalone(params: StandaloneRelyingPartyParams)`: nuova API per inizializzare l'SDK senza contattare alcun backend RP. Genera un certificato reader self-signed localmente. Destinata a test e sviluppo. Una successiva `initializeRelyingPartyInstance` sovrascrive la sessione standalone.
- `StandaloneRelyingPartyParams`: parametri per la modalità standalone. `trustAnchors = null` (default) = trust-all. `issuerMetadata = null` (default) = catalogo builtin mDL ISO 18013-5.
- `StandaloneIssuerMetadata.FromJson(json)`: passa il JSON OID4VCI del `.well-known/openid-credential-issuer` direttamente (zero network).
- `StandaloneIssuerMetadata.FromUrl(url)`: l'SDK fetcha il `.well-known` dell'issuer all'init (richiede rete verso l'issuer, non verso il backend RP).

#### Notes
- Il certificato reader generato in standalone è self-signed e non firmato da una CA riconosciuta. Alcuni wallet che implementano strict reader auth (ISO 18013-5 §9.1.4) potrebbero rifiutarlo.
- `enableStatusListsForOfflineUse` non è disponibile dopo `initializeStandalone` (lancia eccezione se chiamato).

### Fixed
- `PermissionManager.checkPermissionsOrThrow` ora riceve i resolved `ProximityVerificationSettings`: il check dei permessi BLE (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE) viene eseguito solo se BLE è attivo nei settings. Flussi NFC-only con BLE hardware disattivato non lanciano più `PVE02` né innescano dialog BLE involontari su iOS ([#gap1])
- Il path QR (`parseQrEngagementInternal`) ora lancia `PVE41` (anziché `PVE07`) quando nessun metodo di connessione offerto dal wallet è compatibile con i settings correnti — consistente con il path NFC ([#gap2])

### Changed (sample apps)
- `sampleandroid/MainActivity`: aggiunta richiesta permessi BLE in `onCreate` (upfront, prima di qualsiasi verifica)
- `ProximityDemoApp/ContentView`: `CBCentralManager` istanziato in `.onAppear` per triggerare il permission prompt BLE all'avvio

## Versione 0.6.4

### Fixed
- `RegistryResolver.mapRegistryType` non sovrascrive più il `dataType`
  dei claim pubblicati come `type: "string"` con `format: "data"` o
  `format: "binary"`: ora restituisce `DATA_TYPE_STRING`, lasciando
  che `CatalogBridge.KNOWN_ELEMENT_TYPES` disambigui per identifier
  (`portrait` → `Picture`, `signature_usual_mark` → `Picture`,
  `biometric_template_finger` → `Blob`, ecc.). In precedenza il
  claim `portrait` arrivava nelle app come `Blob` impedendo il
  rendering dell'immagine quando il claims registry IT-Wallet era
  risolto.

## Versione 0.6.3

### Added
- `ProximityVerificationExceptionCode.PVE40` (`OFFLINE ENABLE REQUIRES
  NETWORK CONNECTIVITY`): nuovo codice di errore lanciato da
  `enableStatusListsForOfflineUse` quando il device non ha connettività
  di rete al momento dell'invocazione.

### Changed
- `ProximityVerification.enableStatusListsForOfflineUse(...)` ora esegue
  un pre-check di connettività di rete (`NativeUtils.isNetworkAvailable()`)
  subito dopo la verifica di inizializzazione (`PVE01`) e prima di
  qualsiasi pre-check di federation (`PVE37`/`PVE38`), di qualsiasi
  chiamata a `StatusListPrefetcher`/`CrlPrefetcher`, e di qualsiasi
  scrittura su `StatusListStore`/`CrlStore`. Se il device è offline,
  l'API fa throw immediato di `ProximityVerificationException(PVE40)`
  invece di tentare il prefetch e fallire per timeout. Lo stato
  persistente non viene modificato. Il consumer è responsabile del retry
  quando la connettività ritorna disponibile.
- Il prefetch CRL ora scarica anche la CRL del certificato leaf
  dell'issuer (`x5c[0]`), oltre a quelle di intermediates e root.
  Per ogni issuer registrato la rete riceve fino a una richiesta HTTP
  aggiuntiva per `enableStatusListsForOfflineUse`; la dedup per
  `(issuerDN, serialHex)` e il timeout di 5s su `DefaultCrlHttpFetcher`
  ne limitano il costo, e la cache evita ripetizioni entro la
  finestra di freschezza.
- I certificati privi di estensione `cRLDistributionPoints` non
  compaiono più in `CrlPrefetchSummary.crlsFailed`: vengono loggati
  a WARN e silenziosamente saltati.

### Removed
- `CrlFailureReason.NO_DISTRIBUTION_POINT` rimosso. **Source-breaking**
  per i consumer downstream con espressioni `when` esaustive su
  `CrlFailureReason`: rimuovere il branch `NO_DISTRIBUTION_POINT`.

### Internal
- Rinominato il campo interno `ChainExtractionResult.intermediatesAndRoot`
  in `certificates`. Cambiamento confinato al modulo
  `:proximityverification` (modello `internal data class`); nessun
  impatto su API pubblica.

## Versione 0.6.2

### Added
- `ProximityVerification.getOfflineCacheSnapshot(): OfflineCacheSnapshot`:
  metodo pubblico suspend che restituisce una proiezione delle cache
  offline (Token Status List + CRL) per consumer Kotlin/Swift/Flutter.
  Include `offlineEnabled`, `generatedAtEpochMillis`, lista
  `CachedStatusListInfo` (con `usability` Fresh/StaleByTtl,
  `expiresAtEpochSeconds = iat + ttlSeconds`, context `issuerEntityId` /
  `credentialTypes` / `docTypes`) e lista `CachedCrlInfo` (con `isStale`
  derivato da `nextUpdateEpochSeconds`). Sicuro chiamarlo prima di
  `initializeRelyingPartyInstance`: ritorna snapshot vuoto con
  `offlineEnabled = false`. Errori I/O del `LocalStorage` non sono mai
  propagati: vengono loggati a WARN e l'API degrada a snapshot vuoto.
- Nuovi model `@Serializable` in
  `it.infocert.proximityverification.offline.model`:
  `OfflineCacheSnapshot`, `CachedStatusListInfo`, `CachedCrlInfo`. Pubblici
  per il marshalling diretto verso Flutter via `ProximityVerificationPlugin`.
- `StatusListStore.listAll()`: nuovo metodo internal che ritorna tutte
  le entry persistite (lista vuota su envelope assente o errore I/O).
- Persistence + prefetch + cache-aware trust verification del CRL
  (units 01–03 della stessa intent): `CrlStore`, `CrlPrefetcher`, e
  integrazione di `CrlChecker` nel flusso di trust verification.

### Changed
- `CachedStatusList`: aggiunti tre campi opzionali con default safe —
  `issuerEntityId: String? = null`, `credentialTypes: List<String> =
  emptyList()`, `docTypes: List<String> = emptyList()`. Backward-compat
  garantita da `Json { ignoreUnknownKeys = true; encodeDefaults = true }`:
  gli envelope persistiti da versioni precedenti restano leggibili e i
  nuovi campi assumono i default.
- `StatusListStore.put(uri, payload, meta)` accetta ora tre parametri
  opzionali aggiuntivi (`issuerEntityId`, `credentialTypes`, `docTypes`).
  Backward-compat: i call site esistenti continuano a compilare grazie ai
  default. `StatusListPrefetcher` propaga ora `issuerEntityId` e la lista
  best-effort di `docTypes` pubblicati dall'issuer per la chiamata corrente.
- `StatusListPrefetchSummary`: aggiunto campo opzionale `crlSummary`
  (default null) per riportare anche l'esito del prefetch CRL allo stesso
  consumer.
- `RevocationResult`: aggiunto campo `crlCheckStatus` con default
  `VERIFIED` per il path online che salta la verifica CRL.
- `TrustUtils.isCertRevoked`: deprecato — usare `CrlChecker.checkCert(...)`
  che è cache- e offline-aware. Il path online-only è preservato per
  back-compat con i fixture esistenti.
- `extractCrlDistributionPoint`: visibilità ampliata da `private` a
  `internal` per consentire il riuso da `CrlChecker`.

### Notes
- `docTypes` su `CachedStatusList` / `CachedStatusListInfo` è
  best-effort **per-issuer**, non per-URI: il filtro di aggregazione opera
  per `credentialType` e non esiste un mapping diretto URI → docType nelle
  risposte aggregation. La granularità per-URI è un follow-up separato.
- `CachedStatusListInfo.usability` può valere solo `Fresh` o `StaleByTtl`.
  `Expired` resta riservato alla decisione use-time di multipaz (rejection
  su `exp`) e non è popolato dallo snapshot.
- Carryover Gradle: i test multiplatform sono eseguiti via
  `:proximityverification:test` (Android JVM unit test) anziché
  `:proximityverification:allTests`, in linea con la deviazione delle
  unit precedenti di questa intent.

## Versione 0.6.1

### Fixed
- `ProximityVerification.enableStatusListsForOfflineUse(...)`: aggiunta annotation `@Throws(ProximityVerificationException::class, CancellationException::class)` mancante. Senza, su Kotlin/Native (iOS) le `ProximityVerificationException` (es. `PVE37` quando la federation registry chain non ha prodotto dati, `PVE01` quando chiamata prima di `initializeRelyingPartyInstance`, `PVE38` su docType sconosciuto) non venivano marshallate a `NSError` e causavano una terminazione nativa del processo invece di propagare al consumer Swift/Flutter come errore gestibile. Tutte le altre suspend function pubbliche dell'SDK avevano già l'annotazione (es. `initializeRelyingPartyInstance`, `startVerification`, `clearLibraryLocalStorage`).

## Versione 0.6.0

### Breaking
- **Rinominato** `ProximityVerification.prefetchStatusListsForOfflineUse(docTypes)` → `ProximityVerification.enableStatusListsForOfflineUse(docTypes)`. Nessun alias deprecato. La nuova API, oltre al prefetch, persiste il flag `PROX_VER_OFFLINE_ENABLED = "true"` su rientro strutturalmente riuscito (anche con failure parziali nel summary).
- **Default `disabled`**: alla prima installazione `ProximityVerification.isStatusListsForOfflineUseEnabled()` ritorna `false`. `MsoRevocationChecker` non scrive in cache durante le verifiche online finché il flag non viene attivato esplicitamente. Le entry rimaste in cache da versioni precedenti diventano inerti finché non si chiama `enableStatusListsForOfflineUse(...)` per ripopolarle, oppure `disableStatusListsForOfflineUse()`/`clearLibraryLocalStorage()` per rimuoverle.

### Added
- `ProximityVerification.disableStatusListsForOfflineUse()`: best-effort, scrive `PROX_VER_OFFLINE_ENABLED = "false"` e ripulisce la cache prefetch (`PROX_VER_STATUS_LIST_CACHE`). Chiamabile in qualunque momento, anche prima di `initializeRelyingPartyInstance`.
- `ProximityVerification.isStatusListsForOfflineUseEnabled()`: legge il flag persistito; ritorna `false` su chiave assente, valore malformato o errore I/O.
- `MsoRevocationChecker.check(...)`: nuovo lambda `isOfflineEnabled: suspend () -> Boolean`. Quando ritorna `false` né `cacheLookup` né `cachePut` vengono invocati per il ramo `RevocationStatus.StatusList` (la branch `IdentifierList` resta invariata).
- `ValidationMode.OFFLINE_DISABLED_NOT_DETERMINED`: nuovo modo di logging usato quando il `NOT_DETERMINED` è dovuto al toggle disabilitato (e non a un cache miss).
- Sample app Android e iOS: la home espone uno Switch/Toggle "Offline status list verification" che mappa il flag SDK e mostra un banner persistente sopra le azioni quando la modalità è attiva.

### Changed
- `clearLibraryLocalStorage()` reimposta il flag offline al default `false` come effetto del wipe globale; il commento del metodo è aggiornato di conseguenza.
- `enableStatusListsForOfflineUse(...)`: when issuers expose Status List URIs as `{baseurl}/statuslists/{credential_type}/{*}` (with `statuslists` as the first path component), the prefetcher now downloads only the lists whose `credential_type` is relevant to the requested `docTypes`, correlating via the resolved Schema Registry. URIs that don't match the pattern are kept as-is (best-effort, no regression for non-IPZS issuers). The `StatusListPrefetchSummary` shape is unchanged.

### Migration
Per i consumer che desiderano rimuovere le entry rimaste dalla 0.5.0:
```kotlin
ProximityVerification.disableStatusListsForOfflineUse()
```
Questa singola chiamata, anche prima di `initializeRelyingPartyInstance`, scrive il flag a `false` e svuota il cache envelope.

## Versione 0.5.0

### Added
- `FailureReason.AGGREGATION_HTTP_ERROR` e `FailureReason.FETCH_HTTP_ERROR`: distinguono le risposte HTTP non-success dell'endpoint issuer ("raggiunto ma rifiutato", 4xx/5xx) dalla mancata raggiungibilità transport-level (DNS, connection refused, timeout).
- `FailureReason.FETCH_UNREACHABLE`: dedicato ai soli fallimenti di trasporto sui payload Status List (rimpiazza il precedente `FETCH_FAILED` che collassava entrambi i casi).
- Nuovo helper condiviso `it.infocert.proximityverification.network.logHttpErrorResponse(...)`: per ogni risposta HTTP non-success estrae l'envelope `error` + `error_description` (OpenID Federation §8.9 / convenzione IT-Wallet) dal body e lo logga a `WARN`. Usato da `RegistryHttp` e `StatusListHttp` per allineare il logging diagnostico.
- Sample app Android (Compose) e iOS (SwiftUI): nuovo pulsante "Prefetch Offline Status Lists" che invoca `ProximityVerification.prefetchStatusListsForOfflineUse()` e mostra una card di summary con `requestedDocTypes`, `resolvedIssuers`, conteggi `fetchedLists`/`failedLists`/`failedIssuers` e dettaglio delle ragioni di fallimento per URI/issuer.
- `samples/README.md` aggiornato con la sezione "Offline Status List Prefetch" (snippet Kotlin/Swift, descrizione campi summary, tabella completa delle `FailureReason` e delle eccezioni `PVE01/PVE37/PVE38/PVE39`).

### Changed
- `StatusListHttp.fetchAggregation()` e `fetchStatusListPayload()` ritornano un sealed `FetchResult<T>` interno (`Success` / `Unreachable` / `HttpError(statusCode)` / `Malformed`) invece di `T?`. Consente a `StatusListPrefetcher` di mappare ogni esito al `FailureReason` corretto (in precedenza tutto confluiva indistintamente in `AGGREGATION_UNREACHABLE` / `FETCH_FAILED`).
- `RegistryHttp` non duplica più la logica di parsing dell'error envelope; delega al nuovo `logHttpErrorResponse(...)` condiviso. Comportamento invariato.
- `FailureReason.FETCH_FAILED` rinominato `FETCH_UNREACHABLE` (mai distribuito in release stabile; era introdotto solo nelle voci 0.4.3/0.4.4 pre-rilascio).

### Notes
- `FailureReason` resta una superficie pubblica categoriale: lo status code HTTP preciso e l'`error_description` issuer-specifica finiscono nei log SDK (WARN level), non nel `StatusListPrefetchSummary`. Mantiene l'enum stabile per i consumer Swift/Kotlin che fanno pattern matching e isola il summary da stringhe issuer-dipendenti.
- La voce 0.5.0 raccoglie le iterazioni 0.4.2 / 0.4.3 / 0.4.4 (descritte sotto), nessuna delle quali è stata rilasciata indipendentemente: questa è la prima release dell'intera serie offline-status-list/MSO-revocation/concurrency-fixes su un tag pubblico successivo a 0.4.1.

## Versione 0.4.4

### Changed
- `StatusListPrefetcher` esegue ora fully sequential (rimossi `async`, `Semaphore`, `withContext(Dispatchers.Default)`); elimina i rischi di race su `StatusListStore` segnalati in code review e onora il vincolo "no `Dispatchers.*` in commonMain".
- `StatusListStore.put(...)` propaga ora gli errori non-cancellation invece di swalloware silenziosamente; il use-time write-through resta best-effort grazie al try/catch in `MsoRevocationChecker.checkStatusList`.
- `StatusListPrefetchSummary.fetchedLists` contiene ora solo gli URI effettivamente persistiti; gli errori di scrittura confluiscono in `failedLists[uri] = STORAGE_ERROR`.
- `MsoRevocationChecker` usa internamente `ValidationMode` (enum tipato) al posto di stringhe; la decisione "stale" è centralizzata in `StatusListStore.usability`.
- `StatusListPrefetcher.parseAndValidate` usa `org.multipaz.rpc.handler.InvalidRequestException` per discriminare `EXPIRED_AT_DOWNLOAD` vs `MALFORMED_PAYLOAD` (al posto del precedente match su `String.contains`).

### Notes
- Nessun cambio della superficie pubblica: `prefetchStatusListsForOfflineUse(...)` e `StatusListPrefetchSummary` mantengono la stessa shape; la semantica di `fetchedLists` si è solo ristretta (è ora strettamente equivalente all'effettiva persistenza).

## Versione 0.4.3

### Added
- Public API `ProximityVerification.prefetchStatusListsForOfflineUse(docTypes)` (and no-arg overload) that pre-downloads Token Status Lists from issuer aggregation endpoints (Token Status List draft-13 §9) and persists them locally.
- `MsoRevocationChecker` now uses the cached Status Lists to validate MSO revocation **offline** and as a fallback when the live fetch fails online.
- Token Status List **JWT format** support end-to-end (prefetch + use-time), in addition to the existing CWT support (Token Status List draft-13 §6).
- New error codes: `PVE37` (no aggregation endpoint), `PVE38` (unknown docType in prefetch), `PVE39` (status list cache I/O).
- `StatusListPrefetchSummary` returned by the prefetch API surfaces per-list and per-issuer outcomes (`fetchedLists`, `failedLists`, `failedIssuers`).

### Changed
- Cache write-through: when an MSO revocation check succeeds online, the freshly downloaded Status List payload is also written to the persistent cache so it can serve later offline calls.
- `clearLibraryLocalStorage()` also clears the new Status List cache (`PROX_VER_STATUS_LIST_CACHE`).

### Notes
- `IdentifierList` revocation status is intentionally **not** prefetched (the aggregation endpoint covers only bit-based Status Lists per draft-13 §9).
- The validation mode used (`ONLINE_LIVE`, `ONLINE_FALLBACK_CACHE_FRESH`, `ONLINE_FALLBACK_CACHE_STALE`, `OFFLINE_CACHE_FRESH`, `OFFLINE_CACHE_STALE`) is logged at `info`/`warn`. Surfacing it on `VerificationOutcome` is tracked as a follow-up.

## Versione 0.4.2

### Added
- `MsoRevocationChecker`: verifica revoca MSO tramite StatusList e IdentifierList (ISO/IEC 18013-5 Section 12.3.6). Restituisce `NOT_DETERMINED` se il dispositivo è offline o il formato è sconosciuto.
- `TrustUtils.fetchRevocationPayload(uri)`: recupero payload revoca (StatusList/IdentifierList) via HTTP, speculare a `fetchCrlBytes`.
- `PVE36` / `ProtocolFailureReason.SESSION_TERMINATED_WITHOUT_DEVICE_RESPONSE`: terminazione di sessione lato mDL (`SessionTerminationReceived`) senza payload `DeviceResponse`; distinto da `PVE07` generico (es. `TransportTerminated`).

### Changed
- **BREAKING**: Rimossi `ISSUER_SIGNATURE`, `DIGEST_INTEGRITY`, `VALIDITY_PERIOD`, `DEVICE_AUTHENTICATION` da `VerificationCheckType`. I consumer downstream devono rimuovere i riferimenti a questi valori. I check ISO sono ora enforced all-or-nothing da `DeviceResponse.verify()`.
- `DeviceResponseParser` sostituito da `DeviceResponse.verify()` in `VerificationSession`. La verifica ISO 18013-5 è ora atomica: qualsiasi fallimento genera `PVE04` e blocca l'estrazione del documento.
- Descrizione `PVE04` aggiornata da `"CRYPTO OPERATION EXCEPTION WHILE VERIFYING"` a `"DEVICE RESPONSE INTEGRITY VERIFICATION FAILED"`.

## Versione 0.4.1

### Added
- Nuova API policy-driven: `startVerification(encodedDeviceEngagement, settings, callback, platformContext)` e `startVerificationWithNfc(settings, callback, activity)` con `ProximityVerificationSettings` come unico oggetto di configurazione per ogni transazione di verifica.
- `ProximityVerificationSettings`: configurazione centralizzata che unifica requests, transport preferences, NFC engagement parameters e timeouts.
- `TransportPreference` enum (`AUTO`, `BLE`, `NFC`) per preferenza di trasporto configurabile.
- Nuovi `VerificationStep`: `EngagementParsed`, `TransportSelected(transportType: String)`, `NfcDataTransferReady(message: String)`.
- Introdotto `DocumentTypeCatalog` per gestione catalogo document type con supporto a issuer metadata remoti.
- Introdotto `VerificationRequestBuilder` per costruzione programmatica delle richieste di verifica dal catalogo.
- Nuovi codici di errore: `PVE31` (catalog JSON invalido), `PVE32` (nessuna configurazione mso_mdoc), `PVE33` (fetch issuer metadata fallito), `PVE34` (claim sconosciuto nel builder), `PVE35` (docType sconosciuto nel builder).

### Changed
- **BREAKING**: API pubblica semplificata a due soli entry point (`startVerification` per QR, `startVerificationWithNfc` per NFC). Tutte le precedenti API multi-step sono state rimosse.
- **BREAKING**: `ProximityVerificationSettings` e ora l'unica fonte di verita per policy di trasporto e prompt NFC (`nfcScanMessage`, `nfcTransferMessage`), senza fallback/override separati.
- **BREAKING**: `RelyingPartyInstanceParams` non include piu configurazioni di trasporto; resta solo la configurazione bootstrap (`providerInfo`).
- `multipaz` aggiornata a `0.98.0` (da `0.96.0`); Ktor aggiornato a `3.3.3`; JVM target aggiornato a 17.
- `VerificationStep.TransportSelected` ora usa `transportType: String` (e.g. `"BLE"`, `"NFC"`) al posto di `ReaderTransport`.
- CRL parsing migrata a `multipaz X509Crl` (rimossa implementazione custom).
- Device request generation migrata a `multipaz DeviceRequest.Builder` (rimossa `DeviceRequestGenerator` custom).
- Aggiornate app di esempio Android (Compose) e iOS (SwiftUI) con API policy-driven e configurazione `preferredTransport`.

### Removed
- Rimossi tutti i metodi deprecati: `parseQrEngagement`, `scanNfcEngagement`, `startVerification(EngagementResult, ...)`, `startVerificationWithEngagement`.
- Rimossi tipi interni esposti: `ReaderTransport`, `StartVerificationParams`, `NfcEngagementOptions`, `TransportType`, `ConnectionMethod`, `TransportMapper`.
- **BREAKING**: rimossi `TransportConfiguration`, `defaultTransportConfig` e `transportConfigOverride` dalla superficie pubblica.
- Rimossa dipendenza `multipaz-doctypes`.

## Versione 0.4.0

### Added
- Introdotta API multi-step allineata a ISO 18013-5: fase di engagement separata dalla fase di data retrieval.
- Nuovo metodo `parseQrEngagement(String): EngagementResult` per parsing QR senza avviare la verifica.
- `scanNfcEngagement()` ora restituisce `EngagementResult` (in precedenza `NfcEngagementResult`).
- Nuovo metodo `startVerification(EngagementResult, StartVerificationParams, VerificationCallback, ReaderTransport?, Any?)` per avviare la verifica con transport esplicito.
- Nuovo modello `EngagementResult`: espone `availableTransports`, `recommendedTransport` e `engagementType`.
- Nuova sealed class `ReaderTransport` (`Ble`, `Nfc`) per ispezione e selezione del trasporto da parte dell'app.
- Nuovo step `VerificationStep.TransportSelected(transport)` nel callback di verifica.
- Nuovi valori `ProtocolFailureReason`: `INVALID_TRANSPORT_SELECTION`, `PLATFORM_CONTEXT_REQUIRED`.
- Introdotto `TransportMapper` condiviso in `commonMain` per mapping tra connection method interni e `ReaderTransport` pubblici.
- Factory `EngagementResult.fromNfcScan()` in `commonMain` per eliminare duplicazione Android/iOS nella costruzione da scansione NFC.

### Changed
- `VerificationSession` riceve ora un trasporto pre-risolto: tutta la logica di engagement parsing e transport selection è stata spostata in `ProximityVerification`.
- `EngagementData` resa `internal` (non più parte dell'API pubblica).
- `NfcEngagementResult` resa `internal` (sostituita da `EngagementResult` nell'API pubblica).
- Aggiornate app di esempio Android (Compose) e iOS (SwiftUI) con flusso multi-step e UI di selezione trasporto.
- Documentazione SDK consolidata: unica `INTEGRATION_GUIDE.md` in `docs/` (sostituisce `ProximityVerificationSDK_Docs_All.md`), solo inglese, aggiornata alla multi-step API.
- Ristrutturata cartella documentazione: tutti i documenti ora in `docs/`, unificati `docs/plans/` e `docs/design/`.

## Versione 0.3.16

### Removed
- Rimossa API V1 `initialize(VerificationParams)`: usare `initializeRelyingPartyInstance(RelyingPartyInstanceParams)`.
- Rimosso modello V1 `ApplicationProviderInfo`: usare `RelyingPartyProviderInfo`.
- Rimosso modello V1 `VerificationParams`: usare `RelyingPartyInstanceParams` per l'inizializzazione e `StartVerificationParams` per la verifica.
- Rimosso metodo convenience `startVerification(encodedDeviceEngagement, params, callback)`: usare `parseQrEngagement()` + `startVerification(EngagementResult, ...)`.
- Rimosso metodo convenience `startVerificationWithNfc(activity, params, callback, nfcOptions)`: usare `scanNfcEngagement()` + `startVerification(EngagementResult, ...)`.
- Rimosso metodo convenience `startVerificationWithEngagement(engagementData, params, callback)`: usare `startVerification(EngagementResult, ...)`.
- Rimossa enum `NfcFlowMode` dalle app di esempio (non più necessaria con API multi-step).

## Versione 0.3.15

### Added
- Introdotto `VerificationOutcome` come callback unico. Veicola sia errori di processo che risultato di verifica
- Aggiunte al risultato di verifica informazioni di sessione
- Aggiunte classi `DocumentCheck` per incapsulare la logica dei controlli previsti dalla ISO18013-5

### Changed
- Modificato flusso di verifica ISO18013-5. Supporto iniziale allo stato "revocabile" del MSO.
- `VerificationSession` resa `internal`: la classe di orchestrazione sessione non fa parte della superficie API pubblica e deve essere usata solo internamente tramite `ProximityVerification`.
- Nota di compatibilità: deviazione breaking intenzionale approvata per contesto a singolo consumer.
- Allineamento ISO 18013-5 su `TransportConfiguration`: abilitati di default `bleCentralClientModeEnabled`, `blePeripheralServerModeEnabled`, `nfcEnabled` e `bleUseL2CAPInEngagement`.
- Aggiunto `transportTimeout` a `TransportConfiguration` (default `30s`) con supporto nel builder.
- Introdotto `ConnectionMethodSelector` condiviso in `commonMain` per selezione trasporto coerente tra Android/iOS/QR.

### Fixed
- Corretto il criterio di preferenza BLE: ora viene privilegiato `central_client_mode` quando disponibile, in linea con ISO 18013-5 (§8.3.3.1.1.1).
- Corretto il flow QR: la selezione del connection method ora rispetta `TransportConfiguration` (filtro metodi abilitati + gestione `automaticallySelectTransport`).
- Rimosso il commento fuorviante su Android relativo a static handover forzato.
- Aggiunti timeout applicativi su `transport.open`, `transport.sendMessage` e `receiveMessagesLoop` con errore dedicato `PVE30` per evitare hang indefiniti.

## Versione 0.3.14

### Changed
- Gestione NFC in due step separati. 1. Scansione 2. Comunicazione
- NOTA: La comunicazione tramite NFC necessita di delay tra chiusura canale e apertura nuovo canale di trasporto.
- Logging aggiuntivo
- Aggiornamento app di esempio

### Fixed
- Impossibilità di proseguire dopo scansione NFC su ios

## Versione 0.3.13

### Added
- Priorità configurabile del transport type con controllo del transport preferito

### Changed
- Migliorato controllo sul `refreshToken`
- Aggiornamenti alla sample app iOS per gestione step NFC e scelta del transport preferito
- Logging diagnostico aggiuntivo per `EngagementData` e `NfcEngagementResult`

### Fixed
- Allineato dispatcher NFC Android al Main dispatcher per coerenza con iOS
- Corretto threading BLE su iOS: operazioni CoreBluetooth ora sul Main dispatcher
- Risolta gestione offline di token e materiali crittografici
- Corretta pulizia dei dati di attestazione
- Rimossa stampa non necessaria dei data item letti

## Versione 0.3.12

### Changed
- Allineamento dispatcher NFC Android al Main dispatcher per coerenza con iOS
- Rifattorizzazioni del flusso NFC su iOS per maggiore stabilità
- Migliorata gestione del refresh token con `clearRefreshToken()` e `try/catch` aggiuntivi

### Fixed
- Risolte race condition NFC su Android tramite reset completo dello stato (PromptModel, ReaderMode, NfcScope)
- Risolta terminazione silenziosa delle sessioni CoreNFC su iOS con fallback in foreground polling
- Gestita correttamente la terminazione benigna e la cancellazione utente delle sessioni NFC su iOS
- Gestione tentative errore “Remote service vanished” nel flusso BLE iOS durante il trasferimento dati
- Corretta gestione delle `CancellationException` nelle coroutine

## Versione 0.3.11 

### Added

- Supporto completo per verifica tramite NFC oltre al QR code e BLE
- Nuova classe TransportConfiguration per configurare i trasporti (BLE/NFC) con opzioni predefinite e personalizzabili
- Nuovi modelli dati per la gestione dell’engagement NFC e delle relative opzioni
- API pubbliche per:
- startVerificationWithNfc(): avvio automatico del flusso NFC
- scanNfcEngagement(): scansione NFC con controllo manuale
- Implementazioni native per Android (NfcAdapter) e iOS (Core NFC)
- Nuovi codici di errore dedicati al canale NFC (PVE24–PVE28)
- Aggiornata la documentazione con guida all’integrazione NFC e nuovi esempi nelle app di esempio per Android e iOS

### Changed

- VerificationParams: aggiunto parametro opzionale transportConfig per la configurazione dei trasporti
- VerificationSession: refactoring per supportare engagement multipli e selezione automatica del trasporto
- ProximityVerification: aggiunto metodo interno getVerificationSession() per estensioni platform-specific

### Fixed

#### Propagazione Eccezioni
- **Problema**: Le istanze di `ProximityVerificationException` venivano catturate e re-incapsulate, perdendo il contesto dello stack trace originale
- **Soluzione**: Modificata la gestione delle eccezioni per propagare `ProximityVerificationException` as-is, trasformando solo altri tipi di eccezioni
- **File modificati**: `ProximityVerification.kt`, `ProximityVerificationNfc` (Android/iOS), `IntegrityWarmupManager.kt`
- **Impatto**: Migliore debugging, stack trace più puliti, codici errore preservati correttamente
- **Testing**: ✅ Tutti i 27 test unitari NFC passano

#### Dipendenze UI NFC Rimosse - Riutilizzo Platform.promptModel di multipaz
- **Problema**: L'implementazione NFC richiedeva un `ViewModel` (`AndroidPromptModel`) per funzionare con multipaz su Android
- **Soluzione**: Riutilizzo del singleton `Platform.promptModel` di multipaz:
  1. L'SDK usa il singleton `Platform.promptModel` già fornito e inizializzato da multipaz
  2. L'SDK usa `withContext(promptModel)` per iniettarlo nel contesto coroutine
  3. multipaz lo recupera automaticamente via `AndroidPromptModel.get(coroutineContext)`
  4. **Nessuna gestione richiesta dall'app developer!**
- **Modifiche principali**:
  - **API NFC**: **Rimosso** parametro `promptModel` da `startVerificationWithNfc()` e `scanNfcEngagement()` - API più semplice!
  - **VerificationSession.kt**: Usa `Platform.promptModel` di multipaz direttamente (nessuna creazione custom)
  - **Android**: Usa il singleton `AndroidPromptModel` di multipaz (già inizializzato correttamente)
  - **iOS**: Usa il singleton `IosPromptModel` di multipaz (CoreNFC gestisce i dialoghi nativamente)
  - **Rimossi**: File `PromptModelFactory` (non più necessari)
  - **NfcScanner.android.kt**: Usa `withContext(promptModel)` per context injection pulita
  - **VerificationStep.kt**: Aggiunti 4 nuovi step NFC (`NfcScanningStarted`, `NfcTagDetected`, `NfcEngagementReading`, `NfcEngagementCompleted`)
  - **NfcEngagementOptions.kt**: Arricchito con 13 opzioni di configurazione (timeout, retry, preferenza trasporto, feedback aptico)
- **Benefici**:
  - ✅ **Zero configurazione** - nessun PromptModel da creare o gestire
  - ✅ **API semplicissima** - solo callback e opzioni
  - ✅ Segue esattamente il pattern di multipaz (usa il loro singleton)
  - ✅ Nessuna creazione custom - riutilizza l'infrastruttura esistente
  - ✅ Nessuna interferenza - l'interazione NFC procede naturalmente
  - ✅ L'app ha pieno controllo sulla presentazione UI NFC tramite callback
  - ✅ Pattern di callback consistente tra flussi QR e NFC
  - ✅ Compatibile con qualsiasi framework (Compose, Views, Flutter, React Native, etc.)
  - ✅ Nessun import aggiuntivo richiesto
  - ✅ Codice più semplice e manutenibile
- **Esempio d'uso Android** (aggiornato):
  ```kotlin
  class MainActivity : AppCompatActivity(), VerificationCallback {
      private fun startNfcVerification() {
          // Nessun PromptModel da creare - tutto gestito internamente!
          ProximityVerification.startVerificationWithNfc(
              callback = this,
              nfcOptions = NfcEngagementOptions(...)
          )
      }
  }
  ```
- **Esempio d'uso iOS**:
  ```swift
  ProximityVerification.shared.startVerificationWithNfc(
      callback: self,
      nfcOptions: NfcEngagementOptions(...)
  )
  ```

## Versione 0.3.10

### Changed

- Rinominazione chiavi delle shared preferences

## Versione 0.3.9

### Added

- Gestione interna automatica dei token di autenticazione
- Refresh automatico dei token scaduti
- Re-registrazione automatica del device quando i refresh token sono scaduti
- Supporto platform-specific per attestazione device (cloudProjectNumber per Android, nonce per iOS)
- Parametro `cloudProjectNumber` in `ApplicationProviderInfo` per Android Play Integrity
- Parametro `deviceInfo` in `ApplicationProviderInfo` per registrazione automatica
- Pulizia automatica dei dati di attestazione per prevenire loop di registrazione
- **Valore di ritorno in `initialize()`**: ora restituisce l'access token valido per permettere alle app integranti di utilizzarlo per altre chiamate al backend

### Changed

- **BREAKING**: `attestDevice()` non deve più essere chiamato manualmente, viene invocato automaticamente da `initialize()` quando necessario
- **BREAKING**: Rimosso parametro `accessToken` da `ApplicationProviderInfo` (la gestione è ora interna)
- **BREAKING**: `deviceInfo` è ora obbligatorio in `ApplicationProviderInfo`
- Attestazione device ora gestita automaticamente con logica platform-specific:
  - Android: utilizza `cloudProjectNumber` fornito dall'app
  - iOS: recupera nonce dal backend automaticamente
- Semplificata l'integrazione della libreria: non è più necessario gestire manualmente token, nonce, o chiamate API di registrazione

### Fixed

- Risolto problema di loop di attestazione con pulizia automatica dei dati in caso di errori
- Migliorata gestione errori durante registrazione e re-registrazione device
- Pulizia automatica di token e attestazione in caso di errori di autenticazione (400, 401, 403, 422)

### Migration Guide

**Prima (0.3.8 e precedenti):**
```kotlin
// Android
ProximityVerification.attestDevice("cloudProjectNumber")
val params = VerificationParams(
    providerInfo = ApplicationProviderInfo(
        accessToken = "your-token",
        ...
    )
)
ProximityVerification.initialize(params)
```

**Dopo (0.3.9):**
```kotlin
// Android
val params = VerificationParams(
    providerInfo = ApplicationProviderInfo(
        deviceInfo = DeviceInfo(...),
        cloudProjectNumber = "cloudProjectNumber",
        ...
    )
)
// attestDevice e gestione token sono ora automatici
// initialize() restituisce l'access token per altre chiamate backend
val accessToken = ProximityVerification.initialize(params)
// Usa accessToken per altre chiamate API se necessario
```

## Versione 0.3.8

### Fixed

- Gestione delle shared preferences

## Versione 0.3.7

### Changed

- Timeout di attesa risposta device aumentato da 15 a 300 secondi
- Migliorata la gestione dei log verso le app integranti

### Fixed

- Evitata doppia registrazione dei log centralizzando e controllando il delegate
