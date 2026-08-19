package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * iOS dispatcher provider.
 * 
 * - defaultDispatcher: Dispatchers.Default (CPU-bound work)
 * - backgroundDispatcher: Dispatchers.Default (blocking/heavy work; Dispatchers.IO is JVM-only)
 * - callbackDispatcher: Defaults to defaultDispatcher (callbacks, consumer can override)
 * 
 * Note: Following iOS CoreBluetooth best practices, the transport library should use a dedicated
 * serial dispatch queue for CoreBluetooth operations (not main thread). Heavy verification work
 * (parsing, crypto, loops) runs on backgroundDispatcher and does not need main thread.
 */
private object IosDefaultDispatcherProvider : DispatcherProvider {
    override val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    override val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default
    override val callbackDispatcher: CoroutineDispatcher = defaultDispatcher
}

public actual fun getDefaultDispatcherProvider(): DispatcherProvider = IosDefaultDispatcherProvider
