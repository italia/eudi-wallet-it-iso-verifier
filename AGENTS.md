# AGENT.md

## 1. Purpose

This document defines the operational contract for AI agents working in this repository.

It establishes architectural principles, repository navigation rules, development workflow expectations, modification constraints, and quality requirements.

All agents must comply strictly with this document.

---

## 0. Scope & Authority

This repository is **proximity-verification-ktm** (root project: `MdlProximityVerification`).

**You are allowed to:**
- Modify code inside this repository:
  - `:proximityverification` (KMP library)
  - `:samples:sampleandroid` (sample Android app, read/modify only if helpful for testing)

**You are NOT allowed to modify files outside this repo unless explicitly instructed.**

**Source of truth** for business logic is `:proximityverification`.

## 0.1 Rule Precedence and Conflict Handling

- This file is the operational policy for this repository.
- `.cursor/rules/kotlin_guidelines.md` and `.cursor/rules/kotlin_multiplatform_libraries.md` are mandatory for Kotlin/KMP code touched in this repository.
- When rules conflict with existing production behavior, preserve runtime behavior first, then document the deviation and propose a follow-up cleanup task.
- Do not apply large style-only rewrites to enforce rules retroactively.

---

## 1. Project Philosophy

This repository prioritizes predictability over cleverness, explicit structure over hidden magic, backward compatibility over rapid change, and maintainability over short-term optimization.

Agents must optimize for long-term system stability and avoid speculative, unnecessary, or overengineered refactors.

---

## 2. Architectural Model

The system follows a layered architecture within a Kotlin Multiplatform (KMP) library structure.

**The Interface Layer** contains public APIs, callbacks, and external-facing contracts. This includes:
- `ProximityVerification` object (main entry point)
- `VerificationCallback` interface
- Public model classes exposed to consumers
- `DispatcherProvider` interface (consumer-configurable dispatcher contract)

**The Application Layer** contains use cases, orchestration logic, and workflow coordination. This includes:
- `VerificationSession` orchestration (internal)
- Verification workflow coordination
- Session lifecycle management (`sessionScope`, `activeVerificationJob`, `close()`)

**The Domain Layer** contains core business rules, entities, and domain models. This includes:
- `core/models/` - Domain entities and value objects
- `core/handlers/` - Business logic handlers and dispatcher infrastructure
- `core/crypto/` - Cryptographic operations and domain rules
- `core/documenttype/` - Document type definitions and business rules
- `core/transport/` - Transport protocol domain logic

**The Infrastructure Layer** contains platform-specific implementations, database access, external APIs, and framework bindings. This includes:
- `network/` - HTTP client implementations and API adapters
- `androidMain/` - Android-specific infrastructure (NFC, permissions, secure storage)
- `iosArm64Main/` - iOS-specific infrastructure (NFC, secure enclave, attestation)
- Platform-specific `expect/actual` implementations
- Platform-specific `DispatcherProvider` implementations

**Architectural Rules:**
- Business logic must not reside in the interface layer.
- Domain logic must not depend on infrastructure details.
- Infrastructure must implement abstractions defined by higher layers.
- Circular dependencies must be avoided.
- Platform-specific code must be isolated in `androidMain/` or `iosArm64Main/`.
- Common business logic must reside in `commonMain/`.
- `Dispatchers.*` must not be referenced in `commonMain/`; use `DispatcherProvider` injection.

---

## 3. Repository Structure

The repository follows a Kotlin Multiplatform modular structure.

**Gradle Modules:**
- `:proximityverification` — Kotlin Multiplatform library
- `:samples:sampleandroid` — sample Android app

**Core Module: `proximityverification`**

KMP library sources:
- `proximityverification/src/commonMain/kotlin` - Shared Kotlin code across platforms
  - `core/` - Core business logic and domain models
  - `network/` - Network layer implementations
  - `callback/` - Callback interfaces
  - `exceptions/` - Exception definitions
  - `lifecycle/` - Verification step definitions
  - `logging/` - Logging utilities
- `proximityverification/src/androidMain/kotlin` - Android-specific implementations
- `proximityverification/src/iosArm64Main/kotlin` - iOS-specific implementations

Tests:
- `proximityverification/src/commonTest/kotlin` - Shared test code (lifecycle tests, network tests, NFC model tests, crypto tests)
- `samples/sampleandroid/src/test` and `samples/sampleandroid/src/androidTest` - Sample app tests
- `proximityverification/src/androidUnitTest` is currently not present; add platform-specific tests only when behavior cannot be verified in `commonTest`
- `proximityverification/src/iosArm64Test` is currently not present

Documentation:
- `build_scripts/proximityverification.podspec` - iOS podspec template (used by build scripts)
- `docs/` - Project-level documentation (`GAP_ANALYSIS.md`, `rationale.md`, plus Lumen component docs)
- `docs/design/` - All design documents (created here by the design/planning workflows)

**Sample Module: `samples:sampleandroid`**
- Sample Android application for testing and demonstration

**iOS Sample:**
- `samples/ProximityDemoApp` - iOS SwiftUI sample application (Xcode workspace, CocoaPods)

**Build Scripts:**
- `build_scripts/build_all.sh`
- `build_scripts/build_android_elements.sh`
- `build_scripts/build_ios_elements.sh`
- `build_scripts/update_build_publish_all.sh`

**Outputs/Artifacts:**
- `Products/Android/<version>/...` - Android artifacts
- `Products/iOS/<version>/proximityverification.framework`, `.podspec`, `.tar.gz` - iOS artifacts

**Generated/Local Files (Do Not Commit):**
- `Products/**`
- `**/build/**`
- `.env`, `.env.local`, `build.log`

**KMP Configuration** (from `proximityverification/build.gradle.kts`):
- group: `it.infocert.proximityverification`
- version currently set here (e.g., `0.3.15`)
- targets:
  - Android (`publishLibraryVariants("release")`)
  - iOS: `iosArm64()` static framework named `proximityverification`
- iOS deployment target: `16.0`
- Compiler options: `-Xexpect-actual-classes`, `kotlin.time.ExperimentalTime`

**Structural Rules:**
- Agents must not create new top-level directories without architectural justification.
- Agents must not move files across architectural boundaries unless explicitly required.
- Infrastructure code must not be placed inside domain modules.
- Platform-specific code must remain in appropriate `*Main/` source sets.
- Common code must remain in `commonMain/`.
- Tests must mirror the source structure in `*Test/` source sets.

---

## 3.1. Package Structure and Class Reference

This section provides a comprehensive reference of all packages and their key classes to help agents navigate the codebase without unnecessary grep operations.

### Root Package: `it.infocert.proximityverification`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/`

**Key Classes:**
- `ProximityVerification` (object) - Main public API entry point. Provides initialization, verification, lifecycle management, and dispatcher configuration.
- `VerificationSession` (internal class) - Manages the verification session lifecycle, handles device engagement, transport, and response processing. Not part of the public API.
- `Utils` (object) - Utility functions for common operations.

### Package: `it.infocert.proximityverification.callback`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/callback/`

**Purpose:** Public callback interfaces for verification events.

**Key Classes:**
- `VerificationCallback` (interface) - Callback interface for verification progress, success, and failure events.

### Package: `it.infocert.proximityverification.core`

**Purpose:** Core business logic and domain models.

#### Subpackage: `core.crypto`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/crypto/`

**Purpose:** Cryptographic operations, secure storage, token management, and trust utilities.

**Key Classes:**
- `TokenManager` - Manages access tokens, refresh tokens, and token lifecycle.
- `LocalStorage` - Interface for secure local storage operations.
- `LocalSecureAreaFactory` - Factory for creating secure area instances (expect/actual pattern).
- `ExtendedSecureArea` - Extended secure area interface with additional operations.
- `ExtendedSecureAreaWithTTL` - Secure area with time-to-live support.
- `SecureAreaExtension` - Extension functions for secure area operations.
- `GenerateCert` - Certificate generation utilities.
- `GenerateAssertion` - Assertion generation for integrity checks.
- `HardwareKeyTagGenerator` - Generates tags for hardware-backed keys.
- `IntegrityWarmupManager` - Manages integrity check warmup/preparation.
- `SecureRandomProvider` - Provides secure random number generation.
- `TrustUtils` - Utilities for trust management and certificate validation.
- `MsoRevocationChecker` (internal object) - MSO-level revocation checking via StatusList and IdentifierList (ISO/IEC 18013-5). Injectable test seams (`isNetworkAvailable`, `fetchPayload`). Returns `NOT_DETERMINED` when offline, format is unknown, or fetch/parse fails. Always re-throws `CancellationException`.

#### Subpackage: `core.handlers`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/handlers/`

**Purpose:** Business logic handlers for verification workflow, permissions, platform utilities, and dispatcher infrastructure.

**Key Classes:**
- `DispatcherProvider` (interface) - Cross-platform dispatcher abstraction with `defaultDispatcher`, `backgroundDispatcher`, `callbackDispatcher`. Public API for consumer configuration.
- `DefaultDispatcherProvider` (computed property) - Platform-specific default dispatcher provider accessed via `expect fun getDefaultDispatcherProvider()`.
- `VerificationDispatcher` - Dispatches verification steps. Contains deprecated `expect fun verificationCoroutineDispatcher()` superseded by `DispatcherProvider`.
- `NfcChecker` - Checks NFC availability and capabilities (expect/actual pattern).
- `BluetoothChecker` - Checks Bluetooth availability and capabilities.
- `PermissionManager` - Manages app permissions (expect/actual pattern).
- `NativeUtils` - Platform detection and native utility functions. `isNetworkAvailable()` is an SDK-level "usable internet for backend/REST operations" guard, not a mere local-interface-present check. Android may require validated internet; iOS uses host reachability as a best-effort approximation.

#### Subpackage: `core.models`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/models/`

**Purpose:** Domain models and data structures.

**Key Classes:**
- `VerificationResult` - Result of a verification operation.
- `KeyCreationResult` - Result of key creation operation.
- `KeyCreationStatus` - Status of key creation.
- `LocalKeyStoreKeyInfo` - Information about keys stored in local keystore.
- `AppPermission` - App permission definitions.

**Subpackage: `core.models.initialization`**

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/models/initialization/`

**Purpose:** Models for initialization phase.

**Key Classes:**
- `ProximityVerificationSettings` - Central per-verification policy object.
- `RelyingPartyInstanceParams` - Parameters for initializing the verification library. Fields: `providerInfo: RelyingPartyProviderInfo` (mandatory), `allowedIssuerIds: List<String> = emptyList()` (optional whitelist applied at IT-Wallet Credential Catalog filter step in `RegistryResolver`; empty list = no filter).
- `RelyingPartyProviderInfo` - Information about the application provider.
- `TransportPreference` - Preferred transport for verification (`AUTO`, `BLE`, `NFC`).
- `CSRParam` - Certificate Signing Request parameters.

**Subpackage: `core.models.engagement`**

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/models/engagement/`

**Purpose:** Models for device engagement.

**Key Classes:**
- `EngagementData` - Device engagement data structure.
- `EngagementType` - Type of engagement (QR, NFC).

**Subpackage: `core.models.nfc`**

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/models/nfc/`

**Purpose:** Models specific to NFC operations.

**Key Classes:**
- `NfcEngagementResult` - Result of NFC engagement scan.

**Subpackage: `core.models.document`**

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/models/document/`

**Purpose:** Models for document data and metadata.

**Key Classes:**
- `DocumentData` - Document data structure.
- `DocumentKeyValuePair` - Key-value pair for document data.
- `DocumentMetadataKey` - Metadata keys for documents.
- `DocumentValueMimeType` - MIME types for document values.
- `DocumentInfoCode` - Information codes for documents.
- `DocumentWarningCode` - Warning codes for documents.

#### Subpackage: `core.request`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/request/`

**Purpose:** Request generation for device requests.

**Key Classes:**
- `DeviceRequestGenerator` - Generates device requests for verification.

#### Subpackage: `core.transport`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/transport/`

**Purpose:** Transport protocol handling.

**Key Classes:**
- `HandoverBuilder` - Builds handover messages for transport protocols.

#### Subpackage: `core.trust`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/trust/`

**Purpose:** Trust management and certificate operations.

**Key Classes:**
- `CSRCreator` - Creates Certificate Signing Requests.
- `ClientData` - Client data for certificate operations.
- `ClientDataHash` - Hash of client data.
- `Jwk` - JSON Web Key structure.
- `JwkUtils` - Utilities for JWK operations.
- `JwkThumbprint` - JWK thumbprint calculations.

#### Subpackage: `core.documenttype`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/documenttype/`

**Purpose:** Document type definitions and implementations.

**Key Classes:**
- `ItalianDrivingLicense` - Italian driving license document type implementation.
- `WalletAttestation` - Wallet attestation document type.
- `SampleData` - Sample data for testing.

#### Subpackage: `core.utils`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/core/utils/`

**Purpose:** Core utility functions and constants.

**Key Classes:**
- `Constants` - Application constants.

### Package: `it.infocert.proximityverification.network`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/network/`

**Purpose:** Network layer for API communication with the Relying Party backend.

**Key Classes:**
- `RelyingPartyApi` (interface) - Interface for Relying Party API operations.
- `RelyingPartyApiImpl` - Implementation of Relying Party API client. All 6 API methods re-throw `CancellationException`.
- `HttpClientProvider` - Provides HTTP client instances.
- `ApiResult` - Result wrapper for API operations.
- `ErrorResponse` - Error response model.

**Subpackage: `network.model.common`**

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/network/model/common/`

**Purpose:** Common network request/response models.

**Key Classes:**
- `RegisterDeviceRequest` / `RegisterDeviceResponse` / `RegisterDeviceData`
- `RefreshTokenRequest` / `RefreshTokenResponse`
- `KeyBindingRequest` / `KeyBindingResponse`
- `AccessCertificateRequest` / `AccessCertificateResponse`
- `NonceResponse`
- `DeviceInfo`
- `WellKnownConfiguration`

### Package: `it.infocert.proximityverification.exceptions`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/exceptions/`

**Purpose:** Exception definitions for error handling.

**Key Classes:**
- `ProximityVerificationException` - Base exception for verification errors.
- `ProximityVerificationExceptionCode` - Exception code enumeration.
- `VerificationException` - General verification exception.

### Package: `it.infocert.proximityverification.lifecycle`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/lifecycle/`

**Purpose:** Lifecycle and step definitions for verification process.

**Key Classes:**
- `VerificationStep` - Enumeration of verification steps in the lifecycle.

### Package: `it.infocert.proximityverification.logging`

**Location:** `proximityverification/src/commonMain/kotlin/it/infocert/proximityverification/logging/`

**Purpose:** Logging infrastructure.

**Key Classes:**
- `Logger` (interface) - Logger interface.
- `LoggerConfig` - Logger configuration (includes `delegateLogger` property).
- `LogLevel` - Log level enumeration.
- `LoggerDelegate` - Interface for external logger delegates.

### Platform-Specific Packages

#### Android-Specific: `it.infocert.proximityverification.core.nfc` (Android)

**Location:** `proximityverification/src/androidMain/kotlin/it/infocert/proximityverification/core/nfc/`

**Purpose:** Android-specific NFC implementations.

**Key Classes:**
- `NfcPromptBinding` - NFC prompt/dialog binding and reader callback infrastructure.
  - `NfcReaderCallback<T>` - Active NFC callback implementation. Uses `runBlocking` in `onTagDiscovered()` (Android NFC API requirement, documented in KDoc).
- `NfcDialogObserver` - Observes NFC dialog state.

**Deleted:** `NfcReaderHandler.android.kt` (was dead code), `SdkNfcScope.kt` (responsibilities subsumed by session scope).

#### Android-Specific: `it.infocert.proximityverification.core.crypto` (Android)

**Location:** `proximityverification/src/androidMain/kotlin/it/infocert/proximityverification/core/crypto/`

**Purpose:** Android-specific cryptographic implementations.

**Key Classes:**
- `LocalSecureArea` (actual) - Android implementation of secure area.
- `SelfSignedCertUtil` - Self-signed certificate utilities for Android.
- `IntegrityWarmupManager` (actual) - Android Play Integrity warmup manager.

#### Android-Specific: `it.infocert.proximityverification.core.handlers` (Android)

**Location:** `proximityverification/src/androidMain/kotlin/it/infocert/proximityverification/core/handlers/`

**Purpose:** Android-specific handler and dispatcher implementations.

**Key Classes:**
- `DispatcherProvider.android.kt` - `actual fun getDefaultDispatcherProvider()` returning Android implementation (`backgroundDispatcher = Dispatchers.IO`).
- `VerificationDispatcher` (actual) - Deprecated, delegates to `DefaultDispatcherProvider.backgroundDispatcher`.
- `NfcChecker` (actual) - Android NFC availability checker.
- `PermissionManager` (actual) - Android permission manager.
- `NativeUtils` (actual) - Android native utilities.

#### iOS-Specific: `it.infocert.proximityverification.core.crypto` (iOS)

**Location:** `proximityverification/src/iosArm64Main/kotlin/it/infocert/proximityverification/core/crypto/`

**Purpose:** iOS-specific cryptographic implementations.

**Key Classes:**
- `LocalSecureArea` (actual) - iOS implementation using Secure Enclave.
- `LocalSecureAreaFactory` (actual) - iOS secure area factory.
- `KeyTagStorage` - Key tag storage for iOS.

#### iOS-Specific: `it.infocert.proximityverification.core.handlers` (iOS)

**Location:** `proximityverification/src/iosArm64Main/kotlin/it/infocert/proximityverification/core/handlers/`

**Purpose:** iOS-specific handler and dispatcher implementations.

**Key Classes:**
- `DispatcherProvider.iosArm64.kt` - `actual fun getDefaultDispatcherProvider()` returning iOS implementation (`backgroundDispatcher = Dispatchers.Default`).
- `VerificationDispatcher` (actual) - Deprecated, delegates to `DefaultDispatcherProvider.backgroundDispatcher`.
- `NfcChecker` (actual) - iOS NFC availability checker.
- `NativeUtils` (actual) - iOS native utilities.

#### Platform-Specific NFC Extensions

**Location:** 
- Android: `proximityverification/src/androidMain/kotlin/it/infocert/proximityverification/ProximityVerificationNfc.android.kt`
- iOS: `proximityverification/src/iosArm64Main/kotlin/it/infocert/proximityverification/ProximityVerificationNfc.iosArm64.kt`

**Purpose:** Platform-specific NFC API implementations (expect/actual pattern).

**Key Functions:**
- `startVerificationWithNfc` - Starts verification with NFC. Uses `sessionScope` for job hierarchy.
- `resetNfcState` - Resets NFC state. Android: calls `cancelActiveVerification()` and disables reader mode.

---

## 4. Development Workflow

**Dependencies:**
- Dependencies are managed via Gradle (`build.gradle.kts`).
- Dependency versions are managed in `gradle/libs.versions.toml`.

**Local Validation (preferred during development):**
- First set correct environment using : `export $(cat .env | xargs)`
- Use `bash build_scripts/update_build_publish_all.sh --no-publish` to generate `Products/` without publishing.

**Packaging / Release Build:**
- First set correct environment using : `export $(cat .env | xargs)`
- Use `bash build_scripts/update_build_publish_all.sh --no-publish` to generate `Products/` without publishing.
- Use `bash build_scripts/update_build_publish_all.sh` for full publish flow.
- `update_build_publish_all.sh` must be executed with `bash` (not `sh`).
- For publish flow, export required env vars first: `GITLAB_PRIVATE_TOKEN`, `GITLAB_HOST`, `PROJECT_ID`.

**Running:**
- The library is consumed as a dependency by downstream projects.
- Sample Android and iOS apps can be run for testing:
  - Android sample: `samples/sampleandroid` (run with `./gradlew :samples:sampleandroid:installDebug`)
  - iOS sample: `samples/ProximityDemoApp` (open `samples/ProximityDemoApp/ProximityDemoApp.xcworkspace` in Xcode; run `pod install` if workspace is missing)

**Code Quality:**
- Linting and formatting must be applied according to repository standards.
- Follow Kotlin coding conventions.
- Run relevant tests/lint for touched modules before completion. If execution is blocked by environment constraints (sandbox, hardware, credentials), document what could not be run.

**Build Scripts:**
- `build_scripts/update_build_publish_all.sh` is the canonical release pipeline.
- Direct Gradle tasks are allowed for local debugging and incremental validation.
- Do not modify build scripts without explicit approval.

---

## 5. Coding Standards

**Basic Principles:**
- Use English for all code and documentation.
- Always declare the type of each variable and function (parameters and return value).
  - Avoid using `Any`.
  - Create necessary types instead of overusing primitives.
- Don't leave blank lines within a function.

**Nomenclature:**
- Use PascalCase for classes, interfaces, and type aliases.
- Use camelCase for variables, functions, and methods.
- Use underscores_case for file and directory names you control.
  - Do not rename tooling-convention directories (e.g., `commonMain`, `androidMain`, `commonTest` in KMP).
  - For Kotlin source files, keep repository consistency: preserve existing Kotlin naming conventions and avoid mass renaming for style-only reasons.
- Use UPPERCASE for environment variables and compile-time constants.
  - Avoid magic numbers; define `const val` constants.
- Start each function with a verb.
- Use verbs for boolean variables. Example: `isLoading`, `hasError`, `canDelete`.
- Use complete words instead of abbreviations and correct spelling.
  - Except for standard abbreviations like API, URL, ID.
  - Except for well-known abbreviations: `i`, `j` for loops; `err` for errors; `ctx` for contexts.

**Functions:**
- Write short functions with a single purpose. Less than 20 instructions.
- Name functions with a verb and something else.
  - If it returns a boolean, use `isX` or `hasX`, `canX`, etc.
  - If it doesn't return anything, use `executeX`, `saveX`, `createX`, `updateX`, etc.
- Avoid nesting blocks by:
  - Early checks and returns.
  - Extraction to utility functions.
- Use higher-order functions (`map`, `filter`, `fold`, etc.) to avoid function nesting.
  - Use lambdas only for simple functions (less than 3 instructions).
  - Use named functions for non-simple functions.
- Use default parameter values instead of checking for null.
- Reduce function parameters using RO-RO (Receive Object, Return Object).
  - Use an object to pass multiple parameters.
  - Use an object to return results.
  - Declare necessary types for input arguments and output.
- Use a single level of abstraction.

**Data:**
- Use data classes for data.
- Don't abuse primitive types; encapsulate data in composite/domain types.
- Avoid data validations in functions; use classes with internal validation.
- Prefer immutability for data.
  - Use `val` by default.
  - Expose read-only views of collections.
  - Use `const val` for compile-time constants.

**Classes:**
- Follow SOLID principles.
- Prefer composition over inheritance.
- Declare interfaces to define contracts.
- Write small classes with a single purpose.
  - Less than 200 instructions.
  - Less than 10 public methods.
  - Less than 10 properties.

**Exceptions:**
- Use exceptions to handle errors you don't expect.
- If you catch an exception, it should be to:
  - Fix an expected problem.
  - Add context.
  - Convert to a domain error at module boundaries.
  - Otherwise, use a global handler.
- Error handling must follow existing patterns (use `ProximityVerificationException`).
- `CancellationException` must never be caught and wrapped as a domain error. Always re-throw it to preserve structured concurrency.

**Architectural Principles:**
- Hidden side effects must be avoided.
- Global mutable state must not be introduced.
- Deep nesting should be minimized.
- Composition is preferred over inheritance.
- Each module should have a single responsibility.
- Large monolithic files should be avoided.
- Business logic must not be duplicated across modules.

**KMP-Specific Code Organization:**
- Platform-specific code must use `expect` declarations in `commonMain/` and `actual` implementations in platform source sets.
- Avoid platform-specific APIs in `commonMain/` unless abstracted properly.
- Use `NativeUtils` for platform detection when necessary.
- `commonMain` must not import platform APIs (Android/JVM/Apple/JS).
- `commonMain` must not reference `Dispatchers.*` directly (use `DispatcherProvider`).
- Do not expose platform types in public APIs; keep the public surface in `commonMain`.
- Hide platform behavior behind interfaces (ports) or `expect/actual`.

**Reference Documentation:**
- For detailed Kotlin coding guidelines, see `.cursor/rules/kotlin_guidelines.md`.
- For detailed KMP library development rules, see `.cursor/rules/kotlin_multiplatform_libraries.md`.

---

## 6. Agent Modification Rules

**Allowed Actions:**
- Agents may add new features within architectural boundaries.
- Agents may add or improve automated tests.
- Agents may refactor internal implementations only if behavior remains unchanged.
- Agents may improve documentation when appropriate.
- Agents may add new platform-specific implementations following `expect/actual` pattern.

**Prohibited Actions:**
- Agents must not delete files unless explicitly instructed.
- Agents must not modify historical database migrations (if any).
- Agents must not introduce breaking API changes silently.
- Agents must not change public interfaces without documentation.
- Agents must not modify CI/CD configuration without approval.
- Agents must not introduce new dependencies without clear justification.
- Agents must never hardcode secrets or sensitive data.
- Agents must not reduce test coverage.
- Agents must not modify build scripts without explicit approval.
- Agents must not change Gradle module structure without architectural justification.
- Agents must not commit secrets, tokens, or local env files (`.env`, `.env.local`).
- Agents must not introduce `Dispatchers.*` references in `commonMain`. Use `DispatcherProvider` injection.
- Agents must not catch `CancellationException` and convert it to a domain error.

**Backward Compatibility:**
- Backward compatibility must be preserved.
- Public API changes require version increments and migration documentation.
- Previously published API contracts must not be altered.
- Breaking changes must be explicitly approved and documented.

**Public API Surface:**

**Primary KTM entrypoint:** `it.infocert.proximityverification.ProximityVerification` (Kotlin `object`)

Current callable surface that downstream depends on:

- `suspend fun initializeRelyingPartyInstance(params: RelyingPartyInstanceParams): String`
  - Returns access token string
  - Performs: integrity/attestation (platform-specific), token ensure/registration, key/cert init, trust manager population
  - Creates internal verification session state and session scope
  - Cancels any previous session scope before starting

- `suspend fun initializeStandalone(params: StandaloneRelyingPartyParams = StandaloneRelyingPartyParams())`
  - **Test-only / no-backend init path.** Bypasses all RP backend contact (no attestation, no token, no CSR, no federation registry)
  - Creates a self-signed reader key via `Utils.initAndCreateKeyStandalone()` (key alias `PROX_VER_STANDALONE_INIT_KEY`); reuses hardware key if already present
  - Catalog: default = built-in mDL ISO 18013-5; `StandaloneIssuerMetadata.FromJson` → parse OID4VCI JSON; `StandaloneIssuerMetadata.FromUrl` → HTTP GET `{url}/.well-known/openid-credential-issuer` then parse
  - TrustManager: `trustAnchors = null` → trust-all (null `TrustManager`); non-null list → populated TrustManager
  - `statusListStore` initialised; `crlCheckerProvider` returns null (CRL validation skipped)
  - `enableStatusListsForOfflineUse()` called on a standalone session will throw `PVE01` (no `providerInfo`)
  - A subsequent `initializeRelyingPartyInstance()` call overwrites the standalone session via `cancelSessionScope()` — no special handling needed
  - Throws `ProximityVerificationException` / `CancellationException`

- `fun startVerification(encodedDeviceEngagement: String, settings: ProximityVerificationSettings, callback: VerificationCallback, platformContext: Any? = null)`
  - Single-call QR verification: parses engagement, selects transport, runs full flow
  - Expects `encodedDeviceEngagement` formatted as `"mdoc:<base64url>"`
  - `settings` centralizes all transport preferences, requests, and NFC parameters
  - Launches verification as child of `sessionScope`; cancel-previous semantics via `activeVerificationJob`

- `expect fun ProximityVerification.startVerificationWithNfc(settings: ProximityVerificationSettings, callback: VerificationCallback, activity: Any?)`
  - Single-call NFC verification: scans NFC engagement, selects transport, runs full flow
  - Platform-specific `expect/actual` extension

- `expect fun ProximityVerification.resetNfcState(activity: Any?)`
  - Resets NFC state and cancels active verification

- `fun close()`
  - Cancels all in-flight library work (verifications, NFC scans, transport) and releases internal session scope
  - Returns library to uninitialized state; new `initializeRelyingPartyInstance()` required for further verifications
  - Idempotent and safe to call when already closed/uninitialized

- `fun configureDispatcherProvider(provider: DispatcherProvider?)`
  - Configures custom dispatcher provider; pass `null` to reset to platform default

- `suspend fun clearLibraryLocalStorage()`
  - Clears local storage (status list cache + CRL cache + offline flag); re-throws `CancellationException`

- `suspend fun enableStatusListsForOfflineUse(docTypes: List<String> = emptyList()): StatusListPrefetchSummary`
  - Orchestrates **status list + CRL** prefetch as a single offline-mode activation
  - Returns `StatusListPrefetchSummary` with `crlSummary: CrlPrefetchSummary?` populated on successful CRL prefetch (`null` when the CRL leg threw and was absorbed; the offline flag still flips to `true` because status list is the primary lifecycle owner)
  - Throws `ProximityVerificationException(PVE40)` when the device is offline at invocation time. The connectivity pre-check (`NativeUtils.isNetworkAvailable()`) runs immediately after the `PVE01` initialization check and before any federation pre-check (`PVE37`/`PVE38`), prefetch call, or store write. Persisted state is left untouched and the consumer is responsible for retry when connectivity returns
  - **Direct-federation fallback (since 0.6.5):** when `resolvedRegistryData` is null (registry unreachable at init time) but `sessionAllowedIssuerIds` is non-empty, the call falls back to per-issuer direct-federation resolution to build a synthetic `ResolvedRegistryData`. Persisted under the same `PROX_VER_REGISTRY_RESOLVED_DATA` key. If `resolvedRegistryData` is null and `sessionAllowedIssuerIds` is empty, `PVE37` is thrown as before

- `suspend fun disableStatusListsForOfflineUse()`
  - Clears both stores (status list + CRL) and resets `PROX_VER_OFFLINE_ENABLED = false`. Idempotent

- `fun isStatusListsForOfflineUseEnabled(): Boolean`
  - Reads the persisted offline-mode flag

- `suspend fun getOfflineCacheSnapshot(): OfflineCacheSnapshot`
  - Public consumer-facing read-side API returning a snapshot of both caches with computed projection fields (`usability`, `expiresAtEpochSeconds`, `isStale`)
  - Friendly pre-init behavior: returns empty snapshot with `offlineEnabled = false` instead of throwing
  - Errors degrade to empty snapshot; re-throws `CancellationException` only

**Key Public Model Classes:**
- `ProximityVerificationSettings` — central configuration for a verification transaction (requests, transport preferences, NFC params, timeouts)
- `TransportPreference` — enum: `AUTO`, `BLE`, `NFC`
- `VerificationCallback` — callback interface for verification progress and completion
- `DispatcherProvider` — interface for consumer-configurable coroutine dispatchers
- `OfflineCacheSnapshot` — `@Serializable` aggregate of `offlineEnabled`, `generatedAtEpochMillis`, `statusLists: List<CachedStatusListInfo>`, `crls: List<CachedCrlInfo>` (returned by `getOfflineCacheSnapshot`)
- `CachedStatusListInfo` — `@Serializable` projection: `uri, format, issuerEntityId?, credentialTypes, docTypes, iatEpochSeconds?, ttlSeconds?, expiresAtEpochSeconds? (= iat + ttl), fetchedAtEpochMillis, usability` (`Fresh | StaleByTtl`)
- `CachedCrlInfo` — `@Serializable` projection: `crlUri, subjectDn, issuerDn, serialHex, thisUpdateEpochSeconds?, nextUpdateEpochSeconds?, fetchedAtEpochMillis, isStale` (computed `nextUpdate < now`)
- `CrlCheckStatus` — public enum `VERIFIED, REFRESHED, STALE_CACHE, CACHE_MISS, FETCH_FAILED, OFFLINE_NO_DATA` (severity ordinal). Default `VERIFIED` on the new `RevocationResult.crlCheckStatus` field
- `CrlPrefetchSummary` — `certsConsidered, crlsFetched: List<String>, crlsFailed: Map<String, CrlFailureReason>, parseFailureCount, persistedAtEpochMillis`
- `CrlFailureReason` — `CRL_UNREACHABLE, CRL_HTTP_ERROR, CRL_MALFORMED_PAYLOAD, X5C_PARSE_ERROR, STORAGE_ERROR`
- `VerificationStep` — sealed class with lifecycle steps:
  - `NfcScanningStarted(message)`, `NfcTagDetected`, `NfcEngagementReading`, `NfcEngagementCompleted`
  - `EngagementParsed`, `TransportSelected(transportType: String)`, `NfcDataTransferReady(message: String)`
  - `Starting`, `Connecting`, `SendingRequest`, `Authenticating`, `Verifying`, `Completed`, `Failed`
- `VerificationOutcome` — sealed class with `DocumentReceived(sessionInfo, documents, requestOutcome)` and `ProtocolFailed(reason, message, code?)`. A response with no documents — empty, or carrying only a non-`0` `status` — is still `DocumentReceived`; the wallet's status reaches the consumer only via `SessionInfo.deviceResponseStatus`
- `RequestOutcome` / `DocumentRequestOutcome` / `DocumentRequestStatus` / `ClaimOutcome` / `ClaimStatus` (`core/models/request/`) — inventory of what was requested versus what the wallet returned. Additive; no verdict, no new `VerificationCheckType`. Enforcement must rest on `RETURNED` vs not-`RETURNED`: `errors`/`documentErrors` carry no signature, so `WITHHELD` vs `ABSENT` is diagnostic only. The field is `nameSpace`, not `namespace` — Kotlin/Native would export the latter as `namespace_`
- `CheckMetadata` — sealed, optional metadata on `VerificationCheck`: `StatusListRevocation`, `CrlRevocation`, `MsoValidity`, `DocumentExpiry`
- `ReaderCertificateInfo` — projection of the reader/access certificate, returned by `getReaderCertificate()`
- `VerificationRequest` — request definition for document verification
- `DocumentTypeCatalog` — catalog for document type configurations
- `RelyingPartyInstanceParams` / `RelyingPartyProviderInfo` — initialization parameters
- `StandaloneRelyingPartyParams` — params for `initializeStandalone`: `trustAnchors: List<String>?` (null = trust-all), `issuerMetadata: StandaloneIssuerMetadata?` (null = built-in mDL catalog)
- `StandaloneIssuerMetadata` — sealed class: `FromJson(json: String)` (zero-network OID4VCI parse) | `FromUrl(url: String)` (HTTP GET `{url}/.well-known/openid-credential-issuer` at init time)

**Internal API (for platform extensions and testing):**
- `internal fun getSessionScope(): CoroutineScope?`
- `internal fun setActiveVerificationJob(job: Job)`
- `internal fun cancelActiveVerification()`
- `internal fun getDispatcherProvider(): DispatcherProvider`
- `internal fun getActiveVerificationGeneration(): Long?`
- `internal fun createSessionScopeForTesting(): Job` (test-only)

**Removed from public API (v0.4.1):**
- `ReaderTransport`, `StartVerificationParams`, `NfcEngagementOptions`, `TransportType`, `ConnectionMethod`, `TransportMapper`
- `parseQrEngagement()`, `scanNfcEngagement()`, `startVerification(EngagementResult, ...)`, `startVerificationWithEngagement()`
- `EngagementResult` is now `internal class`

**Removed from public API (v0.4.2):**
- `VerificationCheckType.ISSUER_SIGNATURE`, `VerificationCheckType.DIGEST_INTEGRITY`, `VerificationCheckType.VALIDITY_PERIOD`, `VerificationCheckType.DEVICE_AUTHENTICATION` — ISO integrity checks are now enforced all-or-nothing by `DeviceResponse.verify()`. Any failure produces `PVE04`. `VerificationCheckType` now contains exactly three values: `ISSUER_TRUST`, `ISSUER_CERT_REVOCATION`, `MSO_REVOCATION`. Downstream consumers must remove references to the deleted entries.

---

## 7. Performance and Security Constraints

**Performance:**
- Agents must avoid N+1 query patterns.
- Blocking operations must be avoided in asynchronous contexts (exception: `runBlocking` in Android NFC `onTagDiscovered()` per platform API contract).
- Unnecessary computational complexity must not be introduced.
- Security and correctness take precedence over micro-optimizations.

**Security:**
- All external input must be validated.
- User-provided data must be sanitized.
- Sensitive information must never be logged.
- Cryptographic operations must use secure implementations.
- Platform-specific security features (Play Integrity, App Attestation) must be preserved.
- Token and certificate management must follow existing secure patterns.
- Connectivity checks used before backend-dependent operations must model usable internet connectivity, not only link presence. Do not weaken `NativeUtils.isNetworkAvailable()` to a generic "network attached" check without an explicit design decision.

**KMP-Specific:**
- Platform-specific security implementations must maintain parity.
- Secure storage implementations must be platform-appropriate.

---

## 8. Feature Implementation Protocol

When implementing a feature, agents must:

1. **Identify the correct architectural layer:**
   - Determine if it's interface, application, domain, or infrastructure.

2. **Implement in the correct layer:**
   - Business logic must be implemented in the domain layer (`core/`).
   - Coordination must occur in the application layer.
   - Exposure must occur through the interface layer (`ProximityVerification`).
   - Platform-specific code must use `expect/actual` pattern.
   - Coroutines must use `DispatcherProvider`; no `Dispatchers.*` in `commonMain`.

3. **Add/adjust tests:**
   - Prefer `commonTest` first.
   - Add platform-specific tests only when behavior is platform-bound.
   - If a path cannot be automated (hardware/platform constraints), document manual verification steps.
   - Use `TestDispatcherProvider` for lifecycle/concurrency tests.

4. **Verify:**
   - Execute linting and the full test suite.
   - Ensure no architectural violations.
   - Ensure layers are not bypassed for convenience.

5. **Document:**
   - Update relevant documentation if API changes occur.
   - Document new public APIs.

---

## 9. Bug Fix Protocol

When fixing a bug, agents must:

1. **Reproduce the issue:**
   - Understand the failing behavior.
   - Identify the root cause.

2. **Implement the fix:**
   - Make minimal changes to fix the issue.
   - Ensure the fix addresses the root cause.

3. **Verify:**
   - Ensure no regressions were introduced.

4. **Document:**
   - Add or update tests whenever feasible.
   - If adding an automated test is not feasible, document why and provide manual verification evidence.

---

## 10. Documentation Rules

**When to Update Documentation:**
- Relevant documentation must be updated when behavior changes.
- New configuration variables must be documented.
- Public API changes must be documented.
- New platform-specific features must be documented.

**Documentation Style:**
- Comments must remain concise and technical.
- Redundant comments describing obvious code must be avoided.
- Update `docs/` directory for user-facing documentation.

**Documentation Location:**
- User documentation: `docs/` (INTEGRATION_GUIDE.md).
- Component deep dives: `docs/<component>/README.md` (Lumen-managed).
- Architecture documentation: This file (AGENT.md).
- Risk analysis: `docs/GAP_ANALYSIS.md`.
- Decision log: `docs/rationale.md`.
- Design documents: `docs/design/` (canonical folder for design and planning output).

---

## 11. KMP-Specific Considerations

**Source Sets:**
- Put cross-platform logic in `commonMain`.
- Put platform-specific adapters in platform source sets.
- Platform involved are Android and iOS and source sets are `androidMain` and `iosArm64Main`.
- Current native target configured in Gradle is `iosArm64`; do not assume simulator targets unless explicitly added.
- Mirror tests:
  - Shared tests in `commonTest`.
  - Platform-specific tests only when required.

**Dependency Rules:**
- `commonMain` dependencies must be multiplatform artifacts only.
- Use `api` only when the dependency types appear in your public API.
- Use `implementation` for internals to avoid leaking transitive dependencies.
- Configure dependencies in `proximityverification/build.gradle.kts`.
- Prefer centralized versions in `gradle/libs.versions.toml`.

**Public API Rules (Library-First):**
- Default to internal; make declarations public only when required by consumers.
- `explicitApi()` is not currently enabled; treat public visibility carefully and avoid accidental API expansion.
- If enabling `explicitApi()` is proposed, do it in a dedicated change with migration impact review.
- Do not expose platform types in public APIs.
- Document all public declarations with KDoc, including a small usage example.
- For expected failures, prefer domain errors over throwing exceptions.
- The public API is consumed by Flutter plugins and native apps.
- Changes to public APIs must consider downstream impact.
- Version increments required for breaking changes.

**Platform Boundaries:**

**Port/Adapter Pattern (Preferred for Services):**
- Define public interface in `commonMain` (port).
- Implement in platform source set (adapter).
- Inject adapter via constructor or factory.
- Use this pattern for larger services and complex platform interactions.

**Expect/Actual Pattern (Use Sparingly):**
- Use `expect/actual` for small platform primitives (time, device info, entropy), not for large services.
- Use `expect` declarations in `commonMain/` for platform-specific APIs.
- Provide `actual` implementations in `androidMain/` and `iosArm64Main/`.
- Ensure both platforms implement all `expect` declarations.
- Hide platform behavior behind interfaces (ports) or `expect/actual`.

**Concurrency (Coroutines) — Implemented State:**

The following describes the implemented concurrency model:

- **DispatcherProvider** (implemented): Interface in `commonMain` (`core/handlers/DispatcherProvider.kt`) with platform-specific implementations via `expect fun getDefaultDispatcherProvider()`.
  - `defaultDispatcher`: CPU-bound work.
  - `backgroundDispatcher`: blocking/heavy work (`Dispatchers.IO` on Android, `Dispatchers.Default` on iOS).
  - `callbackDispatcher`: where callbacks are invoked (default: `defaultDispatcher`).
- **Session scope** (implemented): Library owns a per-session `CoroutineScope` (`sessionScope`) created at `initialize()`, cancelled at `close()` or next `initialize()`.
  - `activeVerificationJob` provides single-verification-at-a-time with cancel-previous semantics.
  - Generation counters (`sessionGeneration`, `verificationGeneration`) for lifecycle logging.
- **No direct `Dispatchers.*` runtime usage in `commonMain`** (implemented): execution dispatching uses `DispatcherProvider`; deprecated `verificationCoroutineDispatcher()` remains for compatibility.
- **No `GlobalScope`/`MainScope`** (verified): Not present in codebase.
- **`Dispatchers.Main` in platform source sets** (scoped): Android NFC uses `withContext(Dispatchers.Main)` scoped to the NFC scan performed inside `startVerificationWithNfc()` only. iOS uses `withContext(Dispatchers.Main.immediate)` for foreground check only.
- **CancellationException preservation** (implemented): All layers re-throw `CancellationException`. `close()` during active verification does not fire failure callbacks.
- **Accepted constraint**: `runBlocking` in Android NFC `onTagDiscovered()` is isolated from session scope (Android NFC API requirement). Results are discarded at `suspendCancellableCoroutine` boundary if session is cancelled.

**Concurrency Policy** (from `.cursor/rules/kotlin_multiplatform_libraries.md`):
- The library never switches to Main automatically.
- Consumers decide where callbacks run by providing `callbackDispatcher` (for UI needs, they may pass `Dispatchers.Main` from platform code).
- `close()` cancels the session scope and all children.
- Cancellation must not be reported as a failure callback (do not swallow `CancellationException`).

**Platform Parity:**
- Features must work consistently across Android and iOS.
- Platform-specific implementations must maintain behavioral parity where possible.
- Differences must be documented and justified.

**Testing (Cross-Target):**
- Use `kotlin.test` in `commonTest` so tests run on all targets.
- Prefer contract tests for ports in `commonTest`.
- Keep platform-specific tests minimal and focused on adapters.
- Follow the Arrange-Act-Assert convention for tests.
- Name test variables clearly: `inputX`, `mockX`, `actualX`, `expectedX`, etc.
- Write unit tests for each public function.
- Use test doubles to simulate dependencies (except for third-party dependencies that are not expensive to execute).
- For concurrency tests, use `TestDispatcherProvider` backed by `UnconfinedTestDispatcher`.
- Write acceptance/integration tests for each module following the Given-When-Then convention.

**Build and Publishing:**
- Android artifacts: AAR files published to Maven repositories.
- iOS artifacts: Static framework, podspec, and tar.gz archives.
- Version management: Controlled via `build.gradle.kts`.
- Use semantic versioning (MAJOR breaking, MINOR additive, PATCH fixes).
- Include README.md and CHANGELOG.md at the repository/module level.
- Ensure POM metadata is set in publishing {} in `build.gradle.kts`.
- Avoid breaking changes in public API; prefer additive changes (new overloads, new types).
- Do not modify publishing configuration without approval.

---

## 12. Definition of Done

A task is complete only when:

- Code runs correctly on both Android and iOS (where applicable).
- Relevant lint/test tasks have been executed for touched modules (or blockers are explicitly documented).
- Architectural rules are respected.
- No unintended breaking changes were introduced.
- All constraints in this document were followed.
- Public API changes are documented (if any).
- Platform parity is maintained (if applicable).
- `CancellationException` is not swallowed in any new or modified catch block.
- No `Dispatchers.*` introduced in `commonMain`.

**Critical Quality Requirements:**

- **No complex, unreadable, unmaintainable, untestable, or hard-to-modify solutions.**
  - Solutions must be simple, readable, maintainable, testable, and easily modifiable.

- **All changes must be minimal, surgical, motivated, non-invasive, and must not add unnecessary over-engineering.**
  - Over-engineering that burdens readability, maintainability, and testability is strictly prohibited.

- **Analysis phase required before changes.**
  - Before medium/large changes, provide a point-by-point analysis explaining what is being changed and why.
  - A design document in `docs/` is required for architectural changes, public API redesign, or release pipeline modifications.

- **Production/enterprise level quality.**
  - Changes must be made keeping in mind that the library must be production/enterprise level.

- **No regressions.**
  - Changes, even minimal ones, must not introduce any regression compared to the current code.

- **No disruptive approaches.**
  - No approach that disrupts what is already present.

- **No speculative refactoring.**
  - Small targeted refactoring is allowed only when required to fix a bug, preserve behavior, or keep code testable.
  - Broad refactoring without clear necessity is not allowed.

- **Reuse existing infrastructure.**
  - Do not create new infrastructure calls solely for metrics or observability; reuse existing calls and leverage them for metrics.


**These rules apply to every task.**
---

## 13. Final Principle

Agents must act conservatively.

When uncertain, prefer minimal change, preserve existing structure, avoid assumptions about undocumented behavior, and do not introduce speculative refactors.

Stability and clarity are mandatory.

---

## 14. Behavioral Requirements (Must Preserve)

### 14.1 Initialization Lifecycle (Online Path)
- Generate cryptographic material on device
- Perform integrity (Android Play Integrity; iOS device check/app attestation style flow)
- Register device with backend (VerifierBackend / Relying Party API)
- Receive and store access token
- Bind key API call
- CSR/access-certificate call to obtain a certificate
- Trust manager population using provided trust anchors
- Create `VerificationSession` with:
  - requests / allowMultipleRequests
  - trustManager
  - documentTypeRepository (currently adds ItalianDrivingLicense)
  - algorithm identifiers
  - certChain
  - verification-time transport policy derived from `ProximityVerificationSettings`
  - dispatcherProvider
- Create `sessionScope` with `SupervisorJob() + dispatcherProvider.backgroundDispatcher`

### 14.2 Session Lifecycle
- `initialize()` cancels any previous session scope before starting.
- `close()` cancels session scope, active verification job, and resets state.
- `startVerification*()` methods cancel previous `activeVerificationJob` before launching new one (cancel-previous semantics).
- `CancellationException` during `close()` or cancel-previous does not trigger `onVerificationFailed`.
- `close()` is idempotent.
- Generation counters (`sessionGeneration`, `verificationGeneration`) provide lifecycle logging correlation.

### 14.3 Offline Rule (Explicit Policy)
If the device is offline and previously stored security material exists, initialization and verification must preserve current behavior:
- Existing token material may be reused to continue the flow when online re-registration is not possible.
- If a certificate/key already exists, the app can continue even when certificate validity is expired.

This must remain an explicit policy in domain code (not an accidental behavior).

### 14.4 Verification Entrypoints
- QR scanning happens outside KTM; KTM receives engagement data (encoded string or parsed model).
- NFC scanning is managed by KTM via platform-specific implementations.
- All verification jobs are children of `sessionScope`.

**These behaviors must be preserved in all modifications.**

---

## 15. Release Consistency Checklist

When a change affects release artifacts or public API, ensure all items below are aligned:

- Version source of truth: `proximityverification/build.gradle.kts`.
- Keep `CHANGELOG.md` updated with concrete changes for the target version.
- New `CHANGELOG.md` entries must be consistent with existing history:
  - keep the same structural style already used in this repository (version header + sections like Added/Changed/Fixed when applicable);
  - keep terminology and level of detail coherent with previous entries;
  - do not rewrite past versions except for factual corrections.
- Keep podspec/version flow aligned with release scripts:
  - `proximityverification/proximityverification.podspec`
  - `build_scripts/proximityverification.podspec` (version is adjusted during packaging step).
- Validate packaging with `bash build_scripts/update_build_publish_all.sh --no-publish` before requesting publish.

---

## Documentation Index (Lumen)

- [High-Level Design](docs/high-level-design.md) — Architecture and component map
- [Deployment](docs/deployment.md) — Build scripts and distribution (Android/iOS)
- [core-api](docs/core-api/) — ProximityVerification + VerificationSession (Deep)
- [network](docs/network/) — RelyingPartyApiImpl and Ktor HTTP client (Standard)
- [crypto](docs/crypto/) — Token management, secure area, MSO revocation (Standard)
- [transport](docs/transport/) — BLE/NFC transport selection (Standard)
- [catalog](docs/catalog/) — Document type catalog and request builder (Standard)
- [registry](docs/registry/) — OpenID Federation + IT-Wallet registry resolution chain (Standard)
- [status](docs/status/) — Token Status List prefetch, trust, cache, offline toggle (Standard)
- [platform-android](docs/platform-android/) — Android NFC, Bluetooth, Play Integrity (Light)
- [platform-ios](docs/platform-ios/) — iOS NFC, Secure Enclave (Light)
- [samples](docs/samples/) — Android + iOS sample apps (Light)
- [rationale.md](docs/rationale.md) — Decision log in ADR form: decision, rationale, trade-offs, alternatives rejected

Existing docs (manually maintained — do not overwrite):
- [INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md) — Consumer integration guide
- [GAP_ANALYSIS.md](docs/GAP_ANALYSIS.md) — Risk analysis vs KMP best practices (resolved and open risks)
- [IT-WALLET-REGISTRY_DISCOVERY.md](docs/IT-WALLET-REGISTRY_DISCOVERY.md) — Registry resolution URLs, step by step
- [SECURE_AREA_ANALYSIS.md](docs/SECURE_AREA_ANALYSIS.md) — Secure area comparison

Removed on 2026-08-03: `CURRENT_STATE.md` — redundant with this file plus `high-level-design.md` and
`deployment.md`, last updated 2026-04-07, and citing line numbers that had since moved — and
`VERIFICATION_FLOW.md`, which had one commit in its whole life and whose QR+BLE sequence lives in
`high-level-design.md`. `RATIONALE.md` was renamed `rationale.md` and handed to Lumen.

## Lumen Metadata

| Field | Value |
|-------|-------|
| **Managed by** | [Lumen](https://github.com/anthropics/claude-code) — project knowledge keeper skill |
| **Project type** | Library/SDK (Kotlin Multiplatform) |
| **Domain complexity** | High |
| **Integration density** | 6 (Relying Party REST API, Play Integrity API, IT-Wallet Federation Discovery, IT-Wallet Credential Catalog, IT-Wallet Schema/Claims Registry, Token Status List endpoints) |
| **Scan depths** | Deep: core-api / Standard: network, crypto, transport, catalog, registry, status / Light: platform-android, platform-ios, samples |
| **Fingerprint status** | active |
| **Last scan** | 2026-05-08 |
| **Last update** | 2026-08-03 |
| **Last update commit** | 236499c |
| **Last ingest** | 2026-05-09 |
| **Lumen version** | 2.0 |
- Do not commit generated artifacts under `Products/**`.
