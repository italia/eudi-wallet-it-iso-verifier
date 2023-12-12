package it.ipzs.preferences

sealed interface DatastorePrefResult<out T>{
    data object NotFetched: DatastorePrefResult<Nothing>
    data object Missing: DatastorePrefResult<Nothing>

    data class Fetched<T>(val value: T): DatastorePrefResult<T>

}