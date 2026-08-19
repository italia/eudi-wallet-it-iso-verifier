package it.infocert.proximityverification.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import it.infocert.proximityverification.TestUtils
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.network.model.ApiResult
import it.infocert.proximityverification.network.model.common.KeyBindingResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.*

class KeyBindingApiTest {
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
    fun testBindKeysSuccess() = runTest {
        val client = mockClient {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = RelyingPartyApiImpl(TestUtils.BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.bindKeys("exampleToken", TestUtils.signedJwtStub())
        assertIs<ApiResult.Success<KeyBindingResponse>>(result)
    }

    @Test
    fun testBindKeysForbidden() = runTest {
        val client = mockClient {
            respond(
                content = ByteReadChannel(TestUtils.errorJson("invalid_request", "Invalid or used nonce")),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = RelyingPartyApiImpl(TestUtils.BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.bindKeys("exampleToken", TestUtils.signedJwtStub())
        assertIs<ApiResult.InternalError>(result)
        assertTrue(result.exception.message.contains("invalid_request"))
    }

    @Test
    fun testBindKeysException() = runTest {
        val engine = MockEngine {
            throw RuntimeException("network failure")
        }

        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout)
        }

        val api = RelyingPartyApiImpl(TestUtils.BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }

        val result = api.bindKeys("exampleToken", TestUtils.signedJwtStub())
        assertIs<ApiResult.InternalError>(result)
        assertEquals("network failure", result.exception.cause?.message)
    }
}