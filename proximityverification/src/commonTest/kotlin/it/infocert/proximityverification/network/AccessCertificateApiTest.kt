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
import it.infocert.proximityverification.network.model.common.AccessCertificateResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.*

class AccessCertificateApiTest {
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
    fun testRequestAccessCertificateSuccess() = runTest {
        val responseJson = """{ "access_certificate": "CERT123XYZ" }"""

        val client = mockClient {
            respond(
                content = ByteReadChannel(responseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val api = RelyingPartyApiImpl(TestUtils.BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.requestAccessCertificate("exampleToken", "MIIBvzCCAa...")
        assertIs<ApiResult.Success<AccessCertificateResponse>>(result)
        assertEquals("CERT123XYZ", result.data.access_certificate)
    }

    @Test
    fun testRequestAccessCertificateError() = runTest {
        val client = mockClient {
            respond(
                content = ByteReadChannel(TestUtils.errorJson("invalid_request", "CSR mismatch")),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val api = RelyingPartyApiImpl(TestUtils.BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.requestAccessCertificate("exampleToken", "MIIBvzCCAa...")
        assertIs<ApiResult.InternalError>(result)
        assertTrue(result.exception.message.contains("invalid_request"))
    }

    @Test
    fun testRequestAccessCertificateException() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    throw RuntimeException("boom")
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout)
        }

        val api = RelyingPartyApiImpl(TestUtils.BASE_URL, isNetworkAvailable = { true }).also {
            it.overrideClient(client)
        }
        val result = api.requestAccessCertificate("exampleToken", "MIIBvzCCAa...")
        assertIs<ApiResult.InternalError>(result)
        assertEquals("boom", result.exception.cause?.message)
    }
}