Apply these rules when developing Kotlin Multiplatform (KMP) libraries (shared modules and published artifacts).

## Scope

- Applies to KMP library modules and shared code in `proximityverification` module.
- Applies to `commonMain/commonTest` and platform source sets (`androidMain`, `iosArm64Main`, etc.).
- Goal: stable, minimal public API; no platform leakage; testable and publishable.

## Source Sets

- Put cross-platform logic in `commonMain`.
- Put platform-specific adapters in platform source sets.
- Platform involved are Android and iOS and source sets are `androidMain` and `iosArm64Main`.
- Mirror tests:
  - Shared tests in `commonTest`.
  - Platform-specific tests only when required.

Example layout:
- `proximityverification/src/commonMain/kotlin/...`
- `proximityverification/src/androidMain/kotlin/...`
- `proximityverification/src/iosArm64Main/kotlin/...`
- `proximityverification/src/commonTest/kotlin/...`

## Dependency Rules

- `commonMain` dependencies must be multiplatform artifacts only.
- Use `api` only when the dependency types appear in your public API.
- Use `implementation` for internals to avoid leaking transitive dependencies.

Reference:
- Configure dependencies in `proximityverification/build.gradle.kts`
- Prefer centralized versions in `gradle/libs.versions.toml`

Example (Gradle Kotlin DSL):
```kotlin
dependencies {
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
}
```

## Public API Rules (Library-First)

- Default to internal; make declarations public only when required by consumers.
- Enable explicit API mode in published/shared modules:
- Add explicitApi() in the module build.gradle.kts.
- Do not expose platform types in public APIs.
- Document all public declarations with KDoc, including a small usage example.
- For expected failures, prefer domain errors over throwing exceptions.

Example error model:
```kotlin
package library_errors

public sealed interface LibraryError {
    public data class NetworkFailure(public val reason: String) : LibraryError
    public data class InvalidInput(public val fieldName: String) : LibraryError
}
```

## Platform Boundaries

Use Port/Adapter (preferred for services):
- Define public interface in commonMain (port).
- Implement in platform source set (adapter).
- Inject adapter via constructor or factory.

Example port (commonMain):
```kotlin
package key_value_storage

public data class StorageKey(public val value: String)
public data class StorageValue(public val value: String)

public interface KeyValueStorage {
    public suspend fun saveValue(key: StorageKey, value: StorageValue): Unit
    public suspend fun readValue(key: StorageKey): StorageValue?
}
```

Example shared decorator (commonMain):
```kotlin
package key_value_storage

public class CachedKeyValueStorage(private val delegateStorage: KeyValueStorage) : KeyValueStorage {
    private val cacheByKey: MutableMap<StorageKey, StorageValue> = mutableMapOf()
    override suspend fun saveValue(key: StorageKey, value: StorageValue): Unit {
        cacheByKey[key] = value
        delegateStorage.saveValue(key, value)
    }
    override suspend fun readValue(key: StorageKey): StorageValue? {
        val cachedValue: StorageValue? = cacheByKey[key]
        if (cachedValue != null) {
            return cachedValue
        }
        val storedValue: StorageValue? = delegateStorage.readValue(key)
        if (storedValue != null) {
            cacheByKey[key] = storedValue
        }
        return storedValue
    }
}
```

## expect/actual (Use Sparingly)
Use expect/actual for small platform primitives (time, device info, entropy), not for large services.

Example (commonMain):
```kotlin
package time_provider

public interface TimeProvider {
    public fun getEpochMilliseconds(): Long
}

public expect class PlatformTimeProvider() : TimeProvider
```
Example (androidMain):
```kotlin
package time_provider

public actual class PlatformTimeProvider actual constructor() : TimeProvider {
    override fun getEpochMilliseconds(): Long {
        return System.currentTimeMillis()
    }
}
```
Example (iosMain):
```kotlin
package time_provider

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

public actual class PlatformTimeProvider actual constructor() : TimeProvider {
    override fun getEpochMilliseconds(): Long {
        val seconds: Double = NSDate().timeIntervalSince1970
        return (seconds * 1000.0).toLong()
    }
}
```
Sostituisci la sezione ## Concurrency (Coroutines) nella rule kotlin_multiplatform_libraries.md con questa versione (include A e B/case 1 + callback; C esclusa; D adattata al lifecycle interno):

## Concurrency (Coroutines)

- Keep the library as pure as possible:
  - Prefer library-owned callbacks (interfaces/classes in `commonMain`) to report progress and results.
  - Do not expose platform threading concepts in the public API.
- Do not create global scopes in libraries (`GlobalScope`, `MainScope`).
- In `commonMain`, do not reference `Dispatchers.*` directly (including `Dispatchers.Main`).
- Inject dispatchers via a `DispatcherProvider` and keep the semantics consistent across Android+iOS:
  - `defaultDispatcher`: CPU-bound work.
  - `backgroundDispatcher`: blocking/heavy work.
    - On Android it can map to `Dispatchers.IO`.
    - On iOS it must map to `Dispatchers.Default` because `Dispatchers.IO` is JVM-only.  [oai_citation:0‡Kotlin](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html)
  - `callbackDispatcher`: where callbacks are invoked (default should be `defaultDispatcher`).
- Case 1 (default, “pure” library):
  - The library never switches to Main automatically.
  - Consumers decide where callbacks run by providing `callbackDispatcher` (for UI needs, they may pass `Dispatchers.Main` from platform code).  [oai_citation:1‡Kotlin](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-main.html)
- Lifecycle:
  - The library owns its internal scope and exposes a public method (verb) that cancels it (for example `executeCloseLibrary()`).
  - Cancellation must not be reported as a failure callback (do not swallow `CancellationException`).

Example (commonMain) dispatcher provider:
```kotlin
package dispatcher_provider

import kotlinx.coroutines.CoroutineDispatcher

public interface DispatcherProvider {
    public val defaultDispatcher: CoroutineDispatcher
    public val backgroundDispatcher: CoroutineDispatcher
    public val callbackDispatcher: CoroutineDispatcher
}
```
Example (commonMain) callback model:
```kotlin
package library_callback

public sealed interface LibraryEvent {
    public data class ProgressChanged(public val percentage: Int) : LibraryEvent
    public object Completed : LibraryEvent
    public data class Failed(public val cause: Throwable) : LibraryEvent
}

public fun interface LibraryEventCallback {
    public fun handleEvent(event: LibraryEvent): Unit
}
```
Example (commonMain) internal scope + callback dispatch + close:
```kotlin
package library_engine

import dispatcher_provider.DispatcherProvider
import library_callback.LibraryEvent
import library_callback.LibraryEventCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public data class LibraryConfiguration(
    public val dispatcherProvider: DispatcherProvider,
    public val callback: LibraryEventCallback,
)

public class LibraryEngine(private val configuration: LibraryConfiguration) {
    private val supervisorJob: Job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(supervisorJob + configuration.dispatcherProvider.backgroundDispatcher)

    public fun executeStartProcessing(): Unit {
        scope.launch {
            try {
                executeEmitEvent(LibraryEvent.ProgressChanged(percentage = 0))
                executeEmitEvent(LibraryEvent.Completed)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                executeEmitEvent(LibraryEvent.Failed(cause = err))
            }
        }
    }

    public fun executeCloseLibrary(): Unit {
        supervisorJob.cancel()
    }

    private suspend fun executeEmitEvent(event: LibraryEvent): Unit {
        withContext(configuration.dispatcherProvider.callbackDispatcher) {
            configuration.callback.handleEvent(event)
        }
    }
}
```

Example (platform) mapping suggestion:
```kotlin
androidMain: backgroundDispatcher = Dispatchers.IO
iosArm64Main: backgroundDispatcher = Dispatchers.Default  ￼
```

## Testing (Cross-Target)
- Use kotlin.test in commonTest so tests run on all targets.
- Prefer contract tests for ports in commonTest.
- Keep platform-specific tests minimal and focused on adapters.

Example (commonTest):
```kotlin
package identifier_formatter

import kotlin.test.Test
import kotlin.test.assertEquals

public class IdentifierFormatterTest {
    @Test
    public fun formatIdentifier_shouldAddPrefix(): Unit {
        val inputIdentifier: String = "123"
        val actualValue: String = formatIdentifier(inputIdentifier)
        val expectedValue: String = "id-123"
        assertEquals(expectedValue, actualValue)
    }
}
```

## Publishing Rules (If the module is shipped)
- Use semantic versioning (MAJOR breaking, MINOR additive, PATCH fixes).
- Include README.md and CHANGELOG.md at the repository/module level.
- Ensure POM metadata is set in publishing {} in build.gradle.kts.
- Avoid breaking changes in public API; prefer additive changes (new overloads, new types).