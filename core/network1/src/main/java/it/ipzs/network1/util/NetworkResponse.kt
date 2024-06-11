package it.ipzs.network.util

sealed class NetworkResponse<T>(val data: T? = null, val message : String? = null) {

    class Success<T>(data: T) : NetworkResponse<T>(data = data)
    class Error<T>(message: String?, data: T? = null) : NetworkResponse<T>(data = data, message = message)
    class Loading<T>(data: T? = null) : NetworkResponse<T>(data)




}