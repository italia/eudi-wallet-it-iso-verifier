package it.infocert.proximityverification.crl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.HttpClientProvider
import kotlinx.coroutines.CancellationException

/**
 * Discriminated outcome of a CRL HTTP call.
 *
 * Distinguishes "the endpoint could not be contacted at all" ([Unreachable])
 * from "the endpoint was reached but answered with a non-success HTTP status"
 * ([HttpError]) and "the endpoint answered 2xx but the body was empty"
 * ([Malformed]). The `CrlPrefetcher` maps these to the corresponding
 * `CrlFailureReason` values in the public summary.
 *
 * Note: positive parse-time validation of the bytes as a real `X509Crl`
 * happens in `CrlPrefetcher` (the HTTP layer cannot tell "valid CRL" from
 * "valid PKIX-CRL-shaped bytes" without parsing). HTTP-layer [Malformed]
 * is therefore reserved for empty-body 2xx responses; bytes that fail to
 * parse upstream produce `CRL_MALFORMED_PAYLOAD` from the prefetcher.
 */
internal sealed interface CrlFetchResult {
    /** 2xx response with non-empty body. */
    data class Success(val bytes: ByteArray) : CrlFetchResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Transport-level failure: DNS / connection refused / timeout / I/O. */
    data object Unreachable : CrlFetchResult

    /** Endpoint reached, but responded with a non-success HTTP status (4xx / 5xx). */
    data class HttpError(val statusCode: Int) : CrlFetchResult

    /** 2xx response, but the body was empty. */
    data object Malformed : CrlFetchResult
}

/**
 * Contract for downloading CRL DER bytes from a single URI. Existing for
 * dependency-injection seam in `CrlPrefetcher` tests.
 */
internal interface CrlHttpFetcher {
    suspend fun fetch(uri: String): CrlFetchResult
}

/**
 * Default ktor-backed implementation. Reuses [HttpClientProvider.rawClient]
 * (no `Accept: application/json` injection — CRL endpoints serve
 * `application/pkix-crl`) and enforces a fail-fast 5 s call timeout
 * (configurable via [callTimeoutMillis] for tests / consumer-tuned
 * deployments where the default is too tight for a national CRL).
 */
internal class DefaultCrlHttpFetcher(
    private val client: HttpClient = HttpClientProvider.rawClient,
    private val callTimeoutMillis: Long = CALL_TIMEOUT_MILLIS
) : CrlHttpFetcher {
    private val tag: String = "DefaultCrlHttpFetcher"

    override suspend fun fetch(uri: String): CrlFetchResult {
        return try {
            val response: HttpResponse = client.get(uri) {
                timeout {
                    requestTimeoutMillis = callTimeoutMillis
                    connectTimeoutMillis = callTimeoutMillis
                    socketTimeoutMillis = callTimeoutMillis
                }
            }
            if (!response.status.isSuccess()) {
                Logger.warn(tag) { "GET $uri → HTTP ${response.status.value}" }
                return CrlFetchResult.HttpError(response.status.value)
            }
            val bytes: ByteArray = response.bodyAsBytes()
            if (bytes.isEmpty()) {
                Logger.warn(tag) { "GET $uri → 2xx with empty body" }
                return CrlFetchResult.Malformed
            }
            CrlFetchResult.Success(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Logger.warn(tag) { "GET $uri → timeout (${callTimeoutMillis} ms)" }
            CrlFetchResult.Unreachable
        } catch (e: Throwable) {
            Logger.warn(tag) { "GET $uri → ${e::class.simpleName}: ${e.message}" }
            CrlFetchResult.Unreachable
        }
    }

    companion object {
        const val CALL_TIMEOUT_MILLIS: Long = 5_000L
    }
}
