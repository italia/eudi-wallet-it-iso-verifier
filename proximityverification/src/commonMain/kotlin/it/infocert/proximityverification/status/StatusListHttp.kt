package it.infocert.proximityverification.status

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.HttpClientProvider
import it.infocert.proximityverification.network.logHttpErrorResponse
import it.infocert.proximityverification.status.model.StatusListAggregationResponse
import it.infocert.proximityverification.status.model.StatusListPayload
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Discriminated outcome of a Token Status List HTTP call.
 *
 * Distinguishes "the endpoint could not be contacted at all" ([Unreachable])
 * from "the endpoint was reached but answered with a non-success HTTP status"
 * ([HttpError]) and "the endpoint answered 2xx but the body was unusable"
 * ([Malformed]). The prefetcher maps these to the corresponding
 * `FailureReason` values in the public summary.
 */
internal sealed class FetchResult<out T> {
    data class Success<T>(val value: T) : FetchResult<T>()
    /** Transport-level failure: DNS / connection refused / timeout / I/O. */
    object Unreachable : FetchResult<Nothing>()
    /** Endpoint reached, but responded with a non-success HTTP status (4xx / 5xx). */
    data class HttpError(val statusCode: Int) : FetchResult<Nothing>()
    /** 2xx response, but the body could not be decoded as the expected payload. */
    object Malformed : FetchResult<Nothing>()
}

/**
 * Ktor wrapper for the Token Status List flow:
 * - aggregation endpoint (JSON, draft-13 §9)
 * - status list payload (raw bytes, draft-13 §6, JWT or CWT)
 *
 * Intentionally separate from `RegistryHttp`: the registry chain has different
 * content-negotiation rules and a JWS-payload helper that does not apply here.
 *
 * Per-call timeout aligns with `RegistryHttp.CALL_TIMEOUT_MILLIS` (5s, fail-fast).
 */
internal open class StatusListHttp(
    private val client: HttpClient = HttpClientProvider.client,
    private val rawClient: HttpClient = HttpClientProvider.rawClient,
    private val callTimeoutMillis: Long = CALL_TIMEOUT_MILLIS
) {
    private val tag: String = "StatusListHttp"
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    open suspend fun fetchAggregation(uri: String): FetchResult<StatusListAggregationResponse> {
        return try {
            val response: HttpResponse = client.get(uri) {
                timeout {
                    requestTimeoutMillis = callTimeoutMillis
                    connectTimeoutMillis = callTimeoutMillis
                    socketTimeoutMillis = callTimeoutMillis
                }
            }
            if (!response.status.isSuccess()) {
                logHttpErrorResponse(tag, uri, response)
                return FetchResult.HttpError(response.status.value)
            }
            val body: String = response.bodyAsText()
            if (body.isBlank()) {
                Logger.warn(tag) { "GET $uri → 2xx with empty body" }
                return FetchResult.Malformed
            }
            try {
                FetchResult.Success(json.decodeFromString(StatusListAggregationResponse.serializer(), body))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(tag) { "GET $uri → 2xx but body not parseable: ${e::class.simpleName}: ${e.message}" }
                FetchResult.Malformed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Logger.warn(tag) { "GET $uri → timeout (${callTimeoutMillis} ms)" }
            FetchResult.Unreachable
        } catch (e: Throwable) {
            Logger.warn(tag) { "GET $uri → ${e::class.simpleName}: ${e.message}" }
            FetchResult.Unreachable
        }
    }

    open suspend fun fetchStatusListPayload(uri: String): FetchResult<StatusListPayload> {
        return try {
            // Use the raw client (no ContentNegotiation) so we don't drag along
            // an `Accept: application/json` injected by the JSON converter:
            // status list endpoints negotiate on these media types
            // (Token Status List draft-13 §10) and reject JSON with 404.
            val response: HttpResponse = rawClient.get(uri) {
                timeout {
                    requestTimeoutMillis = callTimeoutMillis
                    connectTimeoutMillis = callTimeoutMillis
                    socketTimeoutMillis = callTimeoutMillis
                }
                accept(ContentType("application", "statuslist+jwt"))
                accept(ContentType("application", "statuslist+cwt"))
            }
            if (!response.status.isSuccess()) {
                logHttpErrorResponse(tag, uri, response)
                return FetchResult.HttpError(response.status.value)
            }
            val bytes: ByteArray = response.bodyAsBytes()
            val contentType: String? = response.headers[HttpHeaders.ContentType]
            FetchResult.Success(StatusListPayload(bytes = bytes, contentType = contentType))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Logger.warn(tag) { "GET $uri → timeout (${callTimeoutMillis} ms)" }
            FetchResult.Unreachable
        } catch (e: Throwable) {
            Logger.warn(tag) { "GET $uri → ${e::class.simpleName}: ${e.message}" }
            FetchResult.Unreachable
        }
    }

    companion object {
        const val CALL_TIMEOUT_MILLIS: Long = 5_000L
    }
}
