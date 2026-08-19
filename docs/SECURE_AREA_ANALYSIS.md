# SecureAreaProvider — Security Analysis (Multiplatform)

This implementation provides a local security infrastructure for generating, using, and managing cryptographic keys via `SecureAreaProvider`, with platform-specific backends:

- **Android**: `LocalSecureArea` → `AndroidKeystoreSecureArea`
- **iOS**: `LocalSecureArea` → `SecureEnclaveSecureArea`

---

## Platform Security

### Android — `LocalSecureArea`

| Feature | Status |
|---|---|
| Hardware-backed (TEE / StrongBox) | Supported |
| X.509 attestation | Supported (Google CA) |
| User auth (LSKF / Biometric) | Supported |
| Invalidation on lockscreen change | Supported |
| Non-exportable key | Supported |
| Validity period | Supported |
| ESP256 / ECDH_P256 support | Supported |
| Metadata persistence | Supported (via `Storage`) |
| Brute-force protection | Limited to OS-level |
| Backup / remote revocation | Not supported |

Equivalent to `AndroidKeystoreSecureArea`.

---

### iOS — `LocalSecureArea` with `SecureEnclaveSecureArea`

| Feature | Status |
|---|---|
| Secure Enclave hardware-backed | Supported |
| User auth (TouchID / FaceID) | Supported |
| Non-exportable key | Supported |
| Custom prompt via LAContext | Supported |
| Hardware attestation | Not available in CryptoKit |
| Validity period | Not supported |
| Invalidation tracking | Not tracked |
| Brute-force protection (biometric) | Supported |
| Metadata persistence | Supported (via `StorageTable`) |
| Public key hash integrity check | Supported |

More complete than standalone `SecureEnclaveSecureArea` thanks to persistence and integrity checks.

---

## Comparison with `CloudSecureArea`

| Feature | Local (`SecureAreaProvider`) | CloudSecureArea |
|---|---|---|
| Hardware-backed keys | Supported | Supported |
| Verifiable hardware attestation | Android only | Supported |
| Remote revocation | Not supported | Supported |
| Cloud backup / multi-device | Not supported | Supported |
| Separate passphrase | Not supported | Supported |
| Audit log / tracking | Not supported | Supported |
| E2EE via challenge | Not supported | Supported |

---

## Conclusions

- **Suitable for**: local applications, offline wallets, single-device scenarios, high-privacy environments
- **Not suitable for**: zero-trust environments, centralized revocation, multi-device sync
- **Extensible**: can include fallback, mock, or hybrid providers
- **iOS parity**: ensures integrity and consistency with Android implementation

> Secure, consistent, and maintainable solution for mobile-first environments with a KMP approach.

---

## Multiplatform Integration — `createLocalSecureAreaProvider()`

The `SecureAreaProvider` infrastructure natively supports cross-platform usage of `LocalSecureArea` via the multiplatform factory `createLocalSecureAreaProvider()`.

This function is defined as `expect` in `commonMain` and implemented per platform. Implementations are **fully self-contained**, including:

- `platformIsEmulator` detection
- `platformStorage` configuration

### Implementations

- **Android**: `LocalSecureArea` built on `AndroidKeystoreSecureArea`, with storage in `noBackupFilesDir`
- **iOS**: `LocalSecureArea` built on `SecureEnclaveSecureArea`, with SQLite storage in `NSDocumentDirectory`

This makes each implementation self-contained and independent from external platform utilities, improving portability and modular integration.

---

## Detailed Security Analysis

### Implementation Comparison

| Name | Backing | Metadata Persistence | Hardware-backed | Remote Revocation | Attestation | User Auth |
|---|---|---|---|---|---|---|
| `SecureArea` (interface) | Abstract | — | — | — | — | — |
| `AndroidKeystoreSecureArea` | Android KeyStore | No | StrongBox | No | Supported | Supported |
| `SecureEnclaveSecureArea` | Apple Secure Enclave | No | Enclave | No | No | Supported |
| `LocalSecureArea (Android)` | AndroidKeystoreSecureArea | Via Storage | StrongBox | No | Supported | Supported |
| `LocalSecureArea (iOS)` | SecureEnclaveSecureArea | Via Storage | Enclave | No | No | Supported |
| `CloudSecureArea` | Cloud-based HSM | Server-side | Server | Supported | Supported | Supported + passphrase |

### Summary Comparison

| Feature | LocalSecureArea (Android) | LocalSecureArea (iOS) | AndroidKeystoreSecureArea | SecureEnclaveSecureArea | CloudSecureArea |
|---|---|---|---|---|---|
| **Hardware-backed** | StrongBox | Enclave | Supported | Supported | Server HSM |
| **User Auth** | LSKF/Biometric | Biometric | Supported | Supported | Supported + passphrase |
| **Key Attestation** | Supported | No | Supported | No | JWT |
| **Validity Period** | Supported | No | No | No | Supported |
| **Integrity via pubKey hash** | Supported | Supported | No | No | Supported (remote) |
| **Remote revocation** | No | No | No | No | Supported |
| **Backup & sync** | No | No | No | No | Supported |
| **Offline friendly** | Supported | Supported | Supported | Supported | No |

### Conclusion

- `LocalSecureArea` is a **solid hybrid solution**: combines hardware security with **structured persistence** (alias, hash, validity)
- It is **cross-platform** and **independent from external components**
- Not suitable for environments requiring centralized revocation, multi-device restore, or public attestation
- Ideal for local wallets, offline authentication, and high-privacy environments

### Cross-platform Usage Example

```kotlin
val secureAreaProvider = createLocalSecureAreaProvider()

suspend fun provisionSecureKey() {
    val secureArea = secureAreaProvider.get()
    val settings = AndroidKeystoreCreateKeySettings.Builder(nonce = ByteString())
        .setAlgorithm(Algorithm.ESP256)
        .setUserAuthenticationRequired(true, 30_000L, setOf(UserAuthenticationType.BIOMETRIC))
        .build()

    val keyInfo = secureArea.createKey("my-local-key", settings)
}
```
