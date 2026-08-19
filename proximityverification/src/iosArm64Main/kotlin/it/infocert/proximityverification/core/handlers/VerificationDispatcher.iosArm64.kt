package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * iOS: verification runs on background dispatcher (Default, not Main).
 * 
 * Heavy verification work (parsing, crypto, transport loops) runs off main thread.
 * Following iOS CoreBluetooth best practices, the transport library should use a dedicated
 * serial dispatch queue for CoreBluetooth delegate callbacks (not main thread).
 * 
 * @deprecated Use [DefaultDispatcherProvider.backgroundDispatcher] instead.
 */
@Deprecated(
    message = "Use DefaultDispatcherProvider.backgroundDispatcher instead",
    replaceWith = ReplaceWith("DefaultDispatcherProvider.backgroundDispatcher")
)
actual fun verificationCoroutineDispatcher(): CoroutineDispatcher = 
    DefaultDispatcherProvider.backgroundDispatcher
