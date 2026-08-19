package it.infocert.proximityverification.registry

import it.infocert.proximityverification.registry.RegistryTestSupport.fakeJws
import it.infocert.proximityverification.registry.RegistryTestSupport.installNoOpLogger
import it.infocert.proximityverification.registry.RegistryTestSupport.mockClient
import it.infocert.proximityverification.registry.RegistryTestSupport.resetLogger
import it.infocert.proximityverification.registry.model.CatalogCredential
import it.infocert.proximityverification.registry.model.ClaimTypeDefinition
import it.infocert.proximityverification.registry.model.RegistryEndpoints
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistryClientTest {

    private val taUrl: String = "https://ta.example.it"
    private val catalogUrl: String = "https://ta.example.it/registry/credentials"
    private val claimsUrl: String = "https://ta.example.it/registry/claims"

    @BeforeTest fun setUp() = installNoOpLogger()
    @AfterTest fun tearDown() = resetLogger()

    @Test
    fun discoverRegistry_jsonResponse_extractsEndpoints() = runTest {
        val client = mockClient(mapOf(
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = """{
                    "credential_catalog_endpoint":"$catalogUrl",
                    "claims_registry_endpoint":"$claimsUrl"
                }""",
                contentType = "application/json"
            )
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val endpoints: RegistryEndpoints? = registryClient.discoverRegistry(taUrl)

        assertNotNull(endpoints)
        assertEquals(catalogUrl, endpoints.credentialCatalog)
        assertEquals(claimsUrl, endpoints.claimsRegistry)
        assertNull(endpoints.schemaRegistry)
    }

    @Test
    fun discoverRegistry_jwtResponse_decodesPayload() = runTest {
        val client = mockClient(mapOf(
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = fakeJws("""{"credential_catalog_endpoint":"$catalogUrl"}"""),
                contentType = "application/jwt"
            )
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val endpoints = registryClient.discoverRegistry(taUrl)

        assertNotNull(endpoints)
        assertEquals(catalogUrl, endpoints.credentialCatalog)
    }

    @Test
    fun discoverRegistry_nestedEndpointsObject_extractsEndpoints() = runTest {
        val client = mockClient(mapOf(
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = """{
                    "endpoints": {
                        "credential_catalog": "$catalogUrl",
                        "claims_registry": "$claimsUrl",
                        "schema_registry": "https://ta.example.it/registry/schemas"
                    }
                }""",
                contentType = "application/json"
            )
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val endpoints: RegistryEndpoints? = registryClient.discoverRegistry(taUrl)

        assertNotNull(endpoints)
        assertEquals(catalogUrl, endpoints.credentialCatalog)
        assertEquals(claimsUrl, endpoints.claimsRegistry)
        assertEquals("https://ta.example.it/registry/schemas", endpoints.schemaRegistry)
    }

    @Test
    fun discoverRegistry_missingCatalogEndpoint_returnsNull() = runTest {
        val client = mockClient(mapOf(
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = """{}""",
                contentType = "application/json"
            )
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        assertNull(registryClient.discoverRegistry(taUrl))
    }

    @Test
    fun fetchCredentialCatalog_filtersByAllowedIssuers() = runTest {
        val payload = """{
            "credentials":[
                {
                    "credential_type":"pid",
                    "issuers":[{"id":"https://issuer-a.example.it"},{"id":"https://issuer-b.example.it"}]
                },
                {
                    "credential_type":"mDL",
                    "issuers":[{"id":"https://issuer-a.example.it"}]
                },
                {
                    "credential_type":"wallet_attestation",
                    "issuers":[{"id":"https://issuer-c.example.it"}]
                }
            ]
        }"""
        val client = mockClient(mapOf(
            catalogUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val results: List<CatalogCredential>? = registryClient.fetchCredentialCatalog(
            catalogUrl = catalogUrl,
            allowedIssuerIds = listOf("https://issuer-a.example.it")
        )

        assertNotNull(results)
        assertEquals(2, results.size)
        assertEquals("pid", results[0].credentialType)
        assertEquals(listOf("https://issuer-a.example.it"), results[0].issuerIds)
        assertEquals("mDL", results[1].credentialType)
    }

    @Test
    fun fetchCredentialCatalog_emptyAllowedIssuers_returnsAllEntries() = runTest {
        val payload = """{
            "credentials":[
                {
                    "credential_type":"pid",
                    "issuers":[{"id":"https://issuer-a.example.it"}]
                },
                {
                    "credential_type":"mDL",
                    "issuers":[{"id":"https://issuer-b.example.it"}]
                }
            ]
        }"""
        val client = mockClient(mapOf(
            catalogUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val results = registryClient.fetchCredentialCatalog(catalogUrl = catalogUrl, allowedIssuerIds = emptyList())

        assertNotNull(results)
        assertEquals(2, results.size)
        assertTrue(results.all { it.issuerIds.isNotEmpty() })
    }

    @Test
    fun fetchCredentialCatalog_jwsResponse_decodesPayload() = runTest {
        val payload = """{
            "credentials":[
                {
                    "credential_type":"pid",
                    "issuers":[{"id":"https://issuer-a.example.it"}]
                }
            ]
        }"""
        val client = mockClient(mapOf(
            catalogUrl to MockResponse(body = fakeJws(payload), contentType = "application/jwt")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val results = registryClient.fetchCredentialCatalog(catalogUrl = catalogUrl, allowedIssuerIds = emptyList())

        assertNotNull(results)
        assertEquals(1, results.size)
        assertEquals("pid", results[0].credentialType)
    }

    @Test
    fun fetchCredentialCatalog_joseResponse_decodesPayload() = runTest {
        val payload = """{
            "credentials":[
                {
                    "version":"1",
                    "credential_type":"mDL",
                    "issuers":[{"id":"https://issuer.example.it"}]
                }
            ]
        }"""
        val client = mockClient(mapOf(
            catalogUrl to MockResponse(body = fakeJws(payload), contentType = "application/jose")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val results = registryClient.fetchCredentialCatalog(catalogUrl = catalogUrl, allowedIssuerIds = emptyList())

        assertNotNull(results)
        assertEquals(1, results.size)
        assertEquals("mDL", results[0].credentialType)
    }

    @Test
    fun fetchCredentialCatalog_extractsVersionAndProximitySupport() = runTest {
        val payload = """{
            "credentials":[
                {
                    "version":"1",
                    "credential_type":"mDL",
                    "restriction_policy":{"presentation_flows":{"remote":true,"proximity":true}},
                    "issuers":[{"id":"https://issuer.example.it"}]
                },
                {
                    "version":"1",
                    "credential_type":"pid",
                    "restriction_policy":{"presentation_flows":{"remote":true,"proximity":false}},
                    "issuers":[{"id":"https://eid.example.it"}]
                }
            ]
        }"""
        val client = mockClient(mapOf(
            catalogUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val results = registryClient.fetchCredentialCatalog(catalogUrl = catalogUrl, allowedIssuerIds = emptyList())

        assertNotNull(results)
        assertEquals("1", results[0].version)
        assertEquals(true, results[0].isProximitySupported)
        assertEquals(false, results[1].isProximitySupported)
    }

    @Test
    fun fetchCredentialCatalog_unreachable_returnsNull() = runTest {
        val client = mockClient(emptyMap())
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        assertNull(registryClient.fetchCredentialCatalog(catalogUrl, emptyList()))
    }

    // --- Schema Registry ---

    @Test
    fun fetchSchemaRegistry_happyPath_parsesSchemas() = runTest {
        val schemaUrl = "https://ta.example.it/registry/schemas"
        val payload = """{
            "version":"1.0",
            "schemas":[
                {
                    "id":"mDL+mso_mdoc+org.iso.18013.5.1.mDL",
                    "version":"1",
                    "credential_type":"mDL",
                    "format":"mso_mdoc",
                    "docType":"org.iso.18013.5.1.mDL",
                    "schema_uri":"https://ta.example.it/.well-known/schemas/mdoc/mDL",
                    "schema_uri#integrity":"sha256-abc",
                    "description":"Driving license schema"
                },
                {
                    "id":"pid+dc+sd-jwt+urn:it-wallet:pid:1",
                    "version":"1",
                    "credential_type":"pid",
                    "format":"dc+sd-jwt",
                    "vct":"urn:it-wallet:pid:1",
                    "schema_uri":"https://ta.example.it/.well-known/vct/pid",
                    "schema_uri#integrity":"sha256-def"
                }
            ]
        }"""
        val client = mockClient(mapOf(schemaUrl to MockResponse(body = payload, contentType = "application/json")))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val result = registryClient.fetchSchemaRegistry(schemaUrl)

        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals("mDL", result[0].credentialType)
        assertEquals("mso_mdoc", result[0].format)
        assertEquals("org.iso.18013.5.1.mDL", result[0].docType)
        assertEquals("sha256-abc", result[0].schemaIntegrity)
    }

    @Test
    fun fetchSchemaRegistry_unreachable_returnsNull() = runTest {
        val registryClient = RegistryClient(http = RegistryHttp(client = mockClient(emptyMap())))

        assertNull(registryClient.fetchSchemaRegistry("https://ta.example.it/registry/schemas"))
    }

    // --- Step 6: Claims Registry ---

    @Test
    fun fetchClaimsRegistry_happyPath_parsesClaimTypes() = runTest {
        val payload = """{
            "registry_version": "1.0",
            "claims": {
                "given_name": {
                    "type": "string",
                    "description": "Person's given name",
                    "aliases": ["first_name", "forename"]
                },
                "birth_date": {
                    "type": "date",
                    "description": "Date of birth"
                },
                "driving_privileges": {
                    "type": "array",
                    "description": "Driving categories"
                }
            }
        }"""
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val result: Map<String, ClaimTypeDefinition>? = registryClient.fetchClaimsRegistry(claimsUrl)

        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals("string", result["given_name"]?.type)
        assertEquals(listOf("first_name", "forename"), result["given_name"]?.aliases)
        assertEquals("date", result["birth_date"]?.type)
        assertEquals("array", result["driving_privileges"]?.type)
    }

    @Test
    fun fetchClaimsRegistry_parsesFormatAndAliases() = runTest {
        val payload = """{
            "claims": {
                "birth_date": {"type":"string","format":"date","aliases":["birthdate"]},
                "portrait": {"type":"string","format":"data","encoding":"base64"},
                "driving_privileges": {"type":"array","nested_item_claims":["vehicle_category_code"]}
            }
        }"""
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val result = registryClient.fetchClaimsRegistry(claimsUrl)

        assertNotNull(result)
        assertEquals("date", result["birth_date"]?.format)
        assertEquals(listOf("birthdate"), result["birth_date"]?.aliases)
        assertEquals("data", result["portrait"]?.format)
    }

    @Test
    fun fetchClaimsRegistry_jwtResponse_decodesPayload() = runTest {
        val payload = """{"claims":{"tax_code":{"type":"string","description":"Codice Fiscale"}}}"""
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = fakeJws(payload), contentType = "application/jwt")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val result = registryClient.fetchClaimsRegistry(claimsUrl)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("string", result["tax_code"]?.type)
    }

    @Test
    fun fetchClaimsRegistry_missingClaimsObject_returnsNull() = runTest {
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = """{"registry_version":"1.0"}""", contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        assertNull(registryClient.fetchClaimsRegistry(claimsUrl))
    }

    @Test
    fun fetchClaimsRegistry_unreachable_returnsNull() = runTest {
        val client = mockClient(emptyMap())
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        assertNull(registryClient.fetchClaimsRegistry(claimsUrl))
    }

    @Test
    fun fetchClaimsRegistry_claimsAsArray_returnsNullWithoutThrowing() = runTest {
        val payload = """{"claims":[{"name":"family_name","type":"string"}]}"""
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        assertNull(registryClient.fetchClaimsRegistry(claimsUrl))
    }

    @Test
    fun fetchClaimsRegistry_unexpectedFieldShapes_returnsBestEffortMap() = runTest {
        val payload = """{
            "claims": {
                "good_one": {"type": "date"},
                "type_as_object": {"type": {"nested":"value"}},
                "aliases_as_string": {"type": "string", "aliases": "not-an-array"},
                "description_as_array": {"type": "boolean", "description": ["x","y"]}
            }
        }"""
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val result = registryClient.fetchClaimsRegistry(claimsUrl)

        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals("date", result["good_one"]?.type)
        assertNull(result["type_as_object"])
        assertEquals(emptyList(), result["aliases_as_string"]?.aliases)
        assertNull(result["description_as_array"]?.description)
    }

    @Test
    fun fetchClaimsRegistry_skipsEntriesWithoutType() = runTest {
        val payload = """{
            "claims": {
                "valid_claim": {"type": "date"},
                "no_type_claim": {"description": "missing type field"},
                "empty_type_claim": {"type": ""}
            }
        }"""
        val client = mockClient(mapOf(
            claimsUrl to MockResponse(body = payload, contentType = "application/json")
        ))
        val registryClient = RegistryClient(http = RegistryHttp(client = client))

        val result = registryClient.fetchClaimsRegistry(claimsUrl)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result["valid_claim"])
        assertNull(result["no_type_claim"])
        assertNull(result["empty_type_claim"])
    }
}
