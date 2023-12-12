package it.ipzs.utils

sealed interface Status<out T>{
    data object Idle: Status<Nothing>
    data object Loading: Status<Nothing>
    data class Success<T>(val value: T): Status<T>
    data class Failure(val error: Throwable): Status<Nothing>

}