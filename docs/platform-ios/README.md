# platform-ios

Provides the iOS-specific `actual` implementations for every `expect` declaration in `commonMain`, covering NFC scanning, Secure Enclave key storage, coroutine dispatching, and platform detection.

It exists as a dedicated `iosArm64Main` source set because Kotlin Multiplatform requires platform-specific source sets to supply `actual` bodies that call native Apple frameworks unavailable on other targets.

**Key files:**

- `ProximityVerificationNfc.iosArm64.kt` — `actual` implementations of `startVerificationWithNfc()` and `resetNfcState()`, driving CoreNFC tag scanning via `NfcTagReader` with iOS-specific session-close detection.
- `core/crypto/LocalSecureAreaFactory.iosArm64.kt` — creates the `SecureAreaProvider` backed by `LocalSecureArea`, which stores keys in the iOS Secure Enclave using a SQLite-backed `SqliteStorage` in the app's Documents directory.
- `core/handlers/DispatcherProvider.iosArm64.kt` — supplies `IosDefaultDispatcherProvider`, mapping both `defaultDispatcher` and `backgroundDispatcher` to `Dispatchers.Default` (no `Dispatchers.IO` on iOS).
- `core/handlers/NativeUtils.kt` — `actual` platform-detection object; returns `isIos() = true` and checks network reachability via `SCNetworkReachability`.

**Key external dependency:** `org.multipaz` (identity credential library supplying `NfcTagReader`, `SecureEnclaveSecureArea`, and mdoc transport primitives). Deployment target: **iOS 16.0**, `iosArm64` only — no simulator target is provided.
