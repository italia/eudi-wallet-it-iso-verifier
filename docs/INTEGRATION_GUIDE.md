# ProximityVerification SDK — Integration Guide

Kotlin Multiplatform library for ISO 18013-5 proximity document verification (mdoc reader role).  
Targets Android and iOS (iosArm64).

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Build & Distribution](#build--distribution)
- [Integration — GitLab Package Registry](#integration--gitlab-package-registry)
- [Integration — JFrog Artifactory](#integration--jfrog-artifactory)
- [Integration — Manual (local)](#integration--manual-local)
- [SDK Usage Guide](#sdk-usage-guide)
  - [Initialization](#1-initialization)
  - [Verification (policy-driven API)](#2-verification-policy-driven-api)
  - [Document Type Catalog](#3-document-type-catalog)
  - [Verification Outcome](#4-verification-outcome)
  - [Session Lifecycle](#5-session-lifecycle)
  - [Transport Configuration](#6-transport-configuration)
  - [Logging](#7-logging)
- [Error Codes](#error-codes)
- [NFC Setup & Troubleshooting](#nfc-setup--troubleshooting)

---

## Prerequisites

### Android

| Component | Requirement |
|---|---|
| Gradle | `8.11.1` |
| Android Gradle Plugin (AGP) | `8.9.1` |
| JDK | `17` (target JVM 17) |
| minSdkVersion | `30` |
| compileSdkVersion | `35` |
| Kotlin | `2.2.0` |
| Dependency | `androidx.startup:startup-runtime:1.2.0` |
| Namespace | `it.infocert.proximityverification` |

Android consumers must compile with a Java/Kotlin 17 toolchain. If you maintain a wrapper
module or plugin, align its Android Gradle and Kotlin configuration before upgrading the SDK.

### iOS

| Component | Requirement |
|---|---|
| macOS | `15.1.1` (Sequoia) |
| Xcode | `16.2` |
| CocoaPods | `1.16.2` |
| CocoaPods Art Plugin | `1.1.1` (only for Artifactory) |
| Deployment Target | iOS `16.0` |
| Architecture | `iosArm64` |
| Framework | Static `.framework` |
| Linker flags | `-lsqlite3` |

---

## Build & Distribution

All build scripts are in `build_scripts/`.  
Artifacts are generated in `Products/Android/<VERSION>` and `Products/iOS/<VERSION>`.

```bash
# Set environment variables
export $(cat .env | xargs)

# Build + publish to GitLab
bash build_scripts/update_build_publish_all.sh <VERSION>

# Build only (no publish)
bash build_scripts/update_build_publish_all.sh --no-publish <VERSION>

# Dry run
bash build_scripts/update_build_publish_all.sh --dry-run <VERSION>
```

### Optional: Upload to Artifactory

After building, you can upload artifacts to JFrog Artifactory for integration teams:

- **Android** (Maven repo `<MAVEN_REPO>`): upload contents of `Products/Android/<VERSION>`
- **iOS** (CocoaPods repo `<PODS_REPO>`): upload the `.tar.gz` from `Products/iOS/<VERSION>`

---

## Integration — GitLab Package Registry

### Android

**1. Credentials** — in `~/.gradle/gradle.properties`:

```properties
gitLabPrivateToken=your_private_token
```

**2. Repository** — in `settings.gradle.kts`:

```kotlin
repositories {
    google()
    mavenCentral()
    maven {
        url = uri("<GITLAB_HOST>/api/v4/projects/<PROJECT_ID>/packages/maven")
        name = "GitLab"
        credentials(HttpHeaderCredentials) {
            name = "Private-Token"
            value = gitLabPrivateToken
        }
        authentication { header(HttpHeaderAuthentication) }
    }
}
```

**3. Dependency** — in module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("it.infocert.proximityverification:proximityverification-android:<version>")
}
```

### iOS

Download the `.tar.gz` from GitLab Generic Package Registry:

```bash
curl --fail --location \
  --header "PRIVATE-TOKEN: ${TOKEN}" \
  "${GITLAB_HOST}/api/v4/projects/${PROJECT_ID}/packages/generic/${PACKAGE_NAME}/${VERSION}/${ARCHIVE_NAME}" \
  --output "${DEST_DIR}/${ARCHIVE_NAME}"
tar -xzf "${DEST_DIR}/${ARCHIVE_NAME}" -C "${DEST_DIR}"
```

Then follow the [manual iOS integration](#ios-manual-framework) steps.

---

## Integration — JFrog Artifactory

### Android

**1. Credentials** — in `~/.gradle/gradle.properties`:

```properties
artifactory_user={USERNAME}
artifactory_password={PASSWORD}
artifactory_context_url=https://<ARTIFACTORY_HOST>/artifactory/
```

**2. Repository** — in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = URI("${providers.gradleProperty("artifactory_context_url").get()}<MAVEN_REPO>/")
            credentials {
                username = providers.gradleProperty("artifactory_user").get()
                password = providers.gradleProperty("artifactory_password").get()
            }
        }
    }
}
```

**3. Dependency** — in module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("it.infocert.proximityverification:proximityverification:<version>")
}
```

### iOS

**1. Credentials** — in `~/.netrc`:

```netrc
machine <ARTIFACTORY_HOST>
login {USERNAME}
password {PASSWORD}
```

```bash
chmod 600 ~/.netrc
```

**2. Install cocoapods-art:**

```bash
sudo gem install cocoapods-art
pod repo-art add <PODS_REPO> https://<ARTIFACTORY_HOST>/artifactory/<PODS_REPO>
pod repo-art update <PODS_REPO>
```

**3. Podfile:**

```ruby
source 'https://github.com/CocoaPods/Specs.git'
plugin 'cocoapods-art', :sources => ['<PODS_REPO>']

platform :ios, '16.0'

target 'YourApp' do
  pod 'proximityverification', '<version>'
end
```

```bash
pod install
```

---

## Integration — Manual (local)

### Android — Local Maven Repository

Place artifacts in a Maven-compatible directory:

```
<project-root>/maven-repo/
└── it/infocert/proximityverification/proximityverification-android/<version>/
    ├── proximityverification-android-<version>.aar
    ├── proximityverification-android-<version>.pom
    ├── proximityverification-android-<version>.module
    └── proximityverification-android-<version>-sources.jar
```

In module `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("$rootDir/maven-repo") }
}
dependencies {
    implementation("it.infocert.proximityverification:proximityverification-android:<version>")
}
```

### iOS — Manual Framework

1. In Xcode: **File > Add Files to \<Project\>** → select `proximityverification.framework` → enable "Copy items if needed"
2. **General > Frameworks, Libraries, and Embedded Content** → set to `Embed & Sign`
3. **Build Settings > Framework Search Paths** → add `$(PROJECT_DIR)/<framework-location>` (recursive)

---

## SDK Usage Guide

### Migration Notes

The current public verification surface is policy-driven:

- `startVerification(encodedDeviceEngagement, settings, callback, platformContext?)`
- `startVerificationWithNfc(settings, callback, activity)`

The previous multi-step and legacy entry points are no longer part of the public API:

- `parseQrEngagement()`
- `scanNfcEngagement()`
- `startVerification(engagement, params, callback, ...)`
- `startVerificationWithEngagement(...)`
- `StartVerificationParams`
- `NfcEngagementOptions`

### 1. Initialization

```kotlin
val accessToken: String = ProximityVerification.initializeRelyingPartyInstance(
    RelyingPartyInstanceParams(
        providerInfo = RelyingPartyProviderInfo(
            baseUrl = "https://your-verifier-backend.example/v1",
            csrParam = CSRParam(
                commonName = "device-uuid",
                organization = "YourOrg",
                organizationalUnit = "YourUnit",
                country = "IT"
            ),
            deviceInfo = DeviceInfo(
                platform = "android",  // or "ios"
                appVersion = "1.0.0",
                buildVersion = "1",
                lang = "it"
            ),
            federationEntityUrl = "https://your-verifier-backend.example",
            trustAnchors = listOf("-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----"),
            cloudProjectNumber = "123456789"  // Android only, null on iOS
        ),
        allowedIssuerIds = listOf(
            "https://issuer.eudiw.example/pid",
            "https://issuer.eudiw.example/mdl"
        )
    )
)
```

The SDK automatically handles device attestation (Play Integrity / App Attest), device registration, token storage, refresh, and re-registration. The returned `accessToken` can be used for additional backend calls.

In addition, on every successful initialization the SDK runs the IT-Wallet
**Federation Registry resolution chain** (OpenID Federation 1.0 + IT-Wallet
Registry §7.x) starting from `federationEntityUrl`, restricted to
`allowedIssuerIds`. The resolved `DocumentTypeCatalog` is then ready for
`VerificationRequestBuilder`. Resolution is best-effort: on network/registry
failures the SDK degrades to the cached snapshot from the previous successful
init and, as a last resort, to the built-in catalog defaults.

> **Single-tenant cache.** The persisted resolution cache is global per SDK
> install (one Relying Party backend per install). Changing
> `federationEntityUrl` or `allowedIssuerIds` requires re-calling
> `initializeRelyingPartyInstance(...)` — that path forces a fresh online
> resolution and overwrites the cache.

#### `RelyingPartyProviderInfo`

| Property | Type | Description |
|---|---|---|
| `baseUrl` | `String` | Verifier backend base URL |
| `csrParam` | `CSRParam` | Certificate Signing Request parameters |
| `deviceInfo` | `DeviceInfo` | Device platform, app version, build, language |
| `federationEntityUrl` | `String` | OpenID Federation entity URL of the Relying Party (entry point of the registry resolution chain). Replaces the legacy `wellKnownUrl`. |
| `trustAnchors` | `List<String>` | Root CA certificates in PEM format |
| `cloudProjectNumber` | `String?` | Google Cloud Project Number (Android only) |

#### `RelyingPartyInstanceParams`

| Property | Type | Description |
|---|---|---|
| `providerInfo` | `RelyingPartyProviderInfo` | Backend / federation configuration (see above) |
| `allowedIssuerIds` | `List<String>` | Optional allow-list of OpenID Federation issuer entity IDs the catalog is restricted to. Defaults to `emptyList()`, which disables issuer filtering. |

> **Breaking change vs. previous releases:** `RelyingPartyProviderInfo.wellKnownUrl`
> has been replaced by `RelyingPartyProviderInfo.federationEntityUrl`, and a new
> optional `RelyingPartyInstanceParams.allowedIssuerIds` parameter with default
> `emptyList()` has been introduced. Update call sites that need issuer filtering.

### 2. Verification (policy-driven API)

The SDK exposes one-shot verification entry points. Engagement parsing, runtime transport
selection, and data retrieval are internal to the library.

#### `ProximityVerificationSettings`

Use a single immutable settings object per verification:

```kotlin
val request = VerificationRequestBuilder(ProximityVerification.documentTypeCatalog)
    .forDocType("org.iso.18013.5.1.mDL")
    .requestAllClaims()
    .build()

val settings = ProximityVerificationSettings(
    requests = listOf(request),
    allowMultipleRequests = false,
    preferredTransport = TransportPreference.AUTO,
    bleEnabled = true,
    nfcDataTransferEnabled = true,
    bleCentralClientMode = true,
    blePeripheralServerMode = false,
    bleUseL2CAP = false,
    nfcScanMessage = "Hold near credential holder's device",
    transportTimeout = 30.seconds,
    responseTimeout = 300.seconds
)
```

| Property | Type | Description |
|---|---|---|
| `requests` | `List<VerificationRequest>` | Requests built from `DocumentTypeCatalog` |
| `allowMultipleRequests` | `Boolean` | Keep the transport open for additional requests |
| `preferredTransport` | `TransportPreference` | `AUTO`, `BLE`, or `NFC` |
| `bleEnabled` | `Boolean` | Master BLE enable/disable flag |
| `nfcDataTransferEnabled` | `Boolean` | Enable NFC as transport |
| `bleCentralClientMode` / `blePeripheralServerMode` | `Boolean` | BLE mode selection |
| `bleUseL2CAP` / `bleUseL2CAPInEngagement` | `Boolean` | Enable BLE L2CAP runtime and in engagement |
| `nfcCommandDataFieldMaxLength` / `nfcResponseDataFieldMaxLength` | `Int` | NFC negotiated handover field lengths |
| `nfcScanMessage` | `String` | User-facing message during NFC scan |
| `nfcTransferMessage` | `String` | User-facing message during NFC transfer phase |
| `transportTimeout` / `responseTimeout` | `Duration` | Transport and response timeouts |

#### Transport Migration (before/after)

```kotlin
// Before (removed API)
val params = RelyingPartyInstanceParams(
    providerInfo = providerInfo,
    defaultTransportConfig = TransportConfiguration.default()
)
val settings = ProximityVerificationSettings(
    requests = listOf(request),
    transportConfigOverride = TransportConfiguration.Builder()
        .withBleCentralClientMode(true)
        .withNfcDataTransfer(true)
        .build()
)
```

```kotlin
// After (single source of truth)
val params = RelyingPartyInstanceParams(providerInfo = providerInfo)
val settings = ProximityVerificationSettings(
    requests = listOf(request),
    bleEnabled = true,
    bleCentralClientMode = true,
    blePeripheralServerMode = false,
    nfcDataTransferEnabled = true,
    bleUseL2CAP = false,
    bleUseL2CAPInEngagement = true,
    nfcScanMessage = "Hold near credential holder's device",
    nfcTransferMessage = "Transferring data, keep in reader's field"
)
```

#### QR Code Flow

```kotlin
ProximityVerification.startVerification(
    encodedDeviceEngagement = "mdoc:owBjMS...",
    settings = settings,
    callback = myCallback,
    platformContext = activity
)
```

`platformContext` is only required on Android when a QR engagement resolves to NFC data
transfer. When verification stays on BLE, it is ignored.

#### NFC Flow

```kotlin
ProximityVerification.startVerificationWithNfc(
    settings = settings,
    callback = myCallback,
    activity = activity
)
```

#### Verification Steps

The `VerificationCallback.onVerificationUpdate(step)` callback fires with:

| Step | Description |
|---|---|
| `NfcScanningStarted(message)` | NFC scan started (NFC flow only) |
| `NfcTagDetected` | NFC tag detected |
| `NfcEngagementReading` | Reading engagement data |
| `NfcEngagementCompleted` | Engagement read successfully |
| `EngagementParsed` | Engagement payload parsed and accepted |
| `TransportSelected(transportType)` | Transport resolved (`"BLE"` or `"NFC"`), before connection |
| `NfcDataTransferReady(message)` | QR engagement resolved to NFC data transfer; prompt the user for proximity |
| `Starting` | Verification session starting |
| `Connecting` | Connecting to holder device |
| `SendingRequest` | Sending DeviceRequest |
| `Authenticating` | Waiting for holder consent |
| `Verifying` | Processing response |
| `Completed` | Verification completed |
| `Failed` | Verification failed |

### 3. Document Type Catalog

Build verification requests from the catalog instead of using hardcoded request IDs:

```kotlin
val catalog = ProximityVerification.documentTypeCatalog

// List registered document types
val configurations = catalog.getRegisteredConfigurations()

// Build a request for a specific docType
val request = VerificationRequestBuilder(catalog)
    .forDocType("org.iso.18013.5.1.mDL")
    .requestAllClaims()
    .build()

// Use in verification
val settings = ProximityVerificationSettings(requests = listOf(request))
```

#### How the catalog is populated

At every `initializeRelyingPartyInstance()` call the SDK runs the IT-Wallet Federation Registry resolution chain starting from `federationEntityUrl`. The chain executes these steps in order:

| Step | Source | What it contributes |
|---|---|---|
| 1–2 | RP + TA Entity Configuration | Trust Anchor URL, federation resolve endpoint |
| 3 | `{taUrl}/.well-known/it-wallet-registry` | Catalog, Schema Registry, Claims Registry endpoints |
| 4 | Credential Catalog endpoint | Eligible `credential_type` + issuer IDs; entries with `proximity=false` are excluded |
| 4' | Schema Registry endpoint | Expected `mso_mdoc` docTypes per catalog credential type; absent → step 5 keeps all issuer `mso_mdoc` configs |
| 5 | Federation resolve endpoint, one call per issuer | Parses `credential_configurations_supported`, retains only `mso_mdoc` format entries, then applies the docType allowlist from step 4' if available; display metadata and claims → registered in catalog |
| 6 | Claims Registry endpoint | Claim type enrichment (`type`, `format`, aliases); absent → claims registered with issuer types only |

The Schema Registry and Claims Registry endpoints are discovered in step 3 and may not be present in every Trust Anchor's discovery document. If absent or unreachable, the catalog is still populated from issuer metadata. A single issuer resolve failure (step 5) skips only that issuer.

On network or registry failure the SDK falls back to the cached snapshot from the previous successful init, and as a last resort to the built-in configurations below.

#### Built-in configurations (last resort)

| DocType | Description |
|---|---|
| `org.iso.18013.5.1.mDL` | ISO 18013-5 Mobile Driving License |

> For the full URL-level description of each step see [IT-WALLET-REGISTRY_DISCOVERY.md](IT-WALLET-REGISTRY_DISCOVERY.md).

### 4. Verification Outcome

All verification results are delivered via `VerificationCallback.onVerificationCompleted(outcome)`:

```kotlin
when (outcome) {
    is VerificationOutcome.DocumentReceived -> {
        val documents: List<DocumentVerificationResult> = outcome.documents
        val sessionInfo: SessionInfo = outcome.sessionInfo
        val requestOutcome: RequestOutcome = outcome.requestOutcome
    }
    is VerificationOutcome.ProtocolFailed -> {
        val reason: ProtocolFailureReason = outcome.reason
        val message: String = outcome.message
    }
}
```

`DocumentReceived.requestOutcome` reports, for every requested docType, whether a document came back
and what happened to each requested claim (`RETURNED`, `WITHHELD`, `ABSENT`, `UNREQUESTED`). ISO/IEC
18013-5 cannot make a claim mandatory — a request is a wish-list and a response may legitimately be
partial — so requiring claims is a policy you apply on these facts.

Base that policy on `RETURNED` versus not-`RETURNED`. The `WITHHELD`/`ABSENT` distinction comes from
the wallet's `errors` and `documentErrors` maps, which no signature covers: use it to explain a
missing value, never as evidence that the holder deliberately refused it. Expect `WITHHELD` to be
rare — declaring `errors` is optional for the wallet, and most omit a claim without declaring it.

`DocumentReceived` is also the outcome when **no** document comes back: an empty response, or one
carrying only a non-`0` `status`, is not reported as a protocol failure. Read
`sessionInfo.deviceResponseStatus` to tell a wallet-side abort (ISO/IEC 18013-5 uses `10` for a
general error, `11`/`12` for CBOR errors) from a wallet that simply returned nothing.

**If you see `status: 10` with zero documents, check that the wallet trusts your reader CA first.**
That is the common cause during onboarding, and from the reader side it is indistinguishable from any
other refusal: the wallet reports only a general error. Observed in the field — the wallet accepted and
parsed the request, showed the consent screen, then aborted, and only its own log named the reason
(`CertPathValidatorException: Path does not chain with any of the trust anchors`). Adding the reader
certificate's root to the wallet's trust store resolved it. Nothing on the reader side can detect this,
so it belongs on your integration checklist rather than in error handling.

`ProtocolFailureReason` values include: `TRANSPORT_ERROR`, `TIMEOUT`, `SESSION_TERMINATION`, `BACKEND_ERROR`, `INVALID_TRANSPORT_SELECTION`, `PLATFORM_CONTEXT_REQUIRED`, and others.

### 5. Session Lifecycle

```kotlin
// Initialize — creates session, obtains token
val token = ProximityVerification.initializeRelyingPartyInstance(params)

// Verify from QR engagement
ProximityVerification.startVerification(
    encodedDeviceEngagement = "mdoc:owBjMS...",
    settings = settings,
    callback = myCallback,
    platformContext = activity
)

// Verify from NFC engagement
ProximityVerification.startVerificationWithNfc(
    settings = settings,
    callback = myCallback,
    activity = activity
)

// Close — cancels all in-flight work, releases session
// Must call initializeRelyingPartyInstance() again for further verifications
ProximityVerification.close()

// Clear stored tokens and attestation data
ProximityVerification.clearLibraryLocalStorage()
```

Calling `initializeRelyingPartyInstance()` again cancels any existing session before creating a new one.  
Calling `close()` is idempotent and safe to call multiple times.

### 6. Transport Configuration

Transport configuration is fully owned by `ProximityVerificationSettings`.
There is no separate transport override object in the public API.

### 7. Logging

```kotlin
// Kotlin
Logger.setLogLevel(LogLevel.TRACE)
```

```swift
// Swift
Logger.shared.currentLevel = .trace
```

Available levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `NONE`.

---

## Error Codes

All exceptions inherit from `ProximityVerificationException(message, cause, code)`.

| Code | Name | Description |
|---|---|---|
| PVE00 | GENERIC EXCEPTION | Unclassified generic exception |
| PVE01 | GENERIC INITIALIZATION EXCEPTION | Generic error during initialization |
| PVE02 | MISSING PERMISSION EXCEPTION | Required permissions not granted |
| PVE03 | CRYPTO OPERATION EXCEPTION ON INIT | Cryptographic error during initialization |
| PVE04 | CRYPTO OPERATION EXCEPTION WHILE VERIFYING | Cryptographic error during verification |
| PVE05 | INVALID PARAMETERS EXCEPTION | Invalid parameters provided |
| PVE06 | STORAGE EXCEPTION | Secure storage access failed |
| PVE07 | GENERIC VERIFICATION EXCEPTION | Generic error during verification |
| PVE08 | REST CLIENT RESPONSE GENERIC EXCEPTION | Unclassified backend response error |
| PVE09 | BLUETOOTH UNAVAILABLE EXCEPTION | Bluetooth not available or accessible |
| PVE10 | ATTEST DEVICE NOT SUPPORTED | Device attestation not supported |
| PVE11 | ATTEST DEVICE DATA LEN NOT 32 BYTES | Challenge not 32 bytes (Apple App Attest) |
| PVE12 | DEVICE ATTESTATION GENERIC EXCEPTION | Generic device attestation error |
| PVE13 | DEVICE ATTESTATION NOT EXECUTED | Device offline and no stored attestation |
| PVE14 | DEVICE OFFLINE WITHOUT CERTIFICATE | Device offline and no valid certificate |
| PVE15 | GENERIC NETWORK ERROR | Network error (host unreachable, etc.) |
| PVE16 | NETWORK REQUEST TIMEOUT | HTTP request timed out |
| PVE17 | HTTP 400 BAD REQUEST | Malformed or invalid parameters |
| PVE18 | HTTP 403 FORBIDDEN | Access forbidden |
| PVE19 | HTTP 404 NOT FOUND | Endpoint or resource not found |
| PVE20 | HTTP 422 UNPROCESSABLE ENTITY | Semantically incorrect payload |
| PVE21 | HTTP 500 INTERNAL SERVER ERROR | Backend internal error |
| PVE22 | HTTP 503 SERVICE UNAVAILABLE | Backend temporarily unavailable |
| PVE23 | HTTP 401 UNAUTHORIZED | Authentication failed or token invalid |
| PVE24 | NFC NOT AVAILABLE | NFC hardware not available |
| PVE25 | NFC DISABLED | NFC disabled in system settings |
| PVE26 | NFC ENGAGEMENT FAILED | NFC scan failed or cancelled |
| PVE27 | NFC TAG LOST | NFC tag lost during communication |
| PVE28 | INVALID ENGAGEMENT DATA | Engagement data invalid or corrupted |
| PVE29 | NFC SCAN CANCELED BY USER | User cancelled the NFC scan |
| PVE30 | TRANSPORT OPERATION TIMEOUT | Transport operation timed out |
| PVE31 | CATALOG INVALID JSON | Credential configuration JSON is invalid |
| PVE32 | CATALOG NO MSO_MDOC CONFIGURATION | No mso_mdoc format in credential config |
| PVE33 | CATALOG ISSUER METADATA FETCH FAILED | Failed to fetch issuer metadata |
| PVE34 | REQUEST BUILDER UNKNOWN CLAIM | Unknown claim in request builder |
| PVE35 | REQUEST BUILDER UNKNOWN DOCTYPE | Unknown docType in request builder |

---

## NFC Setup & Troubleshooting

### Android
- NFC hardware availability is checked automatically by the SDK
- User must enable NFC in system settings
- No additional runtime permissions required for NFC

Manual check before offering NFC to users:

```kotlin
import it.infocert.proximityverification.core.handlers.NfcChecker

val hasNfc = NfcChecker.getNfcAvailability()
val nfcEnabled = NfcChecker.isNfcEnabled()
```

### iOS

**Info.plist** — add the NFC usage description:

```xml
<key>NFCReaderUsageDescription</key>
<string>App requires NFC to verify digital documents</string>
```

**Entitlement** — in Xcode: **Signing & Capabilities** → **+ Capability** → **Near Field Communication Tag Reading**. Or in the `.entitlements` file:

```xml
<key>com.apple.developer.nfc.readersession.formats</key>
<array>
    <string>NDEF</string>
    <string>TAG</string>
</array>
```

iOS displays a system prompt automatically when an NFC session starts.

### NFC Troubleshooting

| Symptom | Cause | Solution |
|---|---|---|
| PVE24: "NFC not available" | No NFC hardware | Check `NfcChecker.getNfcAvailability()` before offering NFC |
| PVE25: "NFC disabled" | NFC off in settings | Prompt user: `startActivity(Intent(Settings.ACTION_NFC_SETTINGS))` (Android) |
| PVE26: "NFC engagement failed" | Devices too far apart or interference | Hold devices 2–4 cm apart, retry |
| PVE27: "NFC tag lost" | Devices moved during communication | Hold still during scan |
| PVE29: "NFC scan cancelled" | User cancelled the NFC dialog | Allow retry |
| PVE30: "Transport timeout" | Data transfer took too long | Consider BLE for large payloads |
| iOS: NFC works in debug only | Missing entitlement in release profile | Add NFC entitlement to release provisioning profile |
| iOS: "NFC session failed" | Missing Info.plist entry | Add `NFCReaderUsageDescription` |

### NFC Best Practices

- Check NFC availability before offering the NFC option to users
- Provide clear instructions — NFC requires physical proximity (2–4 cm)
- Handle errors gracefully — NFC can fail due to distance, interference, or user cancellation
- Enable both BLE and NFC in `ProximityVerificationSettings` when you want runtime fallback
- Use NFC engagement + BLE transfer for large payloads (NFC transfer is slower)
- Test on real devices — NFC emulators have limitations

---

---

For the architecture and the verification flow sequence diagrams, see [high-level-design.md](high-level-design.md).  
For the federation registry resolution steps and URLs, see [IT-WALLET-REGISTRY_DISCOVERY.md](IT-WALLET-REGISTRY_DISCOVERY.md).  
For sample app usage and configuration, see [samples/README.md](../samples/README.md).
