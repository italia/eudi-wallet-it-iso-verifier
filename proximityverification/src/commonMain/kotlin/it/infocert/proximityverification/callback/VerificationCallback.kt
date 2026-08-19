package it.infocert.proximityverification.callback

import it.infocert.proximityverification.core.models.VerificationOutcome
import it.infocert.proximityverification.lifecycle.VerificationStep

interface VerificationCallback {
    fun onVerificationUpdate(step: VerificationStep)
    fun onVerificationCompleted(outcome: VerificationOutcome)
}