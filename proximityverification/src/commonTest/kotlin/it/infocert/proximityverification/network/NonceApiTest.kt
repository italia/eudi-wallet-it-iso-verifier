package it.infocert.proximityverification.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import it.infocert.proximityverification.TestUtils.BASE_URL
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.network.model.ApiResult
import it.infocert.proximityverification.network.model.common.NonceResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.*

class NonceApiTest {
    private val noOpLoggerDelegate: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    @BeforeTest
    fun setUpLogger(): Unit {
        LoggerConfig.delegateLogger = noOpLoggerDelegate
    }

    @AfterTest
    fun tearDownLogger(): Unit {
        LoggerConfig.delegateLogger = null
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient {
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

    @Test
    fun testFetchNonceSuccess() = runTest {
        val expectedNonce = "abc123xyz"
        val client = mockClient {
            respond(
                content = ByteReadChannel("""{"nonce":"$expectedNonce"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = RelyingPartyApiImpl(BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }

        val result = api.fetchNonce()
        assertIs<ApiResult.Success<NonceResponse>>(result)
        assertEquals(expectedNonce, result.data.nonce)
    }

    @Test
    fun testFetchNonceServiceUnavailable() = runTest {
        val client = mockClient {
            respond(
                content = ByteReadChannel(
                    """{
                      "error": "temporarily_unavailable",
                      "error_description": "The request cannot be fulfilled because the Nonce Endpoint is temporarily unavailable."
                    }"""
                ),
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = RelyingPartyApiImpl(BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.fetchNonce()
        assertIs<ApiResult.InternalError>(result)
        assertTrue(result.exception.message.contains("temporarily_unavailable"))
    }

    @Test
    fun testFetchNonceServerError() = runTest {
        val client = mockClient {
            respond(
                content = ByteReadChannel(
                    """{
                      "error": "server_error",
                      "error_description": "The request cannot be fulfilled because the Nonce Endpoint encountered an internal problem."
                    }"""
                ),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = RelyingPartyApiImpl(BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.fetchNonce()
        assertIs<ApiResult.InternalError>(result)
        assertTrue(result.exception.message.contains("server_error"))
    }

    @Test
    fun testFetchNonceException() = runTest {
        val engine = MockEngine {
            throw RuntimeException("boom")
        }

        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout)
        }

        val api = RelyingPartyApiImpl(BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }

        val result = api.fetchNonce()
        assertIs<ApiResult.InternalError>(result)
        assertEquals("boom", result.exception.cause?.message)
    }
}