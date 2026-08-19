package it.infocert.proximityverification.status

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StatusListHttpTest {

    private class CapturingLogger : LoggerDelegate {
        val messages: MutableList<Pair<LogLevel, String>> = mutableListOf()
        override fun log(level: LogLevel, tag: String, message: String) {
            messages += level to message
        }
    }

    private val capturingLogger = CapturingLogger()

    @BeforeTest fun setUp() {
        capturingLogger.messages.clear()
        LoggerConfig.delegateLogger = capturingLogger
    }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    private fun mockClient(handler: MockEngineConfig.() -> Unit): HttpClient {
        return HttpClient(MockEngine) {
            engine { handler() }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
    }

    /** Mirrors HttpClientProvider.rawClient: HttpTimeout only, no ContentNegotiation. */
    private fun mockRawClient(handler: MockEngineConfig.() -> Unit): HttpClient {
        return HttpClient(MockEngine) {
            engine { handler() }
            install(HttpTimeout)
        }
    }

    /** Sentinel client whose endpoints must not be hit by the test under exam. */
    private fun unusedClient(): HttpClient = mockClient {
        addHandler { request -> error("unexpected call to ${request.url}") }
    }

    @Test
    fun fetchAggregation_parsesStatusListsArray() = runTest {
        val client = mockClient {
            addHandler {
                respond(
                    content = ByteReadChannel("""{"status_lists":["https://a/0","https://a/1"]}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val http = StatusListHttp(client = client)

        val result = http.fetchAggregation("https://issuer/aggregation")

        val success = assertIs<FetchResult.Success<*>>(result)
        assertEquals(
            listOf("https://a/0", "https://a/1"),
            (success.value as it.infocert.proximityverification.status.model.StatusListAggregationResponse).statusLists
        )
    }

    @Test
    fun fetchAggregation_returnsHttpErrorOnNonSuccess() = runTest {
        val client = mockClient {
            addHandler {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
            }
        }
        val http = StatusListHttp(client = client)

        val result = http.fetchAggregation("https://issuer/aggregation")
        val httpError = assertIs<FetchResult.HttpError>(result)
        assertEquals(500, httpError.statusCode)
    }

    @Test
    fun fetchAggregation_returnsMalformedOn2xxWithBrokenBody() = runTest {
        val client = mockClient {
            addHandler {
                respond(
                    content = ByteReadChannel("not-json-at-all"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val http = StatusListHttp(client = client)

        val result = http.fetchAggregation("https://issuer/aggregation")
        assertIs<FetchResult.Malformed>(result)
    }

    @Test
    fun fetchAggregation_returnsMalformedOn2xxWithEmptyBody() = runTest {
        val client = mockClient {
            addHandler {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
            }
        }
        val http = StatusListHttp(client = client)

        val result = http.fetchAggregation("https://issuer/aggregation")
        assertIs<FetchResult.Malformed>(result)
    }

    @Test
    fun fetchStatusListPayload_returnsBytesAndContentType() = runTest {
        val rawClient = mockRawClient {
            addHandler {
                respond(
                    content = ByteReadChannel(byteArrayOf(0x01, 0x02, 0x03)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/statuslist+cwt")
                )
            }
        }
        val http = StatusListHttp(client = unusedClient(), rawClient = rawClient)

        val result = http.fetchStatusListPayload("https://issuer/sl/0")
        val success = assertIs<FetchResult.Success<*>>(result)
        val payload = success.value as it.infocert.proximityverification.status.model.StatusListPayload
        assertEquals(byteArrayOf(0x01, 0x02, 0x03).toList(), payload.bytes.toList())
        assertEquals("application/statuslist+cwt", payload.contentType)
    }

    @Test
    fun fetchStatusListPayload_sendsStatusListAcceptWithoutJsonFromPlugin() = runTest {
        var capturedAcceptValues: List<String>? = null
        val rawClient = mockRawClient {
            addHandler { request ->
                capturedAcceptValues = request.headers.getAll(HttpHeaders.Accept)
                respond(
                    content = ByteReadChannel(byteArrayOf(0x01)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt")
                )
            }
        }
        val http = StatusListHttp(client = unusedClient(), rawClient = rawClient)

        http.fetchStatusListPayload("https://issuer/sl/0")

        val accepts = checkNotNull(capturedAcceptValues) { "Accept header not sent" }
        val joined = accepts.joinToString(", ")
        assertTrue(
            joined.contains("application/statuslist+jwt"),
            "Accept must advertise statuslist+jwt to avoid issuer 404 from JSON-only negotiation: $joined"
        )
        assertTrue(
            joined.contains("application/statuslist+cwt"),
            "Accept must advertise statuslist+cwt: $joined"
        )
        assertTrue(
            !joined.contains("application/json"),
            "Accept must not include application/json (would trigger 404 on negotiating issuers): $joined"
        )
    }

    @Test
    fun fetchStatusListPayload_returnsHttpErrorOnNonSuccess() = runTest {
        val rawClient = mockRawClient {
            addHandler {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
            }
        }
        val http = StatusListHttp(client = unusedClient(), rawClient = rawClient)

        val result = http.fetchStatusListPayload("https://issuer/sl/0")
        val httpError = assertIs<FetchResult.HttpError>(result)
        assertEquals(404, httpError.statusCode)
    }

    @Test
    fun fetchAggregation_logsOAuthErrorEnvelopeOnHttpError() = runTest {
        val client = mockClient {
            addHandler {
                respond(
                    content = ByteReadChannel(
                        """{"error":"invalid_request","error_description":"Parametro sub mancante"}"""
                    ),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val http = StatusListHttp(client = client)

        val result = http.fetchAggregation("https://issuer/aggregation")
        val httpError = assertIs<FetchResult.HttpError>(result)
        assertEquals(400, httpError.statusCode)
        val logged: String = capturingLogger.messages.joinToString("\n") { it.second }
        assertTrue(logged.contains("HTTP 400"), "expected HTTP 400 in log: $logged")
        assertTrue(logged.contains("invalid_request"), "expected error code in log: $logged")
        assertTrue(logged.contains("Parametro sub mancante"), "expected error_description in log: $logged")
    }

    @Test
    fun fetchStatusListPayload_logsOAuthErrorEnvelopeOnHttpError() = runTest {
        val rawClient = mockRawClient {
            addHandler {
                respond(
                    content = ByteReadChannel(
                        """{"error":"server_error","error_description":"Errore generico"}"""
                    ),
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val http = StatusListHttp(client = unusedClient(), rawClient = rawClient)

        val result = http.fetchStatusListPayload("https://issuer/sl/0")
        val httpError = assertIs<FetchResult.HttpError>(result)
        assertEquals(500, httpError.statusCode)
        val logged: String = capturingLogger.messages.joinToString("\n") { it.second }
        assertTrue(logged.contains("HTTP 500"), "expected HTTP 500 in log: $logged")
        assertTrue(logged.contains("server_error"), "expected error code in log: $logged")
        assertTrue(logged.contains("Errore generico"), "expected error_description in log: $logged")
    }

    @Test
    fun fetchStatusListPayload_logsBareStatusWhenBodyHasNoEnvelope() = runTest {
        val rawClient = mockRawClient {
            addHandler {
                respond(
                    content = ByteReadChannel("<html>oops</html>"),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            }
        }
        val http = StatusListHttp(client = unusedClient(), rawClient = rawClient)

        val result = http.fetchStatusListPayload("https://issuer/sl/0")
        assertIs<FetchResult.HttpError>(result)
        val logged: String = capturingLogger.messages.joinToString("\n") { it.second }
        assertTrue(logged.contains("HTTP 404"), "expected HTTP 404 in log: $logged")
    }
}
