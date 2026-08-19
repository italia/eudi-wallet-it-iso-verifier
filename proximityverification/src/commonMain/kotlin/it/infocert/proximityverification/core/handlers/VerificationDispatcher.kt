package it.infocert.proximityverification.core.handlers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-specific dispatcher for verification coroutines (runVerification / runVerificationWithEngagement).
 * - Android: Dispatchers.IO (verification can run off main thread).
 * - iOS: Dispatchers.Default (heavy verification work runs off main thread; transport library handles CoreBluetooth on dedicated queue).
 * 
 * @deprecated Use [DispatcherProvider.backgroundDispatcher] instead. This function is kept for backward compatibility
 * and delegates to [DefaultDispatcherProvider.backgroundDispatcher].
 */
@Deprecated(
    message = "Use DispatcherProvider.backgroundDispatcher instead",
    replaceWith = ReplaceWith("DefaultDispatcherProvider.backgroundDispatcher")
)
expect fun verificationCoroutineDispatcher(): CoroutineDispatcher
