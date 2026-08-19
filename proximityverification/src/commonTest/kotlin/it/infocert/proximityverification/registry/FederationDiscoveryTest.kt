package it.infocert.proximityverification.registry

import it.infocert.proximityverification.registry.RegistryTestSupport.fakeJws
import it.infocert.proximityverification.registry.RegistryTestSupport.installNoOpLogger
import it.infocert.proximityverification.registry.RegistryTestSupport.mockClient
import it.infocert.proximityverification.registry.RegistryTestSupport.resetLogger
import it.infocert.proximityverification.registry.model.FederationEndpoints
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FederationDiscoveryTest {

    private val rpEntityUrl: String = "https://rp.example.it"
    private val taEntityUrl: String = "https://ta.example.it"
    private val resolveEndpoint: String = "https://ta.example.it/resolve"
    private val fetchEndpoint: String = "https://ta.example.it/fetch"

    @BeforeTest fun setUp() = installNoOpLogger()
    @AfterTest fun tearDown() = resetLogger()

    @Test
    fun discover_happyPath_extractsResolveEndpoint() = runTest {
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{"authority_hints":["$taEntityUrl"]}"""),
                contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{
                    "metadata":{
                      "federation_entity":{
                        "federation_resolve_endpoint":"$resolveEndpoint",
                        "federation_fetch_endpoint":"$fetchEndpoint"
                      }
                    }
                }"""),
                contentType = "application/entity-statement+jwt"
            )
        ))
        val discovery = FederationDiscovery(http = RegistryHttp(client = client))

        val endpoints: FederationEndpoints? = discovery.discover(rpEntityUrl)

        assertNotNull(endpoints)
        assertEquals(taEntityUrl, endpoints.trustAnchorUrl)
        assertEquals(resolveEndpoint, endpoints.resolveEndpoint)
        assertEquals(fetchEndpoint, endpoints.fetchEndpoint)
    }

    @Test
    fun discover_rpConfigUnreachable_returnsNull() = runTest {
        val client = mockClient(emptyMap())
        val discovery = FederationDiscovery(http = RegistryHttp(client = client))

        val endpoints: FederationEndpoints? = discovery.discover(rpEntityUrl)

        assertNull(endpoints)
    }

    @Test
    fun discover_missingAuthorityHints_returnsNull() = runTest {
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{"sub":"$rpEntityUrl"}"""),
                contentType = "application/entity-statement+jwt"
            )
        ))
        val discovery = FederationDiscovery(http = RegistryHttp(client = client))

        val endpoints: FederationEndpoints? = discovery.discover(rpEntityUrl)

        assertNull(endpoints)
    }

    @Test
    fun discover_taConfigMissingResolveEndpoint_returnsNull() = runTest {
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{"authority_hints":["$taEntityUrl"]}"""),
                contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{"metadata":{"federation_entity":{}}}"""),
                contentType = "application/entity-statement+jwt"
            )
        ))
        val discovery = FederationDiscovery(http = RegistryHttp(client = client))

        val endpoints: FederationEndpoints? = discovery.discover(rpEntityUrl)

        assertNull(endpoints)
    }

    @Test
    fun discover_trailingSlashIsNormalised() = runTest {
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{"authority_hints":["$taEntityUrl"]}"""),
                contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = fakeJws("""{
                    "metadata":{
                      "federation_entity":{
                        "federation_resolve_endpoint":"$resolveEndpoint"
                      }
                    }
                }"""),
                contentType = "application/entity-statement+jwt"
            )
        ))
        val discovery = FederationDiscovery(http = RegistryHttp(client = client))

        val endpoints = discovery.discover("$rpEntityUrl/")

        assertNotNull(endpoints)
        assertEquals(resolveEndpoint, endpoints.resolveEndpoint)
        assertNull(endpoints.fetchEndpoint)
    }
}
