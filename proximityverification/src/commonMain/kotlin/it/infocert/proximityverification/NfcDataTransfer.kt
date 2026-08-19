package it.infocert.proximityverification

import it.infocert.proximityverification.callback.VerificationCallback
import it.infocert.proximityverification.core.handlers.DispatcherProvider
import org.multipaz.mdoc.transport.NfcTransportMdocReader

internal expect suspend fun executeWithNfcDataTransferTag(
    platformContext: Any?,
    transport: NfcTransportMdocReader,
    scanMessage: String,
    dispatcherProvider: DispatcherProvider,
    callback: VerificationCallback,
    block: suspend () -> Unit
): Unit
