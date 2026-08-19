package it.infocert.proximityverification.network

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.matthewnelson.encoding.base64.Base64
import it.infocert.proximityverification.Utils
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.model.ApiResult
import it.infocert.proximityverification.network.model.ErrorResponse
import it.infocert.proximityverification.network.model.common.*
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException

private const val TAG = "RelyingPartyApiImpl"

class RelyingPartyApiImpl(
    private val baseUrl: String,
    private var client: HttpClient = HttpClientProvider.client,
    private val isNetworkAvailable: () -> Boolean = { NativeUtils.isNetworkAvailable() }
) : RelyingPartyApi {

    fun overrideClient(newClient: HttpClient) {
        client = newClient
    }

    private fun restError(
        status: HttpStatusCode,
        codeFactory: () -> ProximityVerificationExceptionCode,
        errorCode: String,
        errorDesc: String
    ): ApiResult.InternalError {
        val codeInstance = codeFactory()
        val message =
            "${status.value} – ${codeInstance.details} – [error=$errorCode, description=$errorDesc]"
        return ApiResult.InternalError(
            ProximityVerificationException(
                message,
                code = codeInstance
            )
        )
    }

    override suspend fun fetchNonce(): ApiResult<NonceResponse> {
        val mn = "fetchNonce"

        Logger.debug(TAG) { "$mn - GET $baseUrl/nonce" }

        return try {
            if (!isNetworkAvailable()) {
                throw ProximityVerificationException(
                    "Device is not online",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val response = client.get("$baseUrl/nonce") {
                timeout {
                    requestTimeoutMillis = 45000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 25000
                }
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrNull()
            Logger.debug(TAG) { "$mn - Response Status: ${response.status}" }
            Logger.debug(TAG) { "$mn - Response Body: $responseBody" }

            return if (response.status.isSuccess()) {
                val body = Json.decodeFromString<NonceResponse>(responseBody ?: "")
                ApiResult.Success(body)
            } else {
                val errorResponse: ErrorResponse? = try {
                    Json.decodeFromString<ErrorResponse>(responseBody ?: "")
                } catch (e: Exception) {
                    Logger.warn(TAG) {
                        "$mn - Failed to parse error response JSON: ${e.message} - Raw body: $responseBody"
                    }
                    null
                }

                val errorCode = errorResponse?.error ?: "UNKNOWN_ERROR"
                val errorDescription =
                    errorResponse?.errorDescription ?: "No error description provided"

                Logger.error(TAG) { "$mn - Backend error: [error=$errorCode, description=$errorDescription]" }

                when (response.status) {
                    HttpStatusCode.InternalServerError -> restError(
                        HttpStatusCode.InternalServerError,
                        ProximityVerificationExceptionCode.PVE21::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.ServiceUnavailable -> restError(
                        HttpStatusCode.ServiceUnavailable,
                        ProximityVerificationExceptionCode.PVE22::default,
                        errorCode,
                        errorDescription
                    )

                    else -> restError(
                        response.status,
                        ProximityVerificationExceptionCode.PVE08::default,
                        errorCode,
                        errorDescription
                    )
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            Logger.error(TAG) { "$mn - Timeout occurred: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Timeout during fetchNonce request",
                    e,
                    code = ProximityVerificationExceptionCode.PVE16.default()
                )
            )
        } catch (e: IOException) {
            Logger.error(TAG) { "$mn - Network I/O error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Network unavailable or interrupted",
                    e,
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - ProximityVerificationException: ${e.message}" }
            ApiResult.InternalError(e)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Unexpected client error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Generic REST client error during fetchNonce",
                    e,
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            )
        }
    }

    override suspend fun registerDevice(deviceInfo: DeviceInfo, data: RegisterDeviceData?): ApiResult<RegisterDeviceResponse> {
        val mn = "registerDevice"
        val requestBody = RegisterDeviceRequest(data = data, deviceInfo = deviceInfo)
        val serializedBody = Json.encodeToString(requestBody)

        Logger.debug(TAG) { "$mn - POST $baseUrl/register-device" }
        Logger.debug(TAG) { "$mn - Body: $serializedBody" }

        return try {
            if (!isNetworkAvailable()) {
                throw ProximityVerificationException(
                    "Device is not online",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val response = client.post("$baseUrl/register-device") {
                timeout {
                    requestTimeoutMillis = 45000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 25000
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrNull()
            Logger.debug(TAG) { "$mn - Response Status: ${response.status}" }
            Logger.debug(TAG) { "$mn - Response Body: $responseBody" }

            return if (response.status.isSuccess()) {
                val body = Json.decodeFromString<RegisterDeviceResponse>(responseBody ?: "")
                ApiResult.Success(body)
            } else {
                val errorResponse: ErrorResponse? = try {
                    Json.decodeFromString<ErrorResponse>(responseBody ?: "")
                } catch (e: Exception) {
                    Logger.warn(TAG) {
                        "$mn - Failed to parse error response JSON: ${e.message} - Raw body: $responseBody"
                    }
                    null
                }

                val errorCode = errorResponse?.error ?: "UNKNOWN_ERROR"
                val errorDescription =
                    errorResponse?.errorDescription ?: "No error description provided"

                Logger.error(TAG) { "$mn - Backend error: [error=$errorCode, description=$errorDescription]" }

                when (response.status) {
                    HttpStatusCode.BadRequest -> restError(
                        HttpStatusCode.BadRequest,
                        ProximityVerificationExceptionCode.PVE17::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.Unauthorized -> restError(
                        HttpStatusCode.Unauthorized,
                        ProximityVerificationExceptionCode.PVE23::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.Forbidden -> restError(
                        HttpStatusCode.Forbidden,
                        ProximityVerificationExceptionCode.PVE18::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.NotFound -> restError(
                        HttpStatusCode.NotFound,
                        ProximityVerificationExceptionCode.PVE19::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.UnprocessableEntity -> restError(
                        HttpStatusCode.UnprocessableEntity,
                        ProximityVerificationExceptionCode.PVE20::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.InternalServerError -> restError(
                        HttpStatusCode.InternalServerError,
                        ProximityVerificationExceptionCode.PVE21::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.ServiceUnavailable -> restError(
                        HttpStatusCode.ServiceUnavailable,
                        ProximityVerificationExceptionCode.PVE22::default,
                        errorCode,
                        errorDescription
                    )

                    else -> restError(
                        response.status,
                        ProximityVerificationExceptionCode.PVE08::default,
                        errorCode,
                        errorDescription
                    )
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            Logger.error(TAG) { "$mn - Timeout occurred: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Timeout during registerDevice request",
                    e,
                    code = ProximityVerificationExceptionCode.PVE16.default()
                )
            )
        } catch (e: IOException) {
            Logger.error(TAG) { "$mn - Network I/O error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Network unavailable or interrupted",
                    e,
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - ProximityVerificationException: ${e.message}" }
            ApiResult.InternalError(e)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Unexpected client error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Generic REST client error during registerDevice",
                    e,
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            )
        }
    }

    override suspend fun refreshToken(refreshToken: String): ApiResult<RefreshTokenResponse> {
        val mn = "refreshToken"
        val requestBody = RefreshTokenRequest(refreshToken = refreshToken)
        val serializedBody = Json.encodeToString(requestBody)

        Logger.debug(TAG) { "$mn - POST $baseUrl/refresh-token" }
        Logger.debug(TAG) { "$mn - Body: $serializedBody" }

        return try {
            if (!isNetworkAvailable()) {
                throw ProximityVerificationException(
                    "Device is not online",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val response = client.post("$baseUrl/refresh-token") {
                timeout {
                    requestTimeoutMillis = 45000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 25000
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrNull()
            Logger.debug(TAG) { "$mn - Response Status: ${response.status}" }
            Logger.debug(TAG) { "$mn - Response Body: $responseBody" }

            return if (response.status.isSuccess()) {
                val body = Json.decodeFromString<RefreshTokenResponse>(responseBody ?: "")
                ApiResult.Success(body)
            } else {
                val errorResponse: ErrorResponse? = try {
                    Json.decodeFromString<ErrorResponse>(responseBody ?: "")
                } catch (e: Exception) {
                    Logger.warn(TAG) {
                        "$mn - Failed to parse error response JSON: ${e.message} - Raw body: $responseBody"
                    }
                    null
                }

                val errorCode = errorResponse?.error ?: "UNKNOWN_ERROR"
                val errorDescription =
                    errorResponse?.errorDescription ?: "No error description provided"

                Logger.error(TAG) { "$mn - Backend error: [error=$errorCode, description=$errorDescription]" }

                when (response.status) {
                    HttpStatusCode.BadRequest -> restError(
                        HttpStatusCode.BadRequest,
                        ProximityVerificationExceptionCode.PVE17::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.Unauthorized -> restError(
                        HttpStatusCode.Unauthorized,
                        ProximityVerificationExceptionCode.PVE23::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.Forbidden -> restError(
                        HttpStatusCode.Forbidden,
                        ProximityVerificationExceptionCode.PVE18::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.NotFound -> restError(
                        HttpStatusCode.NotFound,
                        ProximityVerificationExceptionCode.PVE19::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.UnprocessableEntity -> restError(
                        HttpStatusCode.UnprocessableEntity,
                        ProximityVerificationExceptionCode.PVE20::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.InternalServerError -> restError(
                        HttpStatusCode.InternalServerError,
                        ProximityVerificationExceptionCode.PVE21::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.ServiceUnavailable -> restError(
                        HttpStatusCode.ServiceUnavailable,
                        ProximityVerificationExceptionCode.PVE22::default,
                        errorCode,
                        errorDescription
                    )

                    else -> restError(
                        response.status,
                        ProximityVerificationExceptionCode.PVE08::default,
                        errorCode,
                        errorDescription
                    )
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            Logger.error(TAG) { "$mn - Timeout occurred: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Timeout during refreshToken request",
                    e,
                    code = ProximityVerificationExceptionCode.PVE16.default()
                )
            )
        } catch (e: IOException) {
            Logger.error(TAG) { "$mn - Network I/O error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Network unavailable or interrupted",
                    e,
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - ProximityVerificationException: ${e.message}" }
            ApiResult.InternalError(e)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Unexpected client error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Generic REST client error during refreshToken",
                    e,
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            )
        }
    }

    override suspend fun bindKeys(accessToken: String, jwt: String): ApiResult<KeyBindingResponse> {
        val mn = "bindKeys"
        val requestBody = KeyBindingRequest(assertion = jwt)
        val serializedBody = Json.encodeToString(requestBody)

        Logger.debug(TAG) { "$mn - POST $baseUrl/key-binding" }
        Logger.debug(TAG) { "$mn - Headers: Authorization=Bearer $accessToken, Content-Type=application/json" }
        Logger.debug(TAG) { "$mn - Body: $serializedBody" }

        return try {
            if (!isNetworkAvailable()) {
                throw ProximityVerificationException(
                    "Device is not online",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val response = client.post("$baseUrl/key-binding") {
                timeout {
                    requestTimeoutMillis = 45000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 25000
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrNull()
            Logger.debug(TAG) { "$mn - Response Status: ${response.status}" }
            Logger.debug(TAG) { "$mn - Response Body: $responseBody" }

            return if (response.status.isSuccess()) {
                ApiResult.Success(KeyBindingResponse())
            }else{
                val errorResponse: ErrorResponse? = try {
                    Json.decodeFromString<ErrorResponse>(responseBody ?: "")
                } catch (e: Exception) {
                    Logger.warn(TAG) {
                        "$mn - Failed to parse error response JSON: ${e.message} - Raw body: $responseBody"
                    }
                    null
                }
                val errorCode = errorResponse?.error ?: "UNKNOWN_ERROR"
                val errorDescription =
                    errorResponse?.errorDescription ?: "No error description provided"

                return when (response.status) {

                    HttpStatusCode.BadRequest -> restError(
                        HttpStatusCode.BadRequest,
                        ProximityVerificationExceptionCode.PVE17::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.Unauthorized -> restError(
                        HttpStatusCode.Unauthorized,
                        ProximityVerificationExceptionCode.PVE23::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.Forbidden -> restError(
                        HttpStatusCode.Forbidden,
                        ProximityVerificationExceptionCode.PVE18::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.NotFound -> restError(
                        HttpStatusCode.NotFound,
                        ProximityVerificationExceptionCode.PVE19::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.UnprocessableEntity -> restError(
                        HttpStatusCode.UnprocessableEntity,
                        ProximityVerificationExceptionCode.PVE20::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.InternalServerError -> restError(
                        HttpStatusCode.InternalServerError,
                        ProximityVerificationExceptionCode.PVE21::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.ServiceUnavailable -> restError(
                        HttpStatusCode.ServiceUnavailable,
                        ProximityVerificationExceptionCode.PVE22::default,
                        errorCode,
                        errorDescription
                    )

                    else -> restError(
                        response.status,
                        ProximityVerificationExceptionCode.PVE08::default,
                        errorCode,
                        errorDescription
                    )
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            Logger.error(TAG) { "$mn - Timeout occurred: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Timeout during bindKeys request",
                    e,
                    code = ProximityVerificationExceptionCode.PVE16.default()
                )
            )
        } catch (e: IOException) {
            Logger.error(TAG) { "$mn - Network I/O error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Network unavailable or interrupted",
                    e,
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - ProximityVerificationException: ${e.message}" }
            ApiResult.InternalError(e)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Unexpected client error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Generic REST client error during bindKeys",
                    e,
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            )
        }
    }

    override suspend fun requestAccessCertificate(
        accessToken: String,
        csr: String
    ): ApiResult<AccessCertificateResponse> {
        val mn = "requestAccessCertificate"
        val requestBody = AccessCertificateRequest(csr)
        val serializedBody = Json.encodeToString(requestBody)

        Logger.debug(TAG) { "$mn - POST $baseUrl/access-certificate" }
        Logger.debug(TAG) { "$mn - Headers: Authorization=Bearer $accessToken, Content-Type=application/json" }
        Logger.debug(TAG) { "$mn - Body: $serializedBody" }

        return try {
            if (!isNetworkAvailable()) {
                throw ProximityVerificationException(
                    "Device is not online",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val response = client.post("$baseUrl/access-certificate") {
                timeout {
                    requestTimeoutMillis = 45000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 25000
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrNull()
            Logger.debug(TAG) { "$mn - Response Status: ${response.status}" }
            Logger.debug(TAG) { "$mn - Response Body: $responseBody" }

            if (response.status.isSuccess()) {
                val body = Json.decodeFromString<AccessCertificateResponse>(responseBody ?: "")
                return ApiResult.Success(body)
            }

            val errorResponse: ErrorResponse? = try {
                Json.decodeFromString<ErrorResponse>(responseBody ?: "")
            } catch (e: Exception) {
                Logger.warn(TAG) {
                    "$mn - Failed to parse error response JSON: ${e.message} - Raw body: $responseBody"
                }
                null
            }

            val errorCode = errorResponse?.error ?: "UNKNOWN_ERROR"
            val errorDescription =
                errorResponse?.errorDescription ?: "No error description provided"

            Logger.error(TAG) { "$mn - Backend error: [error=$errorCode, description=$errorDescription]" }

            return when (response.status) {
                HttpStatusCode.BadRequest -> restError(
                    HttpStatusCode.BadRequest,
                    ProximityVerificationExceptionCode.PVE17::default,
                    errorCode,
                    errorDescription
                )

                HttpStatusCode.Unauthorized -> restError(
                    HttpStatusCode.Unauthorized,
                    ProximityVerificationExceptionCode.PVE23::default,
                    errorCode,
                    errorDescription
                )

                HttpStatusCode.Forbidden -> restError(
                    HttpStatusCode.Forbidden,
                    ProximityVerificationExceptionCode.PVE18::default,
                    errorCode,
                    errorDescription
                )

                HttpStatusCode.InternalServerError -> restError(
                    HttpStatusCode.InternalServerError,
                    ProximityVerificationExceptionCode.PVE21::default,
                    errorCode,
                    errorDescription
                )

                HttpStatusCode.ServiceUnavailable -> restError(
                    HttpStatusCode.ServiceUnavailable,
                    ProximityVerificationExceptionCode.PVE22::default,
                    errorCode,
                    errorDescription
                )

                else -> restError(
                    response.status,
                    ProximityVerificationExceptionCode.PVE08::default,
                    errorCode,
                    errorDescription
                )
            }
        } catch (e: HttpRequestTimeoutException) {
            Logger.error(TAG) { "$mn - Timeout occurred: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Timeout during accessCertificate request",
                    e,
                    code = ProximityVerificationExceptionCode.PVE16.default()
                )
            )
        } catch (e: IOException) {
            Logger.error(TAG) { "$mn - Network I/O error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Network unavailable or interrupted",
                    e,
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - ProximityVerificationException: ${e.message}" }
            ApiResult.InternalError(e)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Unexpected client error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Generic REST client error during requestAccessCertificate",
                    e,
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            )
        }
    }

    override suspend fun fetchWellKnownConfiguration(wellKnownUrl: String): ApiResult<WellKnownConfiguration> {
        val mn = "fetchWellKnownConfiguration"

        Logger.debug(TAG) { "$mn - GET $wellKnownUrl" }

        return try {
            if (!isNetworkAvailable()) {
                throw ProximityVerificationException(
                    "Device is not online",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val response = client.get(wellKnownUrl) {
                timeout {
                    requestTimeoutMillis = 45000
                    connectTimeoutMillis = 15000
                    socketTimeoutMillis = 25000
                }
            }

            val responseBody = runCatching { response.bodyAsText() }.getOrNull()
            Logger.debug(TAG) { "$mn - Response Status: ${response.status}" }
            Logger.debug(TAG) { "$mn - Response Body: $responseBody" }

            return if (response.status.isSuccess()) {
                ApiResult.Success(WellKnownConfiguration(jwt = responseBody ?: ""))
            } else {
                val errorResponse: ErrorResponse? = try {
                    Json.decodeFromString<ErrorResponse>(responseBody ?: "")
                } catch (e: Exception) {
                    Logger.warn(TAG) {
                        "$mn - Failed to parse error response JSON: ${e.message} - Raw body: $responseBody"
                    }
                    null
                }

                val errorCode = errorResponse?.error ?: "UNKNOWN_ERROR"
                val errorDescription = errorResponse?.errorDescription ?: "No error description provided"

                Logger.error(TAG) { "$mn - Backend error: [error=$errorCode, description=$errorDescription]" }

                when (response.status) {
                    HttpStatusCode.InternalServerError -> restError(
                        HttpStatusCode.InternalServerError,
                        ProximityVerificationExceptionCode.PVE21::default,
                        errorCode,
                        errorDescription
                    )

                    HttpStatusCode.ServiceUnavailable -> restError(
                        HttpStatusCode.ServiceUnavailable,
                        ProximityVerificationExceptionCode.PVE22::default,
                        errorCode,
                        errorDescription
                    )

                    else -> restError(
                        response.status,
                        ProximityVerificationExceptionCode.PVE08::default,
                        errorCode,
                        errorDescription
                    )
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            Logger.error(TAG) { "$mn - Timeout occurred: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Timeout during fetchWellKnownConfiguration request",
                    e,
                    code = ProximityVerificationExceptionCode.PVE16.default()
                )
            )
        } catch (e: IOException) {
            Logger.error(TAG) { "$mn - Network I/O error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Network unavailable or interrupted",
                    e,
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - ProximityVerificationException: ${e.message}" }
            ApiResult.InternalError(e)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Unexpected client error: ${e.message}" }
            ApiResult.InternalError(
                ProximityVerificationException(
                    "Generic REST client error during fetchWellKnownConfiguration",
                    e,
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            )
        }
    }
}
