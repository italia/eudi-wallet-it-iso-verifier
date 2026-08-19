package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Android dispatcher provider.
 * 
 * - defaultDispatcher: Dispatchers.Default (CPU-bound work)
 * - backgroundDispatcher: Dispatchers.IO (blocking/heavy work)
 * - callbackDispatcher: Defaults to defaultDispatcher (callbacks, consumer can override)
 */
private object AndroidDefaultDispatcherProvider : DispatcherProvider {
    override val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    override val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO
    override val callbackDispatcher: CoroutineDispatcher = defaultDispatcher
}

public actual fun getDefaultDispatcherProvider(): DispatcherProvider = AndroidDefaultDispatcherProvider
