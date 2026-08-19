# platform-android

Provides the Android-specific `actual` implementations for all `expect` declarations defined in `commonMain`, covering NFC scanning, Bluetooth, secure storage, Play Integrity, and coroutine dispatching.

It exists as a separate Kotlin Multiplatform source set so that Android SDK types and APIs (e.g. `android.nfc`, `android.security.keystore`, Google Play Integrity) are isolated from shared code and never compiled on other targets.

**Key files:**

- `ProximityVerificationNfc.android.kt` — `actual` implementations of `startVerificationWithNfc()` and `resetNfcState()`, wiring the NFC tag dispatch into the ISO 18013-5 engagement flow.
- `core/nfc/NfcPromptBinding.android.kt` — manages NFC reader mode on the `Activity` and defines `NfcReaderCallback<T>` for tag discovery callbacks.
- `core/crypto/LocalSecureAreaFactory.android.kt` — constructs the Android Keystore-backed secure area used to store and access document credentials.
- `core/crypto/IntegrityWarmupManager.kt` (android actual) — warms up the Google Play Integrity API at startup so integrity tokens are available quickly.
- `core/handlers/DispatcherProvider.android.kt` — fulfils the `expect` dispatcher contract by supplying `Dispatchers.IO` as the background dispatcher.

**Key external dependency:** Google Play Integrity API (`com.google.android.play:integrity`).
