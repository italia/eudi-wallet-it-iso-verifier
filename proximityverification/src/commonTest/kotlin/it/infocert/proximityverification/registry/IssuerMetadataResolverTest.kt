package it.infocert.proximityverification.registry

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import it.infocert.proximityverification.registry.RegistryTestSupport.fakeJws
import it.infocert.proximityverification.registry.RegistryTestSupport.installNoOpLogger
import it.infocert.proximityverification.registry.RegistryTestSupport.mockClient
import it.infocert.proximityverification.registry.RegistryTestSupport.resetLogger
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuerMetadataResolverTest {

    private val resolveEndpoint: String = "https://ta.example.it/resolve"
    private val taUrl: String = "https://ta.example.it"
    private val issuerId: String = "https://issuer.example.it"
    private val statusListAggregationUrl: String = "https://issuer.example.it/status-list-aggregation"

    @BeforeTest fun setUp() = installNoOpLogger()
    @AfterTest fun tearDown() = resetLogger()

    private fun resolveResponse(body: String, contentType: String = "application/json"): MockResponse =
        MockResponse(body = body, contentType = contentType)

    @Test
    fun resolveIssuer_happyPath_extractsCredentialAndStatusListEndpoint() = runTest {
        val payload = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "PID-mso-mdoc":{
                    "format":"mso_mdoc",
                    "doctype":"eu.europa.ec.eudi.pid.1",
                    "credential_metadata":{
                      "display":[{"name":"Personal ID","locale":"en"}],
                      "claims":[
                        {"path":["eu.europa.ec.eudi.pid.1","family_name"],"mandatory":true,
                          "display":[{"name":"Family Name","locale":"en"}]}
                      ]
                    }
                  }
                },
                "status_list_aggregation_endpoint":"$statusListAggregationUrl"
              }
            }
        }"""
        val client = mockClient(mapOf(resolveEndpoint to resolveResponse(payload)))
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        val metadata: IssuerResolvedMetadata? = resolver.resolveIssuer(
            resolveEndpoint = resolveEndpoint,
            issuerEntityId = issuerId,
            trustAnchorUrl = taUrl
        )

        assertNotNull(metadata)
        assertEquals(issuerId, metadata.entityId)
        assertEquals(statusListAggregationUrl, metadata.statusListAggregationEndpoint)
        assertEquals(1, metadata.credentialConfigurations.size)
        assertEquals("eu.europa.ec.eudi.pid.1", metadata.credentialConfigurations[0].docType)
        assertEquals(1, metadata.credentialConfigurations[0].claims.size)
    }

    @Test
    fun resolveIssuer_missingOpenidCredentialIssuer_returnsNull() = runTest {
        val client = mockClient(mapOf(
            resolveEndpoint to resolveResponse("""{"metadata":{}}""")
        ))
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        assertNull(resolver.resolveIssuer(resolveEndpoint, issuerId, taUrl))
    }

    @Test
    fun resolveIssuer_noMsoMdocConfig_returnsNull() = runTest {
        val payload = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "PID-sd-jwt":{"format":"sd-jwt-vc","doctype":"eu.europa.ec.eudi.pid.1"}
                }
              }
            }
        }"""
        val client = mockClient(mapOf(resolveEndpoint to resolveResponse(payload)))
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        assertNull(resolver.resolveIssuer(resolveEndpoint, issuerId, taUrl))
    }

    @Test
    fun resolveIssuer_unreachable_returnsNull() = runTest {
        val client = mockClient(emptyMap())
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        assertNull(resolver.resolveIssuer(resolveEndpoint, issuerId, taUrl))
    }

    @Test
    fun resolveIssuer_firstRequestIncludesOpenidCredentialIssuerEntityType() = runTest {
        var capturedUrl: String? = null
        val payload = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "mDL-mso-mdoc":{
                    "format":"mso_mdoc",
                    "doctype":"org.iso.18013.5.1.mDL",
                    "credential_metadata":{"display":[],"claims":[]}
                  }
                }
              }
            }
        }"""
        val client = RegistryTestSupport.mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                content = ByteReadChannel(fakeJws(payload)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/resolve-response+jwt")
            )
        }
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        resolver.resolveIssuer(resolveEndpoint, issuerId, taUrl)

        assertNotNull(capturedUrl)
        assertTrue(capturedUrl.contains("entity_type=openid_credential_issuer"))
    }

    @Test
    fun resolveIssuer_fallsBackWithoutEntityTypeOnFirstFailure() = runTest {
        val capturedUrls: MutableList<String> = mutableListOf()
        val payload = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "mDL-mso-mdoc":{
                    "format":"mso_mdoc",
                    "doctype":"org.iso.18013.5.1.mDL",
                    "credential_metadata":{"display":[],"claims":[]}
                  }
                }
              }
            }
        }"""
        val client = RegistryTestSupport.mockClient { request ->
            val url: String = request.url.toString()
            capturedUrls += url
            if (url.contains("entity_type=openid_credential_issuer")) {
                respond(
                    content = ByteReadChannel("""{"error":"unsupported_parameter"}"""),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = ByteReadChannel(fakeJws(payload)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/resolve-response+jwt")
                )
            }
        }
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        val metadata = resolver.resolveIssuer(resolveEndpoint, issuerId, taUrl)

        assertNotNull(metadata)
        assertEquals(2, capturedUrls.size)
        assertTrue(capturedUrls[0].contains("entity_type=openid_credential_issuer"))
        assertTrue(!capturedUrls[1].contains("entity_type="))
    }

    @Test
    fun resolveIssuer_buildsUrlWithSubAndAnchor() = runTest {
        var capturedUrl: String? = null
        val payload = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "PID-mso-mdoc":{
                    "format":"mso_mdoc",
                    "doctype":"eu.europa.ec.eudi.pid.1",
                    "credential_metadata":{"display":[],"claims":[]}
                  }
                }
              }
            }
        }"""
        val client = RegistryTestSupport.mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val resolver = IssuerMetadataResolver(http = RegistryHttp(client = client))

        resolver.resolveIssuer(resolveEndpoint, issuerId, taUrl)

        assertNotNull(capturedUrl)
        assertTrue(capturedUrl.contains("sub=https%3A%2F%2Fissuer.example.it"))
        assertTrue(capturedUrl.contains("trust_anchor=https%3A%2F%2Fta.example.it"))
    }
}
