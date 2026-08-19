package it.infocert.proximityverification.registry

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import it.infocert.proximityverification.core.crypto.LocalStorage
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.serialization.json.Json
import org.multipaz.util.toBase64Url

/**
 * Utilities shared by the federation registry resolver tests:
 * - Logger silencing (BeforeTest/AfterTest helpers)
 * - Ktor [MockEngine] builders that respond per URL
 * - In-memory [LocalStorage]
 * - Compact JWS builder that synthesises a `header.payload.signature` triple
 *   with an arbitrary signature segment (the resolver does not verify it).
 */
internal object RegistryTestSupport {

    val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    fun installNoOpLogger(): Unit { LoggerConfig.delegateLogger = noOpLogger }
    fun resetLogger(): Unit { LoggerConfig.delegateLogger = null }

    /**
     * Builds an [HttpClient] backed by a [MockEngine] that dispatches by URL prefix.
     * If no entry matches, responds 404 with an empty body.
     */
    fun mockClient(routes: Map<String, MockResponse>): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request -> resolveRoute(request, routes) }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout)
        }
    }

    /**
     * Builds an [HttpClient] backed by a [MockEngine] driven by a free-form handler.
     */
    fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler(handler)
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout)
        }
    }

    private fun MockRequestHandleScope.resolveRoute(
        request: HttpRequestData,
        routes: Map<String, MockResponse>
    ): HttpResponseData {
        val url: String = request.url.toString()
        for ((prefix, response) in routes) {
            if (url.startsWith(prefix)) {
                return respond(
                    content = ByteReadChannel(response.body),
                    status = response.status,
                    headers = headersOf(HttpHeaders.ContentType, response.contentType)
                )
            }
        }
        return respond(
            content = ByteReadChannel(""),
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "text/plain")
        )
    }

    /**
     * Synthesises a compact JWS with arbitrary header/payload/signature segments.
     * The `signature` is filler bytes; resolver code does not verify it.
     */
    fun fakeJws(payload: String, header: String = """{"alg":"none"}"""): String {
        val h: String = header.encodeToByteArray().toBase64Url()
        val p: String = payload.encodeToByteArray().toBase64Url()
        val s: String = "deadbeef".encodeToByteArray().toBase64Url()
        return "$h.$p.$s"
    }

    /**
     * In-memory [LocalStorage] for tests.
     */
    class InMemoryLocalStorage : LocalStorage {
        private val map: MutableMap<String, String> = mutableMapOf()
        override suspend fun saveElement(key: String, value: String) { map[key] = value }
        override suspend fun getElement(key: String): String? = map[key]
        override suspend fun clear() { map.clear() }
        override suspend fun clearElement(logicalKey: String) { map.remove(logicalKey) }
        fun snapshot(): Map<String, String> = map.toMap()
    }
}

internal data class MockResponse(
    val body: String,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val contentType: String = "application/json"
)
