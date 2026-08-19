package it.infocert.proximityverification.core.models

import it.infocert.proximityverification.core.models.document.DocumentVerificationResult
import it.infocert.proximityverification.core.models.request.RequestOutcome

sealed class VerificationOutcome {
    /**
     * @property requestOutcome the inventory of what was requested and what the wallet returned.
     *   Additive information: it never affects whether the verification succeeded. Use it to apply a
     *   mandatory-claim policy, which ISO/IEC 18013-5 cannot impose on the wallet.
     */
    data class DocumentReceived(
        val sessionInfo: SessionInfo,
        val documents: List<DocumentVerificationResult>,
        val requestOutcome: RequestOutcome
    ) : VerificationOutcome()

    data class ProtocolFailed(
        val reason: ProtocolFailureReason,
        val message: String,
        val code: String? = null
    ) : VerificationOutcome()
}
