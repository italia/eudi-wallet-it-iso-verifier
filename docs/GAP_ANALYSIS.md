# GAP_ANALYSIS.md

## 1. Evaluation Model

Baseline used for gap comparison:

- Kotlin language best practices for public API typing, error semantics, and state ownership in library code.
- Coroutine structured concurrency (parented scopes, cancellation propagation, non-blocking execution).
- Flow/StateFlow discipline (hot vs cold boundaries, state ownership, platform parity).
- KMP source-set boundary hygiene (commonMain purity, expect/actual scope, adapter placement).
- Android lifecycle-aware concurrency expectations for SDK consumers.
- iOS suspend-to-Swift bridging behavior and exception surface safety.
- Gradle/dependency hygiene for published KMP libraries.
- Cross-target testing discipline for shared logic and platform adapters.
- Rules defined in `.cursor/rules/kotlin_guidelines.md` and `.cursor/rules/kotlin_multiplatform_libraries.md`.

## 2. Addressed Risks (Resolved or Accepted)

The following risks from the previous analysis have been addressed based on observable repository changes:

### Resolved — Main-Thread Overload in Verification Paths (was P0)

**Resolution:** `DispatcherProvider` interface introduced in `commonMain` with platform-specific implementations. `Dispatchers.Main` removed from `commonMain`. iOS verification dispatcher now maps to `Dispatchers.Default`. `Dispatchers.Main` appears only in platform source sets via scoped `withContext` for platform-specific requirements. See `CURRENT_STATE.md` §5 (DispatcherProvider infrastructure, Dispatcher usage patterns).

**Evidence:** `DispatcherProvider.kt`, `DispatcherProvider.android.kt`, `DispatcherProvider.iosArm64.kt` exist. No `import kotlinx.coroutines.Dispatchers` in `ProximityVerification.kt` (commonMain). `verificationCoroutineDispatcher()` deprecated.

### Resolved — Unmanaged Coroutine Scope Lifecycle in Library API (was P0)

**Resolution:** Per-session `CoroutineScope` owned by `ProximityVerification`, created at `initialize()`, cancelled at `close()` or next `initialize()`. All verification jobs are children of `sessionScope` with cancel-previous semantics via `activeVerificationJob`. Public `close()` method added. `SdkNfcScope.kt` deleted. See `CURRENT_STATE.md` §5 (Session scope lifecycle, Scope creation patterns).

**Evidence:** `sessionJob`, `sessionScope`, `activeVerificationJob` properties in `ProximityVerification.kt`. `close()`, `cancelSessionScope()`, `cancelActiveVerification()` methods. 7 lifecycle tests in `ProximityVerificationLifecycleTest.kt`.

### Resolved — Cancellation Semantics Not Preserved End-to-End (was P1)

**Resolution:** All identified cancellation-sensitive catch boundaries now re-throw `CancellationException`. 6 `RelyingPartyApiImpl` methods, 3 `initialize()` blocks, `VerificationSession`, and `clearLibraryLocalStorage()` all preserve cancellation propagation. See `CURRENT_STATE.md` §5 (CancellationException handling).

**Evidence:** Pattern `catch (e: CancellationException) { throw e }` present at all identified locations. Lifecycle test `closeCancelsActiveJobWithoutFailureCallback` validates no spurious failure callback on cancellation.

### Addressed — Blocking Execution Inside NFC Coroutine Flows (was P1, accepted platform constraint)

**Resolution:** Dead code `NfcReaderHandler.android.kt` was deleted and the active `NfcReaderCallback` path was documented. The `runBlocking` usage is intentionally retained due Android NFC API contract constraints and is treated as an accepted platform constraint with explicit documentation. See `CURRENT_STATE.md` §5 (Deleted concurrency infrastructure).

**Evidence:** `NfcReaderHandler.android.kt` absent from repository. KDoc on `NfcReaderCallback` class documents blocking contract.

## 3. Prioritized Risk List

### Risk 1 — iOS Verification Confidence Gap (Target/Test Coverage)

**Priority Level:**
P1 (High structural or maintainability risk)

**Category:**
Testing

**Observed Condition:**
`CURRENT_STATE.md` §1 (Targets declared) shows only `iosArm64`; §7 (iOS tests) reports no iOS-specific test source set.

**Why This Is Risky:**
Platform adapters and iOS runtime behavior are weakly regression-protected. Missing simulator target/test loop raises defect escape probability and slows issue isolation for iOS-specific failures. The `actual` implementations in `iosArm64Main` (crypto, NFC, handlers, DispatcherProvider) have no dedicated test coverage.

**Impact Domain:**
- Platform correctness
- Stability
- Testability

**Mitigation Direction (High-Level Only):**
Increase iOS target/test surface so adapter behavior and suspend bridging can be validated continuously.

### Risk 2 — Interface Layer Concentration and Shared Mutable Session State

**Priority Level:**
P1 (High structural or maintainability risk)

**Category:**
Architecture

**Observed Condition:**
`CURRENT_STATE.md` §6 identifies `ProximityVerification` object as central orchestration entrypoint with mutable session state (`sessionJob`, `sessionScope`, `activeVerificationJob`, `verificationSession`, `dispatcherProvider`, generation counters); §4 notes platform-branch logic in `commonMain`.

**Why This Is Risky:**
The singleton entrypoint concentrates orchestration, mutable state management, session lifecycle, and platform-branching in a single object. Although session lifecycle is now explicit (with `close()` and cancel-previous semantics), the architectural concentration remains: the object manages 6+ mutable private properties and coordinates multiple concerns. This increases change-coupling and makes unit testing of the entry point difficult without exposing internal state via test-only methods.

**Impact Domain:**
- Maintainability
- Testability

**Mitigation Direction (High-Level Only):**
Separate orchestration concerns from API surface to reduce the singleton's responsibility count.

### Risk 3 — Platform Branching Embedded in Shared Business Flow

**Priority Level:**
P2 (Medium architectural drift)

**Category:**
Source Sets

**Observed Condition:**
`CURRENT_STATE.md` §4 (Platform-specific logic inside commonMain) reports `NativeUtils.isAndroid()` / network checks used directly in shared initialization/attestation flow.

**Why This Is Risky:**
Cross-platform logic becomes conditional and harder to reason about, increasing parity drift and reducing the value of common tests as true contract verification.

**Impact Domain:**
- Maintainability
- Platform correctness
- Testability

**Mitigation Direction (High-Level Only):**
Move platform decisions behind explicit abstractions so shared flow remains platform-agnostic.

### Risk 4 — Large Expect/Actual Surface for Service-Level Behavior

**Priority Level:**
P2 (Medium architectural drift)

**Category:**
Architecture

**Observed Condition:**
`CURRENT_STATE.md` §4 (Usage of expect/actual) lists 16+ expect/actual declarations including handlers, crypto operations, and service-level concerns beyond small primitives.

**Why This Is Risky:**
Broad expect/actual contracts couple shared logic tightly to platform behavior, increasing parity management cost and complicating testing/mocking of service-level concerns. The `getDefaultDispatcherProvider` expect/actual is appropriate for its scope; broader service-level declarations (e.g., `PermissionManager`, `IntegrityWarmupManager`, cert generation) increase surface area beyond the "small primitives" guideline in `.cursor/rules/kotlin_multiplatform_libraries.md`.

**Impact Domain:**
- Maintainability
- Testability
- Platform correctness

**Mitigation Direction (High-Level Only):**
Constrain expect/actual to thin primitives and model larger services through explicit common ports plus platform adapters.

### Risk 5 — Build/Quality Gate Incompleteness for a Published KMP Library

**Priority Level:**
P2 (Medium architectural drift)

**Category:**
Build

**Observed Condition:**
`CURRENT_STATE.md` §9 reports no explicit static-analysis/coverage plugins and no CI configuration files; §3 shows mixed dependency declaration patterns including literal versions in both sample module and library module (e.g., `startup-runtime:1.2.0`, `play-integrity:1.4.0`).

**Why This Is Risky:**
Without enforced quality gates and consistent dependency governance, regressions and version drift can reach release artifacts undetected.

**Impact Domain:**
- Stability
- Maintainability
- API compatibility

**Mitigation Direction (High-Level Only):**
Define mandatory automated checks and normalize dependency/version governance across modules.

### Risk 6 — Reactive-State Model Asymmetry Across Platforms

**Priority Level:**
P3 (Low optimization or hygiene issue)

**Category:**
Architecture

**Observed Condition:**
`CURRENT_STATE.md` §5 (Flow / StateFlow / SharedFlow) reports `MutableStateFlow` only in Android NFC internals and no shared/iOS flow model.

**Why This Is Risky:**
State signaling semantics differ by platform path, which can create subtle behavior inconsistencies and increase debugging effort for event-timing issues.

**Impact Domain:**
- Maintainability
- Platform correctness

**Mitigation Direction (High-Level Only):**
Standardize event/state signaling contract across platforms or keep callback-only behavior fully uniform.

## 4. Dedicated Coroutine Risk Analysis (Mandatory Section)

### 4.1 Structured Concurrency

- **Use of structured concurrency:** Substantially improved. All verification jobs are children of `sessionScope`. Cancel-previous semantics via `activeVerificationJob`. No detached `CoroutineScope(SupervisorJob() + ...)` in API methods.
- **Presence of `GlobalScope`/`MainScope`:** Not observed (positive).
- **Unmanaged scopes:** Eliminated in API methods. Remaining detached scopes exist in NFC prompt model (`CoroutineScope(currentCoroutineContext() + androidPromptModel)`) and iOS callback scope — both are implementation-level and inherit job context from the calling coroutine.
- **SupervisorJob usage patterns:** `sessionJob` is `SupervisorJob`, providing fault isolation at session level. Verification failures in one child do not kill the session scope.
- **Lifecycle ownership:** Explicit. `close()` cancels all in-flight work. `initialize()` cancels previous session. Generation counters provide correlation for diagnostics.

### 4.2 Dispatcher Model

- **Hardcoded Dispatchers in shared code:** Removed from runtime paths. No direct `Dispatchers.*` dispatching calls in `commonMain`; dispatching is handled through `DispatcherProvider`.
- **Use of `Dispatchers.Main` in `commonMain`:** Removed.
- **Dispatcher injection patterns:** `DispatcherProvider` interface with platform-specific implementations. Consumer-configurable via `configureDispatcherProvider()`. `VerificationSession` receives `DispatcherProvider` in constructor.
- **Threading assumptions in shared code:** Eliminated. Platform dispatcher mapping (`IO` vs `Default`) is encapsulated in platform source sets.
- **Deprecated legacy:** `verificationCoroutineDispatcher()` is deprecated with `@Deprecated` annotation pointing to `DispatcherProvider.backgroundDispatcher`.

### 4.3 Flow / StateFlow Discipline

- **Flow vs StateFlow usage correctness:** Limited cross-platform reactive contract. Snapshot shows Android-only `MutableStateFlow` usage in NFC internals.
- **Cold vs hot flow misuse:** No major misuse signal in shared code because flow usage is minimal; residual risk from platform-only hot state channels without shared contract.

### 4.4 Cancellation and Exceptions

- **Cancellation propagation:** Strong. `CancellationException` is re-thrown at all identified layers (network, initialization, verification session, cleanup).
- **Exception handling in coroutines:** Pattern is consistent: `catch (e: CancellationException) { throw e }` before generic `catch (e: Exception)` or `if (e is CancellationException) throw e` guard.
- **Cancellation in callbacks:** `close()` during active verification does not fire `onVerificationFailed` (verified by test). Cancel-previous stops old verification without failure callback.
- **Accepted isolation:** `runBlocking` in Android NFC `onTagDiscovered()` is intentionally disconnected from session scope (Android NFC API requirement). Documented in KDoc.

### 4.5 Platform Coroutine Usage

- **Coroutine usage inside platform layers:** Present in Android/iOS NFC implementations; complexity managed via session scope hierarchy.
- **Blocking calls inside coroutines:** `runBlocking` in Android NFC callback is documented and accepted (Android NFC API contract).
- **Android lifecycle alignment with `ViewModel`:** Not aligned at library boundary (library APIs use internal session scope). Sample integration uses plain scopes.
- **Coroutine lifecycle alignment for SDK API:** Explicit. `close()` method provides deterministic teardown. `initialize()` cancels previous session.

### 4.6 iOS Suspend Bridging and Framework Exposure

- **Suspend bridging to Swift:** Present and functional (`CURRENT_STATE.md` §8).
- **Exception surface toward Swift:** Improved. `CancellationException` is preserved as-is in `initialize()` (annotated `@Throws(CancellationException::class)`). Swift callers receive `CancellationError`.
- **Coroutine usage in iOS framework exposure:** Background dispatcher used for verification work. `Dispatchers.Main.immediate` scoped to foreground check only.

### 4.7 Kotlin/Native Memory Model Considerations

- **Deprecated freezing/thread-local APIs:** Not observed.
- **Residual memory-model risk:** Mutable singleton state in `ProximityVerification` is accessed from session scope's background dispatcher. Session lifecycle provides ordering guarantees (initialize before verify, cancel before re-initialize), but concurrent access from multiple threads remains a theoretical risk on the new memory model.

### 4.8 Coroutine Overall Judgment

Coroutine usage has improved substantially:

- Session scope lifecycle with explicit `close()` and cancel-previous semantics.
- `DispatcherProvider` injection replaces hardcoded dispatchers.
- `CancellationException` preserved end-to-end.
- No detached scopes in API methods.
- Lifecycle tests provide regression coverage.

Residual risks are:
- Mutable singleton state concentration (Risk 2).
- Android-only `MutableStateFlow` without cross-platform contract (Risk 6).
- Missing iOS test coverage for platform adapters (Risk 1).

## 5. Summary

- **Total risks identified:** 6 (down from 10; 4 addressed)
- **Distribution by priority:** P0: 0, P1: 2, P2: 3, P3: 1
- **Addressed since previous analysis:**
  - Main-Thread Overload (was P0) — resolved via DispatcherProvider
  - Unmanaged Coroutine Scope Lifecycle (was P0) — resolved via session scope + close()
  - Cancellation Semantics (was P1) — resolved via end-to-end CancellationException propagation
  - Blocking NFC Flows (was P1) — addressed as accepted platform constraint (`runBlocking` retained by design, documented)
- **Most critical remaining weaknesses:**
  - Missing iOS test infrastructure (no simulator target, no iosArm64Test).
  - Singleton entrypoint concentration with mutable session state.
- **Most critical remaining coroutine-related risk:**
  - Mutable singleton state accessed across dispatcher boundaries without explicit synchronization.
