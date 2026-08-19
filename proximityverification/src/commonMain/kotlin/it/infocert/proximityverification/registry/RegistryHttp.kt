package it.infocert.proximityverification.registry

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.HttpClientProvider
import it.infocert.proximityverification.network.logHttpErrorResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.multipaz.util.fromBase64Url

/**
 * HTTP and JWT helpers shared by the registry resolver components.
 *
 * All calls use the aggressive 5-second per-call timeout dictated by the
 * federation registry resolver design (offline-friendly, fail-fast).
 *
 * The default constructor uses the SDK shared [HttpClientProvider]; tests
 * inject a mocked [HttpClient].
 */
internal class RegistryHttp(
    private val client: HttpClient = HttpClientProvider.client,
    private val callTimeoutMillis: Long = CALL_TIMEOUT_MILLIS
) {

    private val tag: String = "RegistryHttp"

    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Performs a GET on [url] with the resolver per-call timeout and the optional
     * `Accept` header. Returns the response body as text or `null` on any failure
     * (logged at WARN level). Cancellation always propagates.
     *
     * On non-success HTTP status, the body is inspected for the OpenID Federation
     * §8.9 error format (`error` + `error_description`) and logged for diagnostics.
     * The empty-string body case is collapsed to `null` for caller convenience.
     */
    suspend fun getOrNull(
        url: String,
        accept: ContentType? = null,
        queryParams: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val response: HttpResponse = client.get(url) {
                timeout {
                    requestTimeoutMillis = callTimeoutMillis
                    connectTimeoutMillis = callTimeoutMillis
                    socketTimeoutMillis = callTimeoutMillis
                }
                accept?.let { accept(it) }
                queryParams.forEach { (key, value) -> parameter(key, value) }
            }
            if (!response.status.isSuccess()) {
                logHttpErrorResponse(tag, url, response)
                return null
            }
            response.bodyAsText().ifBlank { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Logger.warn(tag) { "GET $url → timeout (${callTimeoutMillis} ms)" }
            null
        } catch (e: Throwable) {
            Logger.warn(tag) { "GET $url → ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    /**
     * Performs a GET expecting either JSON or JWT (compact JWS) and returns the
     * decoded JSON payload. Selection is driven by `Content-Type` first and a
     * fallback heuristic on the body shape (`{` → JSON, otherwise JWS).
     *
     * On non-success HTTP status, the body is inspected for the OpenID Federation
     * §8.9 error format and logged. Returns `null` on any failure.
     * Cancellation propagates.
     */
    suspend fun getJsonOrJwsPayload(url: String): JsonObject? {
        return try {
            val response: HttpResponse = client.get(url) {
                timeout {
                    requestTimeoutMillis = callTimeoutMillis
                    connectTimeoutMillis = callTimeoutMillis
                    socketTimeoutMillis = callTimeoutMillis
                }
                accept(ContentType.Application.Json)
                accept(ContentType("application", "jwt"))
                accept(ContentType("application", "entity-statement+jwt"))
                accept(ContentType("application", "jose"))
                accept(ContentType("application", "resolve-response+jwt"))
            }
            if (!response.status.isSuccess()) {
                logHttpErrorResponse(tag, url, response)
                return null
            }
            val body: String = response.bodyAsText()
            if (body.isBlank()) return null
            val contentType: ContentType? = response.responseContentType()
            val isJwt: Boolean = when {
                contentType == null -> body.trimStart().firstOrNull() != '{'
                contentType.match(ContentType.Application.Json) -> false
                contentType.contentSubtype.contains("json", ignoreCase = true) -> false
                contentType.contentSubtype.contains("jwt", ignoreCase = true) -> true
                contentType.contentSubtype.contains("jose", ignoreCase = true) -> true
                else -> body.trimStart().firstOrNull() != '{'
            }
            if (isJwt) decodeJwsPayload(body) else parseJsonObject(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Logger.warn(tag) { "GET $url → timeout (${callTimeoutMillis} ms)" }
            null
        } catch (e: Throwable) {
            Logger.warn(tag) { "GET $url → ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    /**
     * Decodes the payload of a compact JWS without verifying the signature.
     *
     * Phase 1 does not validate entity statement signatures (the federation
     * trust chain is not yet in scope for the SDK); we extract the claims
     * payload only. Returns `null` if [jws] is malformed.
     */
    fun decodeJwsPayload(jws: String): JsonObject? {
        val parts: List<String> = jws.trim().split(".")
        if (parts.size != 3) {
            Logger.warn(tag) { "JWS parse failed: expected 3 segments, got ${parts.size}" }
            return null
        }
        return try {
            val payloadBytes: ByteArray = parts[1].fromBase64Url()
            parseJsonObject(payloadBytes.decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "JWS payload decode failed: ${e.message}" }
            null
        }
    }

    /** Parses [text] as a JSON object, returning `null` on any error. */
    fun parseJsonObject(text: String): JsonObject? {
        return try {
            json.decodeFromString(JsonObject.serializer(), text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "JSON parse failed: ${e.message}" }
            null
        }
    }

    private fun HttpResponse.responseContentType(): ContentType? {
        val raw: String = headers[HttpHeaders.ContentType] ?: return null
        return try { ContentType.parse(raw) } catch (_: Throwable) { null }
    }

    companion object {
        /** Per-call timeout enforced by the resolver (design §3, risk 2). */
        const val CALL_TIMEOUT_MILLIS: Long = 5_000L

        /**
         * Joins a base entity URL with a well-known path, normalising the trailing slash.
         */
        fun appendWellKnown(baseUrl: String, wellKnownPath: String): String {
            val base: String = baseUrl.trimEnd('/')
            val path: String = wellKnownPath.removePrefix("/")
            return "$base/$path"
        }

        /**
         * Builds the federation entity configuration URL from a base entity URL.
         *
         * Delegates to the top-level
         * [it.infocert.proximityverification.federationEntityConfigUrl] helper so
         * the existing OID4VCI well-known fetch and the new federation registry
         * resolver derive the URL identically.
         */
        fun federationEntityConfigUrl(baseEntityUrl: String): String =
            it.infocert.proximityverification.federationEntityConfigUrl(baseEntityUrl)

        /**
         * Builds the IT-Wallet registry discovery URL from the Trust Anchor base URL.
         */
        fun walletRegistryDiscoveryUrl(taBaseUrl: String): String =
            appendWellKnown(taBaseUrl, ".well-known/it-wallet-registry")
    }
}
