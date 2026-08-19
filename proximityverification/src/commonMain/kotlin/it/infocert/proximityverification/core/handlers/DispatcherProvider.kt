package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Provides dispatchers for verification operations.
 * 
 * Semantics:
 * - [defaultDispatcher]: CPU-bound work (parsing, crypto operations)
 * - [backgroundDispatcher]: Blocking/heavy work (IO, network, long-running operations)
 *   - Android: Maps to Dispatchers.IO
 *   - iOS: Maps to Dispatchers.Default (Dispatchers.IO is JVM-only)
 * - [callbackDispatcher]: Where callbacks are invoked (default: defaultDispatcher)
 *   - Consumers can override via configuration if they need Main thread for UI updates
 */
public interface DispatcherProvider {
    public val defaultDispatcher: CoroutineDispatcher
    public val backgroundDispatcher: CoroutineDispatcher
    public val callbackDispatcher: CoroutineDispatcher
}

/**
 * Default dispatcher provider for the current platform.
 * 
 * Platform-specific implementations:
 * - Android: Uses Dispatchers.IO for background work
 * - iOS: Uses Dispatchers.Default for background work
 */
public expect fun getDefaultDispatcherProvider(): DispatcherProvider

/**
 * Convenience property to access the default dispatcher provider.
 * This is a computed property that delegates to [getDefaultDispatcherProvider].
 */
public val DefaultDispatcherProvider: DispatcherProvider
    get() = getDefaultDispatcherProvider()
