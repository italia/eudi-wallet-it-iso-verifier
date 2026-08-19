# ProximityVerification Sample Applications

This directory contains sample applications demonstrating the ProximityVerification SDK's
policy-driven verification flows, including QR engagement and NFC-based verification.

## Sample Apps

### Android Sample (`sampleandroid/`)

An Android application demonstrating mdoc verification using both QR code and NFC engagement methods.

**Features:**
- QR code scanning with SDK-managed transport selection
- NFC engagement with SDK-managed transport selection
- Device attestation and token management
- Real-time verification status updates
- Visual display of verified credentials and photos

**Requirements:**
- Android 11 (API 30) or higher
- NFC hardware (for NFC verification)
- Bluetooth capability
- Camera (for QR scanning)

**Configuration:** `initializeRelyingPartyInstance` performs a Play Integrity device
attestation, which needs the Cloud Project Number of a Google Cloud project of your own.
Add it to the root `local.properties` (untracked):

```properties
pv.cloudProjectNumber=123456789012
```

It reaches the app as `BuildConfig.CLOUD_PROJECT_NUMBER`; without it the build falls back to
a placeholder and the attestation fails. The `initializeStandalone` flow needs no such
configuration. The Relying Party endpoints hardcoded in `MainActivity` point at an
environment you will not have access to: replace them with your own.

### iOS Sample (`ProximityDemoApp/`)

An iOS application with the same verification capabilities as the Android sample.

**Features:**
- QR code scanning with SDK-managed transport selection
- NFC engagement with SDK-managed transport selection
- Device attestation and token management
- Real-time verification status updates
- Visual display of verified credentials and photos

**Requirements:**
- iOS 16.0 or higher
- iPhone 7 or later (for NFC support)
- Bluetooth capability
- Camera (for QR scanning)

---

## Portrait Corroboration

Some credentials carry no photo of their own (residency, `education_*`). For those, the photo of the
holder is fetched from the mDL **in the same proximity session**, and its absence must not block the
verification.

**This is a consumer feature, not an SDK one.** `proximity-verification-ktm` is a reusable library and
knows nothing about corroboration: it logs only facts about the session and exposes the verification
checks (`VerificationCheckType`, `CheckStatus`). The rule and the verdict live in the samples, in
`PortraitCorroboration.kt` / `PortraitCorroboration.swift`.

Both samples expose it in **Request Config → Portrait corroboration → "Corroborate portrait"**. The
button sets the two-`DocRequest` shape prescribed by §4.1 of that design and **declares the intent**:

- every enabled credential, with **all** its claims;
- `org.iso.18013.5.1.mDL` with the **`portrait` claim only**, `intentToRetain = false`.

The intent is *declared*, not inferred from the request every time. That distinction matters: an
earlier version inferred corroboration from the shape ("one claim, and it is `portrait`"), so asking
the mDL for one extra claim silently stopped the recognition and the diagnostics vanished **without an
error** — in testing that reads as "corroboration not requested", which is worse than no diagnostics
at all. Now the declaration is stored in `RequestConfig`, and if the request no longer has the expected
shape the samples say so in red (`Declared, but the request no longer has the expected shape: …`) and
the result card reports `BROKEN REQUEST`.

The same shape can be built by hand with the Request Builder controls; the preset exists because it is
easy to get wrong. The button refuses — explaining why — in the cases the design excludes:

| Refusal | Why |
|---|---|
| the mDL is not registered in the catalog | adding it would fail the whole request with `PVE35`, not just that docType (§4.2) |
| the mDL exposes no `portrait` claim | same, with `PVE34` |
| the enabled credential already carries its own `portrait` | the mDL is a fallback source, not a canonical one (§2, the European Disability Card case) |
| nothing is enabled | there is no credential to corroborate |

After a session the result screen shows a **Portrait Corroboration** card:

- `SATISFIED` — the photo arrived, with a thumbnail and the docType it came from;
- `NOT SATISFIED` — distinguishing "the wallet returned no mDL" (the ordinary case: the holder has no
  driving licence, non-blocking) from "the mDL came back without a `portrait` element";
- `BROKEN REQUEST` — corroboration was declared but the request did not have the expected shape.

The same verdict goes to the sample's own logs under the `SampleCorroboration` tag (`Log.i`/`Log.w` on
Android, `print` on iOS). The SDK's own logs stay factual and are enough to cross-check it: `…:REQ`
lists the requested docTypes with their claim counts, `…:RESP` lists each document's data elements and
**which of them are images, by name** — so a `portrait` that did or did not arrive is readable there —
plus a `WARN` for every docType the wallet denied via `documentErrors`.

When a Tier 0 check of the corroborating mDL failed (`MSO_VALIDITY`, `ISSUER_TRUST`,
`ISSUER_CERT_REVOCATION`), the samples still show the photo — they are diagnostic tools — but say so
explicitly: **production would hide it**, per the `showData` gate of the outcome policy. Those checks
are `VerificationCheckType` values, not strings: renaming a case breaks the samples' compilation
instead of silently never matching again.

The photo is looked up **by identifier**, not as "the first image": an mDL can also return
`signature_usual_mark`, which is an image but not the holder's face.

That identifier is a constant declared in each sample, and the duplication is deliberate. The SDK holds
the same knowledge in `CatalogBridge.KNOWN_ELEMENT_TYPES`, but keeps it `internal`: the authority on
claim naming is the federation registry resolved at runtime, which already publishes per-claim
`aliases`. A constant published as SDK API would assert at compile time something that document can
change, and consumers would have no way to notice. The cost is stated rather than hidden — if the
registry ever publishes an alias for the portrait, the fix touches `KNOWN_ELEMENT_TYPES` and every
consumer.

The rule itself is free of UI dependencies and unit-tested on Android (`PortraitCorroborationTest`); the
Swift file is a faithful port, since Kotlin and Swift share no code here.

---

## Required Claims

ISO/IEC 18013-5 has no way to make a claim mandatory: an `ItemsRequest` is a wish-list — its only
per-element modifier, `IntentToRetain`, is a statement by the reader about its own retention
intentions — and a response may legitimately be partial. Requiring a claim therefore exists only as a
rule the verifier applies *afterwards*, on the facts the SDK reports in
`VerificationOutcome.DocumentReceived.requestOutcome`.

**This is a consumer rule, not an SDK one**, on the same footing as portrait corroboration above: the
SDK produces the inventory, the product issues the verdict. A reusable library that learned one
consumer's notion of "mandatory" would watch it change under its feet.

The samples adopt the strictest rule possible — **everything I asked for must come back** — in
`RequiredClaims.kt` / `RequiredClaims.swift`. It is not the right rule for every product; it is the one
that makes every absence visible, which is the point of a sample. The verdict is either `Complete` or
`Incomplete`, the latter carrying the missing documents (with their `DocumentRequestStatus`) and the
missing claims (with their `ClaimStatus`). Both samples show it in a **"Required Claims"** card under
the results. There is no new configuration UI: the policy applies to whatever the request asked for.

`UNREQUESTED` claims — data that arrived without being requested — never make a response incomplete.

**The verdict rests on `RETURNED`, not on the difference between `WITHHELD` and `ABSENT`.** Those two
states come from the wallet's `errors` and `documentErrors` maps, which no signature covers: they are
useful to explain an absence during testing, unfit to establish that the holder deliberately refused a
claim. The samples display the distinction and do not decide on it.

Expect `WITHHELD` to be rare: declaring `errors` is optional for the wallet and most do not bother, so
a deselected claim usually arrives as `ABSENT` and a credential the wallet does not hold as `ABSENT`
rather than `DENIED`. That is precisely why the SDK reconciles the request against the response instead
of merely reporting those maps.

As with corroboration, the rule is free of UI dependencies and unit-tested on Android
(`RequiredClaimsTest`); the Swift file is a faithful port. Note the field name `ClaimOutcome.nameSpace`,
spelled as in the ISO/IEC 18013-5 CDDL: `namespace` would have crossed into Swift as `namespace_`,
because the generated Objective-C header may be included from Objective-C++, where it is a keyword.


---

## NFC Verification Usage

Both sample apps now include NFC verification functionality alongside the existing QR code + BLE verification.

### Android Sample - NFC Usage

#### 1. Setup

The sample app is already configured with NFC support through
`ProximityVerificationSettings`. No additional runtime permissions are required for pure NFC
engagement.

#### 2. Using NFC Verification

1. **Initialize the SDK**: Tap "Initialize mDL Scan" button
2. **Start NFC Verification**: Tap "Start NFC Verification" button
3. **Tap devices together**: Hold your device close (2-4cm) to the credential holder's device
4. **View results**: Verified credentials will be displayed

#### 3. Key Code Snippets

**QR Verification (single call):**
```kotlin
val settings = ProximityVerificationSettings(
    requests = requests,
    preferredTransport = TransportPreference.AUTO,
    bleCentralClientMode = true,
    nfcDataTransferEnabled = true,
    bleUseL2CAPInEngagement = true,
    nfcTransferMessage = "Transferring data, keep in reader's field"
)
ProximityVerification.startVerification(encodedQrPayload, settings, callback, activity)
```

**NFC Verification (single call):**
```kotlin
val settings = ProximityVerificationSettings(
    requests = requests,
    preferredTransport = TransportPreference.AUTO,
    nfcScanMessage = "Hold near credential holder's device"
)
ProximityVerification.startVerificationWithNfc(settings, callback, activity)
```

### iOS Sample - NFC Usage

#### 1. Setup

The sample app is configured with:
- **NFC entitlements** in `ProximityDemoApp.entitlements`
- **NFC usage description** in `Info.plist`

#### 2. Using NFC Verification

1. **Initialize the SDK**: Tap "Initialize mDL Scan" button
2. **Start NFC Verification**: Tap "NFC Verify" button
3. **System NFC dialog**: iOS will show a native NFC scanning dialog
4. **Tap devices together**: Hold your iPhone close (2-4cm) to the credential holder's device
5. **View results**: Verified credentials will be displayed

#### 3. Key Code Snippets

**QR Verification (single call):**
```swift
let settings = ProximityVerificationSettings(
    requests: requests,
    preferredTransport: .auto_,
    bleCentralClientMode: true,
    nfcDataTransferEnabled: true,
    bleUseL2CAPInEngagement: true,
    nfcTransferMessage: "Transferring data, keep in reader's field"
)
ProximityVerification.shared.startVerification(
    encodedDeviceEngagement: qrPayload,
    settings: settings,
    callback: callback,
    platformContext: nil
)
```

**NFC Verification (single call):**
```swift
let settings = ProximityVerificationSettings(
    requests: requests,
    preferredTransport: .auto_,
    nfcScanMessage: "Hold near credential holder's device"
)
ProximityVerification.shared.startVerificationWithNfc(
    settings: settings,
    callback: callback,
    activity: nil
)
```

---

## Offline Status List Prefetch

Both sample apps demonstrate the SDK's offline revocation support via the
`prefetchStatusListsForOfflineUse(...)` API.

After initialization, tap **"Prefetch Offline Status Lists"** to download and
cache all Token Status Lists (Token Status List draft-13 §9) published by the
issuers resolved during `initializeRelyingPartyInstance()` for the docTypes
in the federation catalog. Once cached, MSO revocation can be checked even
when the device is offline; when online, the live fetch remains primary and
the cache serves as fallback on transient failures.

The summary card shows:

- **Requested docTypes** — the docTypes for which prefetch was performed
  (empty list = all docTypes from the resolved registry)
- **Resolved issuers** — issuer entity IDs whose configurations cover the
  requested docTypes and that expose a `status_list_aggregation_endpoint`
- **Lists fetched** — Status List URIs successfully downloaded and persisted
- **Lists failed** / **Issuers failed** — per-URI / per-issuer failure
  reasons. Transport-level failures distinguish between *unreachable*
  (DNS / connection / timeout) and *http-error* (endpoint reached but
  responded 4xx/5xx). The full set:
  - `AGGREGATION_UNREACHABLE` — aggregation endpoint not reachable
  - `AGGREGATION_HTTP_ERROR` — aggregation endpoint responded 4xx/5xx
  - `AGGREGATION_MALFORMED` — aggregation body could not be parsed as
    `{"status_lists":[...]}` (or contains non-absolute URIs)
  - `FETCH_UNREACHABLE` — status list URI not reachable
  - `FETCH_HTTP_ERROR` — status list URI responded 4xx/5xx
  - `UNSUPPORTED_FORMAT` — content-type not CWT or JWT
  - `MALFORMED_PAYLOAD` — payload bytes failed multipaz parse
  - `EXPIRED_AT_DOWNLOAD` — payload `exp` is already past
  - `STORAGE_ERROR` — local cache I/O failed

  When a `*_HTTP_ERROR` is recorded, the SDK log (WARN level) includes
  the precise status code and any OAuth-style `error` /
  `error_description` envelope returned by the issuer.

### Android — Offline Prefetch Usage

```kotlin
val summary = ProximityVerification.prefetchStatusListsForOfflineUse()
// summary: StatusListPrefetchSummary
//   - requestedDocTypes / resolvedIssuers / fetchedLists
//   - failedLists: Map<String, FailureReason>
//   - failedIssuers: Map<String, FailureReason>
```

A no-arg overload is shown above. To prefetch only specific docTypes:

```kotlin
ProximityVerification.prefetchStatusListsForOfflineUse(
    docTypes = listOf("org.iso.18013.5.1.mDL")
)
```

### iOS — Offline Prefetch Usage

```swift
let summary = try await ProximityVerification.shared
    .prefetchStatusListsForOfflineUse(docTypes: [])
// summary: StatusListPrefetchSummary
//   - requestedDocTypes / resolvedIssuers / fetchedLists
//   - failedLists: [String: FailureReason]
//   - failedIssuers: [String: FailureReason]
```

### Errors

Prefetch can throw the following SDK errors via `ProximityVerificationException`:

| Code | Meaning |
|------|---------|
| **PVE01** | Library not initialized — call `initializeRelyingPartyInstance(...)` first |
| **PVE37** | No issuer in the resolved registry exposes a `status_list_aggregation_endpoint` for the requested docTypes |
| **PVE38** | One of the requested docTypes is not present in the resolved catalog |
| **PVE39** | Status list cache I/O error |

Per-list / per-issuer failures are non-fatal and surfaced via
`StatusListPrefetchSummary.failedLists` and `failedIssuers` (the call
returns successfully even when individual fetches fail).

---

## Verification Methods Comparison

| Feature | QR Code + BLE | NFC |
|---------|--------------|-----|
| **Engagement** | QR code scan | NFC tap |
| **Data Transfer** | Bluetooth LE | NFC or BLE (configurable) |
| **Transport Selection** | SDK resolves from engagement + settings | SDK resolves from engagement + settings |
| **User Action** | Scan QR code | Tap devices together |
| **Range** | Visual line of sight | 2-4cm physical proximity |
| **Speed** | Fast engagement, variable transfer | Fast engagement, variable transfer |
| **Use Case** | When devices can't touch | When devices can touch |

---

## Error Handling

Both sample apps demonstrate comprehensive error handling for NFC-specific scenarios:

### NFC Error Codes

| Code | Meaning | Handling |
|------|---------|----------|
| **PVE24** | NFC not available | Inform user device lacks NFC hardware |
| **PVE25** | NFC disabled | Prompt user to enable NFC in settings |
| **PVE26** | NFC scan failed | Ask user to retry, ensure devices are close |
| **PVE27** | NFC tag lost | Connection interrupted, retry needed |
| **PVE28** | Invalid engagement | Data corrupted, retry engagement |

### Android Error Handling Example

```kotlin
when (exception.code.code) {
    "PVE24" -> Toast.makeText(this, "This device doesn't support NFC", Toast.LENGTH_LONG).show()
    "PVE25" -> /* Show dialog to open NFC settings */
    "PVE26" -> Toast.makeText(this, "NFC scan failed. Please try again.", Toast.LENGTH_SHORT).show()
    // ... other cases
}
```

### iOS Error Handling Example

```swift
switch error.code.code {
case "PVE24":
    showAlert(title: "NFC Not Available", message: "This device doesn't support NFC...")
case "PVE26":
    showAlert(title: "NFC Scan Failed", message: "Please try again...")
// ... other cases
}
```

---

## Building and Running

### Important: Using Updated SDK

The sample apps have been updated to demonstrate NFC functionality. To use them:

1. **Build and publish the SDK locally:**
   ```bash
   cd proximityverification
   ./gradlew publishToMavenLocal
   ```

2. **Update sample app dependency** to use the latest local version (0.3.1+)

3. **Build the sample app**

Alternatively, wait for the next SDK release that includes NFC support.

### Android

```bash
cd samples/sampleandroid
./gradlew build
```

Or open in Android Studio and run directly on a device with NFC.

**Current Status:** The Android sample includes NFC code examples but requires the updated SDK to be published first.

### iOS

```bash
cd samples/ProximityDemoApp
pod install
open ProximityDemoApp.xcworkspace
```

Build and run on a physical device (iPhone 7 or later) with NFC capability.

**Current Status:** The iOS sample includes NFC code examples but requires the updated SDK framework to be built first.

**Note:** NFC functionality requires physical devices; it won't work in simulators/emulators.

---

## Configuration Options

Both apps use `ProximityVerificationSettings` for per-verification configuration:

```kotlin
// Android
val settings = ProximityVerificationSettings(
    requests = requests,
    preferredTransport = TransportPreference.AUTO,  // AUTO, BLE, or NFC
    bleCentralClientMode = true,
    blePeripheralServerMode = false,
    nfcDataTransferEnabled = true,
    bleUseL2CAP = true,
    bleUseL2CAPInEngagement = true,
    nfcScanMessage = "Hold near credential holder's device",
    nfcTransferMessage = "Transferring data, keep in reader's field"
)
```

```swift
// iOS
let settings = ProximityVerificationSettings(
    requests: requests,
    preferredTransport: .auto_,  // .auto_, .ble, or .nfc
    bleCentralClientMode: true,
    blePeripheralServerMode: false,
    nfcDataTransferEnabled: true,
    bleUseL2CAP: true,
    bleUseL2CAPInEngagement: true,
    nfcScanMessage: "Hold near credential holder's device",
    nfcTransferMessage: "Transferring data, keep in reader's field"
)
```

---

## Testing Tips

### For NFC Verification

1. **Use real devices**: NFC doesn't work in emulators/simulators
2. **Check NFC is enabled**: Both devices must have NFC enabled
3. **Hold devices close**: 2-4cm is optimal, not touching
4. **Avoid metal objects**: Can interfere with NFC signal
5. **Check alignment**: NFC antennas are usually near the top of phones
6. **Be patient**: Initial connection may take 2-3 seconds

### For QR Code Verification

1. **Good lighting**: Ensure QR code is clearly visible
2. **Steady hand**: Hold camera steady for quick scanning
3. **BLE permissions**: Ensure Bluetooth permissions are granted
4. **BLE enabled**: Bluetooth must be on for data transfer

---

## Additional Resources

- [ProximityVerification SDK Documentation](../docs/INTEGRATION_GUIDE.md)
- [NFC Setup & Troubleshooting](../docs/INTEGRATION_GUIDE.md#nfc-setup--troubleshooting)
- [NFC Implementation Summary](../NFC_IMPLEMENTATION_SUMMARY.md)

---

## Support

For issues or questions:
1. Check the [NFC Setup & Troubleshooting](../docs/INTEGRATION_GUIDE.md#nfc-setup--troubleshooting)
2. Review error codes in [SDK Documentation](../docs/INTEGRATION_GUIDE.md)
3. Enable TRACE level logging for detailed diagnostics:
   - Android: `Logger.setLogLevel(LogLevel.TRACE)`
   - iOS: `Logger.shared.currentLevel = .trace`

---

**Version:** 0.4.1+  
**Last Updated:** April 2026
