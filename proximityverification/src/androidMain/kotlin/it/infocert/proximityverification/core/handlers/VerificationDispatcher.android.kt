package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Android: verification runs on IO dispatcher (can run off main thread).
 * 
 * @deprecated Use [DefaultDispatcherProvider.backgroundDispatcher] instead.
 */
@Deprecated(
    message = "Use DefaultDispatcherProvider.backgroundDispatcher instead",
    replaceWith = ReplaceWith("DefaultDispatcherProvider.backgroundDispatcher")
)
actual fun verificationCoroutineDispatcher(): CoroutineDispatcher = 
    DefaultDispatcherProvider.backgroundDispatcher
