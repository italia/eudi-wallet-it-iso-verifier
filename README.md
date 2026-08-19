# ProximityVerification SDK

Kotlin Multiplatform library for **ISO 18013-5** proximity document verification (mdoc reader role). Instance configuration according to **IT-Wallet** specification.
Targets **Android** and **iOS** (iosArm64).

## Requirements

| | |
|---|---|
| Android | `minSdk 30`, `compileSdk 35`, JVM target 17 |
| iOS | deployment target 16.0. Only the **`iosArm64`** target is built: there is no simulator artifact, so the iOS sample runs on a physical device |
| Build | JDK 17, Gradle wrapper included, Kotlin 2.2.0 |
| Android attestation | Device attestation uses the Play Integrity API, so the Android artifact requires Google Play services on the device |

## ISO 18013-5 — Coverage Matrix

| Engagement | Transport | ISO Ref | Support |
|---|---|---|---|
| QR code | BLE peripheral server | 8.2.2.3 → 8.3.3.1.1 | Supported |
| QR code | BLE central client | 8.2.2.3 → 8.3.3.1.1 | Supported |
| QR code | BLE L2CAP | 8.2.2.3 → Annex A | Supported |
| QR code | NFC data transfer | 8.2.2.3 → 8.3.3.1.2 | Supported (multi-step API) |
| QR code | Wi-Fi Aware | 8.2.2.3 → 8.3.3.1.3 | Not supported |
| NFC static handover | BLE | 8.2.2.1 → 8.3.3.1.1 | Supported |
| NFC static handover | NFC data transfer | 8.2.2.1 → 8.3.3.1.2 | Supported |
| NFC negotiated handover | BLE | 8.2.2.1 → 8.3.3.1.1 | Supported |
| NFC negotiated handover | NFC data transfer | 8.2.2.1 → 8.3.3.1.2 | Supported |

## Documentation

All documentation lives in `docs/`.

### Consumer-facing (SDK integration)

| Document | Description |
|---|---|
| [INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md) | Full integration guide: build, distribution, API reference, NFC setup, error codes (Android + iOS) |

### Architecture & decisions

| Document | Description |
|---|---|
| [GAP_ANALYSIS.md](docs/GAP_ANALYSIS.md) | Gap analysis vs KMP best practices (resolved and open risks) |
| [rationale.md](docs/rationale.md) | Decision log: architectural choices, trade-offs, alternatives considered |
| [SECURE_AREA_ANALYSIS.md](docs/SECURE_AREA_ANALYSIS.md) | Security analysis: Android Keystore, iOS Secure Enclave, platform comparison |

### Sample apps (`samples/`)

| App | Path | Description |
|---|---|---|
| Android | `samples/sampleandroid/` | Compose sample demonstrating multi-step verification with transport selection |
| iOS | `samples/ProximityDemoApp/` | SwiftUI sample demonstrating multi-step verification with transport sheet |
| [samples/README.md](samples/README.md) | Usage examples, build instructions, and configuration reference |

## Quick start

```bash
# Build every artifact into Products/ — no credentials needed
bash build_scripts/update_build_publish_all.sh --no-publish

# Run Android sample
./gradlew :samples:sampleandroid:installDebug

# Run library tests (the whole commonTest suite, on the Android JVM variants:
# there is no host-runnable Kotlin/Native target, so :allTests is not generated)
./gradlew :proximityverification:test
```

To also publish the artifacts to a Maven and a generic package registry, copy
`.env.example` to `.env`, fill it in, and drop the `--no-publish` flag:

```bash
export $(cat .env | xargs)
bash build_scripts/update_build_publish_all.sh
```

The publish step targets a GitLab Package Registry; see
[docs/deployment.md](docs/deployment.md) for the full pipeline and
[docs/INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md) for how consumers resolve
the published artifacts.

## Initialization modes

The SDK offers two ways to initialize, described in detail in the
[integration guide](docs/INTEGRATION_GUIDE.md):

- **`initializeStandalone()`** — no backend contact. Generates a self-signed reader
  key, optionally loads a credential catalog from JSON or a URL, and accepts an
  optional set of trust anchors. This is the path to use to evaluate the SDK.
- **`initializeRelyingPartyInstance()`** — the production path. It requires a
  Relying Party backend that issues the reader access certificate and the session
  token, plus device attestation. **That backend is not part of this repository.**

## Known limitations

- **Federation and status-list payloads are parsed without verifying their
  signature.** The IT-Wallet registry resolution chain and the freshness
  extraction from Status List tokens decode the JWS/CWT envelope and read its
  claims without checking the signature. Trust is enforced on the certificate
  chain, not on the token signature. See
  [docs/IT-WALLET-REGISTRY_DISCOVERY.md](docs/IT-WALLET-REGISTRY_DISCOVERY.md) and
  [docs/status/README.md](docs/status/README.md).
- **Wi-Fi Aware data transfer is not supported** (see the coverage matrix above).
- **No iOS simulator target**, and iOS platform code has no automated test
  coverage; see [docs/GAP_ANALYSIS.md](docs/GAP_ANALYSIS.md) for the open
  architectural risks.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

Licensed under the Apache License, Version 2.0. See [LICENSE.txt](LICENSE.txt).

```
Copyright [yyyy] [name of copyright owner]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

This project depends on third-party libraries distributed under their own terms,
including [Multipaz](https://github.com/openwallet-foundation-labs/identity-credential)
(Apache-2.0), [Signum](https://github.com/a-sit-plus/signum) (Apache-2.0), Ktor and
kotlinx (Apache-2.0), AndroidX (Apache-2.0), and the Google Play Integrity API and
ML Kit, which are subject to the Google APIs Terms of Service.
`NfcPromptBinding.android.kt` is an independent reimplementation inspired by
Multipaz's `ScanNfcTagPromptDialog`.
