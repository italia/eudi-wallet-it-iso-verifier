package it.infocert.proximityverification.network.model

import it.infocert.proximityverification.exceptions.ProximityVerificationException

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: Int, val error: ErrorResponse?) : ApiResult<Nothing>()
    data class InternalError(val exception: ProximityVerificationException) : ApiResult<Nothing>()
}