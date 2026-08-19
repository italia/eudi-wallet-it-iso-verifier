# samples

End-to-end sample apps showing how to integrate the proximity verification library on Android and iOS. They exist as working references so adopters can see real usage of the public API without reading library internals.

## Android (`samples/sampleandroid/`)
- Entry point: `src/main/` — Kotlin app using CameraX for QR scanning, depends on `:proximityverification`
- Run: `./gradlew :samples:sampleandroid:installDebug`
- Unit tests: `./gradlew :samples:sampleandroid:testDebugUnitTest`

## iOS (`samples/ProximityDemoApp/`)
- Entry point: `ProximityDemoApp.xcworkspace` — SwiftUI app consuming `ProximityVerification.shared` via CocoaPods
- Run: `cd samples/ProximityDemoApp && pod install`, then open `ProximityDemoApp.xcworkspace` in Xcode and build
- Links the framework from `Products/iOS/latest` (`FRAMEWORK_SEARCH_PATHS`), so a local SDK change must be rebuilt and packaged before the sample sees it — see [deployment.md](../deployment.md)

## Consumer policies demonstrated

The samples carry the product rules the SDK deliberately refuses to own: the library produces facts,
the consumer issues the verdict. Each rule is pure logic with no UI dependency, unit-tested on
Android, and ported faithfully to Swift (the two languages share no code here).

| Policy | Files | Built on |
|---|---|---|
| **Portrait corroboration** — for credentials carrying no photo of their own, take the holder's photo from the mDL in the same session; its absence must not block the verification | `PortraitCorroboration.kt` / `.swift`, `PortraitCorroborationTest` | `VerificationCheckType`, `CheckStatus`, plus an intent *declared* in the sample's `RequestConfig` rather than inferred from the request shape |
| **Required claims** — everything asked for must come back; the strictest possible rule, chosen because it makes every absence visible | `RequiredClaims.kt` / `.swift`, `RequiredClaimsTest` | `DocumentReceived.requestOutcome` (`RequestOutcome`) |
| **Reader certificate inspection** — show the reader/access certificate the SDK obtained | `ReaderCertificate.kt`, `ReaderCertificateView.swift` | `ProximityVerification.getReaderCertificate()` |

Both verdicts rest on **`RETURNED` versus not-`RETURNED`**, never on the `WITHHELD`/`ABSENT`
distinction: those two states come from the wallet's unsigned `errors`/`documentErrors` maps and are
displayed for diagnostics only.

Full narrative, including why the corroboration intent is declared rather than inferred and what each
result card reports: [`samples/README.md`](../../samples/README.md).
