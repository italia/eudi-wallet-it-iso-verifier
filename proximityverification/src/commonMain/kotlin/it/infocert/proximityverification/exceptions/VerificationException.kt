package it.infocert.proximityverification.exceptions

/**
 * Exception thrown during the execution of the verification flow.
 */
class VerificationException(
    message: String,
    cause: Throwable? = null,
    code: ProximityVerificationExceptionCode
) : ProximityVerificationException(message, cause, code)