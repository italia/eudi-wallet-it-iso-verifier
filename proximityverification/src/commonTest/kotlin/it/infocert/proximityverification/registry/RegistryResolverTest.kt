package it.infocert.proximityverification.registry

import it.infocert.proximityverification.core.catalog.ClaimDescriptor
import it.infocert.proximityverification.core.catalog.DocumentTypeCatalogImpl
import it.infocert.proximityverification.registry.RegistryTestSupport.InMemoryLocalStorage
import it.infocert.proximityverification.registry.RegistryTestSupport.fakeJws
import it.infocert.proximityverification.registry.RegistryTestSupport.installNoOpLogger
import it.infocert.proximityverification.registry.RegistryTestSupport.mockClient
import it.infocert.proximityverification.registry.RegistryTestSupport.resetLogger
import it.infocert.proximityverification.registry.model.ResolvedRegistryData
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistryResolverTest {

    private val rpEntityUrl: String = "https://rp.example.it"
    private val taEntityUrl: String = "https://ta.example.it"
    private val resolveEndpoint: String = "https://ta.example.it/resolve"
    private val catalogUrl: String = "https://ta.example.it/registry/credentials"
    private val claimsUrl: String = "https://ta.example.it/registry/claims"
    private val issuerId: String = "https://issuer.example.it"
    private val statusListUrl: String = "https://issuer.example.it/status-list-aggregation"

    @BeforeTest fun setUp() = installNoOpLogger()
    @AfterTest fun tearDown() = resetLogger()

    private val rpEntityConfigJws: String = fakeJws(
        """{"authority_hints":["$taEntityUrl"]}"""
    )

    private val taEntityConfigJws: String = fakeJws(
        """{
            "metadata":{
              "federation_entity":{
                "federation_resolve_endpoint":"$resolveEndpoint"
              }
            }
        }"""
    )

    private val registryDiscoveryBody: String = """{
        "credential_catalog_endpoint":"$catalogUrl"
    }"""

    private val catalogBody: String = """{
        "credentials":[
            {
                "credential_type":"pid",
                "issuers":[{"id":"$issuerId"}]
            }
        ]
    }"""

    private val issuerResolveBody: String = """{
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
                      "display":[{"name":"Family Name","locale":"en"}]},
                    {"path":["eu.europa.ec.eudi.pid.1","given_name"],"mandatory":true,
                      "display":[{"name":"Given Name","locale":"en"}]}
                  ]
                }
              }
            },
            "status_list_aggregation_endpoint":"$statusListUrl"
          }
        }
    }"""

    private val registryDiscoveryWithClaimsBody: String = """{
        "credential_catalog_endpoint":"$catalogUrl",
        "claims_registry_endpoint":"$claimsUrl"
    }"""

    private val claimsRegistryBody: String = """{
        "registry_version": "1.0",
        "claims": {
            "family_name": {"type": "string", "description": "Family name"},
            "given_name": {"type": "string", "description": "Given name"},
            "birth_date": {"type": "date", "description": "Date of birth"},
            "driving_privileges": {"type": "array", "description": "Driving categories"}
        }
    }"""

    private val issuerResolveBodyWithBirthDate: String = """{
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
                      "display":[{"name":"Family Name","locale":"en"}]},
                    {"path":["eu.europa.ec.eudi.pid.1","birth_date"],"mandatory":true,
                      "display":[{"name":"Birth Date","locale":"en"}]}
                  ]
                }
              }
            }
          }
        }
    }"""

    private fun happyClientWithClaimsRegistry() = mockClient(mapOf(
        "https://rp.example.it/.well-known/openid-federation" to MockResponse(
            body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
        ),
        "https://ta.example.it/.well-known/openid-federation" to MockResponse(
            body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
        ),
        "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
            body = registryDiscoveryWithClaimsBody, contentType = "application/json"
        ),
        catalogUrl to MockResponse(body = catalogBody, contentType = "application/json"),
        resolveEndpoint to MockResponse(body = issuerResolveBodyWithBirthDate, contentType = "application/json"),
        claimsUrl to MockResponse(body = claimsRegistryBody, contentType = "application/json")
    ))

    private val issuerResolveBodyWithPortrait: String = """{
        "metadata":{
          "openid_credential_issuer":{
            "credential_configurations_supported":{
              "PID-mso-mdoc":{
                "format":"mso_mdoc",
                "doctype":"eu.europa.ec.eudi.pid.1",
                "credential_metadata":{
                  "display":[{"name":"Personal ID","locale":"en"}],
                  "claims":[
                    {"path":["eu.europa.ec.eudi.pid.1","portrait"],"mandatory":true,
                      "display":[{"name":"Portrait","locale":"en"}]}
                  ]
                }
              }
            }
          }
        }
    }"""

    private val claimsRegistryBodyWithPortrait: String = """{
        "registry_version": "1.0",
        "claims": {
            "portrait": {"type": "string", "format": "data", "encoding": "base64"}
        }
    }"""

    private fun clientWithPortraitClaim() = mockClient(mapOf(
        "https://rp.example.it/.well-known/openid-federation" to MockResponse(
            body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
        ),
        "https://ta.example.it/.well-known/openid-federation" to MockResponse(
            body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
        ),
        "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
            body = registryDiscoveryWithClaimsBody, contentType = "application/json"
        ),
        catalogUrl to MockResponse(body = catalogBody, contentType = "application/json"),
        resolveEndpoint to MockResponse(body = issuerResolveBodyWithPortrait, contentType = "application/json"),
        claimsUrl to MockResponse(body = claimsRegistryBodyWithPortrait, contentType = "application/json")
    ))

    private fun happyClient() = mockClient(mapOf(
        "https://rp.example.it/.well-known/openid-federation" to MockResponse(
            body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
        ),
        "https://ta.example.it/.well-known/openid-federation" to MockResponse(
            body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
        ),
        "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
            body = registryDiscoveryBody, contentType = "application/json"
        ),
        catalogUrl to MockResponse(body = catalogBody, contentType = "application/json"),
        resolveEndpoint to MockResponse(body = issuerResolveBody, contentType = "application/json")
    ))

    private fun resolverWith(
        client: io.ktor.client.HttpClient,
        online: Boolean = true
    ): RegistryResolver {
        val http = RegistryHttp(client = client)
        return RegistryResolver(
            federationDiscovery = FederationDiscovery(http = http),
            registryClient = RegistryClient(http = http),
            issuerMetadataResolver = IssuerMetadataResolver(http = http),
            networkProbe = { online },
            clock = { 1_700_000_000_000L }
        )
    }

    @Test
    fun resolve_online_populatesCatalogPersistsData() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(happyClient(), online = true)

        val data: ResolvedRegistryData? = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals(1, data.credentials.size)
        assertEquals(1, data.issuerMetadata.size)
        assertEquals(statusListUrl, data.issuerMetadata[0].statusListAggregationEndpoint)
        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        val cached: String? = storage.getElement(RegistryResolver.STORAGE_KEY)
        assertNotNull(cached)
        assertTrue(cached.contains("eu.europa.ec.eudi.pid.1"))
    }

    @Test
    fun resolve_offlineWithCache_loadsCachedData() = runTest {
        val warmCatalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val warmResolver = resolverWith(happyClient(), online = true)
        warmResolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = warmCatalog,
            storage = storage
        )

        val coldCatalog = DocumentTypeCatalogImpl()
        val offlineResolver = resolverWith(mockClient(emptyMap()), online = false)
        val data = offlineResolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = coldCatalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals(1, data.issuerMetadata.size)
        assertTrue(coldCatalog.isRegistered("eu.europa.ec.eudi.pid.1"))
    }

    @Test
    fun resolve_offlineNoCache_keepsBuiltInCatalogOnly() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(mockClient(emptyMap()), online = false)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = emptyList(),
            catalog = catalog,
            storage = storage
        )

        assertNull(data)
        assertFalse(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        assertTrue(storage.snapshot().isEmpty())
    }

    @Test
    fun resolve_onlineButDiscoveryFails_fallsBackToCache() = runTest {
        val storage = InMemoryLocalStorage()
        val warmCatalog = DocumentTypeCatalogImpl()
        resolverWith(happyClient(), online = true).resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = warmCatalog,
            storage = storage
        )

        val coldCatalog = DocumentTypeCatalogImpl()
        val brokenResolver = resolverWith(mockClient(emptyMap()), online = true)
        val data = brokenResolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = coldCatalog,
            storage = storage
        )

        assertNotNull(data)
        assertTrue(coldCatalog.isRegistered("eu.europa.ec.eudi.pid.1"))
    }

    @Test
    fun resolve_onlineFailureWithoutCache_returnsNull() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(mockClient(emptyMap()), online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNull(data)
        assertFalse(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
    }

    @Test
    fun resolve_emptyAllowedIssuers_acceptsEveryIssuer() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(happyClient(), online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = emptyList(),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals(1, data.issuerMetadata.size)
        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
    }

    // --- Phase 2: Claims Registry enrichment ---

    @Test
    fun resolve_withClaimsRegistry_enrichesClaimDataTypes() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(happyClientWithClaimsRegistry(), online = true)

        val data: ResolvedRegistryData? = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertTrue(data.claimTypes.isNotEmpty())
        assertEquals("date", data.claimTypes["birth_date"]?.type)
        val configs = data.issuerMetadata.first().credentialConfigurations
        val birthDateClaim = configs.first().claims.first { it.identifier == "birth_date" }
        assertEquals(ClaimDescriptor.DATA_TYPE_DATE, birthDateClaim.dataType)
        val familyNameClaim = configs.first().claims.first { it.identifier == "family_name" }
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, familyNameClaim.dataType)
    }

    @Test
    fun resolve_withClaimsRegistry_portraitStringDataKeepsStringSoCatalogCanFallback() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(clientWithPortraitClaim(), online = true)

        val data: ResolvedRegistryData? = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals("string", data.claimTypes["portrait"]?.type)
        val configs = data.issuerMetadata.first().credentialConfigurations
        val portraitClaim = configs.first().claims.first { it.identifier == "portrait" }
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, portraitClaim.dataType)
    }

    @Test
    fun resolve_claimsRegistryUnavailable_continuesToWork() = runTest {
        val clientWithBrokenClaims = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryWithClaimsBody, contentType = "application/json"
            ),
            catalogUrl to MockResponse(body = catalogBody, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBody, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(clientWithBrokenClaims, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertTrue(data.claimTypes.isEmpty())
        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
    }

    @Test
    fun resolve_noClaimsRegistryEndpoint_skipsStep6() = runTest {
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(happyClient(), online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertTrue(data.claimTypes.isEmpty())
    }

    // --- re-init: stale registry docTypes pruned on shrinking resolution ---

    @Test
    fun resolve_secondOnlineResolution_prunesDocTypesNoLongerInCatalog() = runTest {
        val catalogBodyAB = """{
            "credentials":[
                {"credential_type":"pid","issuers":[{"id":"$issuerId"}]},
                {"credential_type":"mDL","issuers":[{"id":"$issuerId"}]}
            ]
        }"""
        val catalogBodyA = """{
            "credentials":[
                {"credential_type":"pid","issuers":[{"id":"$issuerId"}]}
            ]
        }"""
        val issuerResolveBodyAB = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "PID-mso-mdoc":{
                    "format":"mso_mdoc","doctype":"eu.europa.ec.eudi.pid.1",
                    "credential_metadata":{
                      "display":[{"name":"Personal ID","locale":"en"}],
                      "claims":[{"path":["eu.europa.ec.eudi.pid.1","family_name"],"mandatory":true,
                          "display":[{"name":"Family Name","locale":"en"}]}]
                    }
                  },
                  "mDL-mso-mdoc":{
                    "format":"mso_mdoc","doctype":"org.iso.18013.5.1.mDL",
                    "credential_metadata":{
                      "display":[{"name":"Driving License","locale":"en"}],
                      "claims":[{"path":["org.iso.18013.5.1","family_name"],"mandatory":true,
                          "display":[{"name":"Family Name","locale":"en"}]}]
                    }
                  }
                }
              }
            }
        }"""
        val sharedCommonResponses = mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryBody, contentType = "application/json"
            )
        )
        val firstClient = mockClient(sharedCommonResponses + mapOf(
            catalogUrl to MockResponse(body = catalogBodyAB, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBodyAB, contentType = "application/json")
        ))
        val secondClient = mockClient(sharedCommonResponses + mapOf(
            catalogUrl to MockResponse(body = catalogBodyA, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBody, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()

        val firstResolver = resolverWith(firstClient, online = true)
        firstResolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )
        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))

        val secondResolver = resolverWith(secondClient, online = true)
        secondResolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        assertFalse(catalog.isRegistered("org.iso.18013.5.1.mDL"))
    }

    // --- issuer metadata: all mso_mdoc configs from catalog-discovered issuers ---

    @Test
    fun resolve_acceptsAllMsoMdocConfigsFromCatalogDiscoveredIssuer() = runTest {
        val issuerResolveBodyWithMultipleDocTypes = """{
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
                  },
                  "mDL-mso-mdoc":{
                    "format":"mso_mdoc",
                    "doctype":"org.iso.18013.5.1.mDL",
                    "credential_metadata":{
                      "display":[{"name":"Driving License","locale":"en"}],
                      "claims":[
                        {"path":["org.iso.18013.5.1","family_name"],"mandatory":true,
                          "display":[{"name":"Family Name","locale":"en"}]}
                      ]
                    }
                  }
                }
              }
            }
        }"""
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryBody, contentType = "application/json"
            ),
            catalogUrl to MockResponse(body = catalogBody, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBodyWithMultipleDocTypes, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(client, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals(1, data.issuerMetadata.size)
        val resolvedDocTypes = data.issuerMetadata.first().credentialConfigurations.map { it.docType }.sorted()
        assertEquals(listOf("eu.europa.ec.eudi.pid.1", "org.iso.18013.5.1.mDL"), resolvedDocTypes)
        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))
    }

    // --- Schema Registry correlation ---

    private val schemaUrl: String = "https://ta.example.it/registry/schemas"

    private val registryDiscoveryWithSchemaBody: String = """{
        "credential_catalog_endpoint":"$catalogUrl",
        "schema_registry_endpoint":"$schemaUrl"
    }"""

    private val schemaRegistryBody: String = """{
        "schemas":[
            {
                "id":"mDL+mso_mdoc",
                "version":"1",
                "credential_type":"mDL",
                "format":"mso_mdoc",
                "docType":"org.iso.18013.5.1.mDL"
            }
        ]
    }"""

    private val catalogBodyMdlAndPid: String = """{
        "credentials":[
            {"version":"1","credential_type":"mDL","issuers":[{"id":"$issuerId"}]},
            {"version":"1","credential_type":"pid","issuers":[{"id":"$issuerId"}]}
        ]
    }"""

    private val issuerResolveBodyMdlAndResidency: String = """{
        "metadata":{
          "openid_credential_issuer":{
            "credential_configurations_supported":{
              "mDL-mso-mdoc":{
                "format":"mso_mdoc","doctype":"org.iso.18013.5.1.mDL",
                "credential_metadata":{
                  "display":[{"name":"mDL","locale":"en"}],
                  "claims":[{"path":["org.iso.18013.5.1","family_name"],"mandatory":true,
                      "display":[{"name":"Family Name","locale":"en"}]}]
                }
              },
              "residency-mso-mdoc":{
                "format":"mso_mdoc","doctype":"it.example.residency.1",
                "credential_metadata":{
                  "display":[{"name":"Residency","locale":"en"}],
                  "claims":[{"path":["it.example.residency.1","city"],"mandatory":true,
                      "display":[{"name":"City","locale":"en"}]}]
                }
              }
            }
          }
        }
    }"""

    @Test
    fun resolve_withSchemaRegistry_filtersIssuerConfigsByExpectedDocType() = runTest {
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryWithSchemaBody, contentType = "application/json"
            ),
            catalogUrl to MockResponse(body = catalogBodyMdlAndPid, contentType = "application/json"),
            schemaUrl to MockResponse(body = schemaRegistryBody, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBodyMdlAndResidency, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(client, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals(1, data.schemas.size)
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))
        assertFalse(catalog.isRegistered("it.example.residency.1"))
    }

    @Test
    fun resolve_proximityFalseInCatalog_excludesEntry() = runTest {
        val catalogBodyWithProximityFalse = """{
            "credentials":[
                {
                    "version":"1","credential_type":"mDL",
                    "restriction_policy":{"presentation_flows":{"remote":true,"proximity":false}},
                    "issuers":[{"id":"$issuerId"}]
                }
            ]
        }"""
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryBody, contentType = "application/json"
            ),
            catalogUrl to MockResponse(body = catalogBodyWithProximityFalse, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBodyMdlAndResidency, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(client, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertFalse(catalog.isRegistered("org.iso.18013.5.1.mDL"))
        assertFalse(catalog.isRegistered("it.example.residency.1"))
    }

    @Test
    fun resolve_schemaRegistryUnavailable_keepsAllIssuerMsoMdocConfigs() = runTest {
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryWithSchemaBody, contentType = "application/json"
            ),
            catalogUrl to MockResponse(body = catalogBodyMdlAndPid, contentType = "application/json"),
            // schemaUrl deliberately missing → fetch returns null
            resolveEndpoint to MockResponse(body = issuerResolveBodyMdlAndResidency, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(client, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertTrue(data.schemas.isEmpty())
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))
        assertTrue(catalog.isRegistered("it.example.residency.1"))
    }

    @Test
    fun mapRegistryType_mapsKnownTypes() {
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, RegistryResolver.mapRegistryType("string"))
        assertEquals(ClaimDescriptor.DATA_TYPE_DATE, RegistryResolver.mapRegistryType("date"))
        assertEquals(ClaimDescriptor.DATA_TYPE_BOOLEAN, RegistryResolver.mapRegistryType("boolean"))
        assertEquals(ClaimDescriptor.DATA_TYPE_NUMBER, RegistryResolver.mapRegistryType("numeric"))
        assertEquals(ClaimDescriptor.DATA_TYPE_NUMBER, RegistryResolver.mapRegistryType("number"))
        assertEquals(ClaimDescriptor.DATA_TYPE_NUMBER, RegistryResolver.mapRegistryType("integer"))
        assertEquals(ClaimDescriptor.DATA_TYPE_PICTURE, RegistryResolver.mapRegistryType("image"))
        assertEquals(ClaimDescriptor.DATA_TYPE_COMPLEX, RegistryResolver.mapRegistryType("array"))
        assertEquals(ClaimDescriptor.DATA_TYPE_COMPLEX, RegistryResolver.mapRegistryType("object"))
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, RegistryResolver.mapRegistryType("email"))
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, RegistryResolver.mapRegistryType("url"))
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, RegistryResolver.mapRegistryType("unknown_type"))
        assertEquals(ClaimDescriptor.DATA_TYPE_DATE, RegistryResolver.mapRegistryType("string", "date"))
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, RegistryResolver.mapRegistryType("string", "data"))
        assertEquals(ClaimDescriptor.DATA_TYPE_STRING, RegistryResolver.mapRegistryType("string", "binary"))
    }

    @Test
    fun resolve_withClaimsRegistryAlias_enrichesByAlias() = runTest {
        val issuerResolveBodyWithBirthdateAlias = """{
            "metadata":{
              "openid_credential_issuer":{
                "credential_configurations_supported":{
                  "PID-mso-mdoc":{
                    "format":"mso_mdoc",
                    "doctype":"eu.europa.ec.eudi.pid.1",
                    "credential_metadata":{
                      "display":[{"name":"Personal ID","locale":"en"}],
                      "claims":[
                        {"path":["eu.europa.ec.eudi.pid.1","birthdate"],"mandatory":true,
                          "display":[{"name":"Birth Date","locale":"en"}]}
                      ]
                    }
                  }
                }
              }
            }
        }"""
        val claimsBodyWithAlias = """{
            "claims": {
                "birth_date": {"type":"string","format":"date","aliases":["birthdate"]}
            }
        }"""
        val client = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/it-wallet-registry" to MockResponse(
                body = registryDiscoveryWithClaimsBody, contentType = "application/json"
            ),
            catalogUrl to MockResponse(body = catalogBody, contentType = "application/json"),
            resolveEndpoint to MockResponse(body = issuerResolveBodyWithBirthdateAlias, contentType = "application/json"),
            claimsUrl to MockResponse(body = claimsBodyWithAlias, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(client, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        val configs = data.issuerMetadata.first().credentialConfigurations
        val birthdate = configs.first().claims.first { it.identifier == "birthdate" }
        assertEquals(ClaimDescriptor.DATA_TYPE_DATE, birthdate.dataType)
    }

    // --- Direct-federation fallback (resolveFromAllowedIssuers) ---

    @Test
    fun resolve_onlineRegistryUnavailableWithAllowedIssuers_usesDirectFederationFallback() = runTest {
        // Steps 1 (federation discovery) respond; Step 2+ (registry) are absent → 404.
        // With allowedIssuerIds non-empty, the resolver should fall back to direct-federation.
        val clientFederationOnlyWithResolve = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            // No it-wallet-registry → Steps 2-4 fail
            resolveEndpoint to MockResponse(body = issuerResolveBody, contentType = "application/json")
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(clientFederationOnlyWithResolve, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = listOf(issuerId),
            catalog = catalog,
            storage = storage
        )

        assertNotNull(data)
        assertEquals(1, data.issuerMetadata.size)
        assertEquals(issuerId, data.issuerMetadata[0].entityId)
        assertEquals(statusListUrl, data.issuerMetadata[0].statusListAggregationEndpoint)
        // Synthetic catalog: credentialType = docType
        assertTrue(data.credentials.any { it.issuerIds.contains(issuerId) })
        // Catalog updated with issuer's credentialConfigurations
        assertTrue(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        // Result persisted to cache
        val cached: String? = storage.getElement(RegistryResolver.STORAGE_KEY)
        assertNotNull(cached)
    }

    @Test
    fun resolve_onlineRegistryUnavailableWithEmptyAllowedIssuers_returnsNull() = runTest {
        // Same setup but allowedIssuerIds = emptyList() → fallback should NOT run.
        val clientFederationOnly = mockClient(mapOf(
            "https://rp.example.it/.well-known/openid-federation" to MockResponse(
                body = rpEntityConfigJws, contentType = "application/entity-statement+jwt"
            ),
            "https://ta.example.it/.well-known/openid-federation" to MockResponse(
                body = taEntityConfigJws, contentType = "application/entity-statement+jwt"
            )
            // No registry, no resolveEndpoint
        ))
        val catalog = DocumentTypeCatalogImpl()
        val storage = InMemoryLocalStorage()
        val resolver = resolverWith(clientFederationOnly, online = true)

        val data = resolver.resolve(
            federationEntityUrl = rpEntityUrl,
            allowedIssuerIds = emptyList(),
            catalog = catalog,
            storage = storage
        )

        assertNull(data)
        assertFalse(catalog.isRegistered("eu.europa.ec.eudi.pid.1"))
        assertTrue(storage.snapshot().isEmpty())
    }
}
