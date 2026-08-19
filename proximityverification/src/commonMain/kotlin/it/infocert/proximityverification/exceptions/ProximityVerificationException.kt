package it.infocert.proximityverification.exceptions

/**
 * Base class for all exceptions thrown by the Proximity Verification SDK.
 *
 * @param message description of the error
 * @param cause underlying cause, if any
 */
open class ProximityVerificationException(
    override val message: String,
    override val cause: Throwable? = null,
    val code: ProximityVerificationExceptionCode = ProximityVerificationExceptionCode.PVE00()
) : Exception(message, cause)

