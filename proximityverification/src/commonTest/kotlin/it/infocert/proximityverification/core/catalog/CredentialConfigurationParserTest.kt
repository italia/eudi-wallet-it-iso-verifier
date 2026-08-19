package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CredentialConfigurationParserTest {

    @Test
    fun parseSingle_validJson_returnsConfiguration() {
        val inputJson = """
        {
          "doctype": "org.iso.18013.5.1.mDL",
          "credential_metadata": {
            "display": [{ "name": "Driving License", "locale": "en" }],
            "claims": [
              { "path": ["org.iso.18013.5.1", "family_name"], "mandatory": true, "display": [{ "name": "Family Name", "locale": "en" }] },
              { "path": ["org.iso.18013.5.1", "given_name"], "mandatory": true, "display": [{ "name": "Given Names", "locale": "en" }] }
            ]
          }
        }
        """.trimIndent()

        val actualConfig: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals("org.iso.18013.5.1.mDL", actualConfig.docType)
        assertEquals(1, actualConfig.display.size)
        assertEquals("Driving License", actualConfig.display.first().name)
        assertEquals(2, actualConfig.claims.size)
        assertTrue(actualConfig.claims.all { it.isMandatory })
        assertTrue(actualConfig.claims.all { it.ns == "org.iso.18013.5.1" })
    }

    @Test
    fun parseIssuerMetadata_validJson_extractsMsoMdocEntries() {
        val inputJson = """
        {
          "credential_configurations_supported": {
            "mdl_config": {
              "format": "mso_mdoc",
              "doctype": "org.iso.18013.5.1.mDL",
              "credential_metadata": {
                "display": [{ "name": "mDL", "locale": "en" }],
                "claims": [
                  { "path": ["org.iso.18013.5.1", "family_name"], "mandatory": true, "display": [{ "name": "Family Name", "locale": "en" }] }
                ]
              }
            },
            "jwt_vc_config": {
              "format": "jwt_vc_json",
              "doctype": "some.jwt.type"
            }
          }
        }
        """.trimIndent()

        val actualConfigs: List<CredentialConfiguration> =
            CredentialConfigurationParser.parseIssuerMetadata(inputJson)

        assertEquals(1, actualConfigs.size)
        assertEquals("org.iso.18013.5.1.mDL", actualConfigs.first().docType)
    }

    @Test
    fun parseIssuerMetadata_noMsoMdocEntries_throwsPVE32() {
        val inputJson = """
        {
          "credential_configurations_supported": {
            "jwt_config": {
              "format": "jwt_vc_json",
              "doctype": "some.jwt.type"
            }
          }
        }
        """.trimIndent()

        val exception: ProximityVerificationException = assertFailsWith {
            CredentialConfigurationParser.parseIssuerMetadata(inputJson)
        }

        assertEquals("PVE32", exception.code.code)
    }

    @Test
    fun parseSingle_invalidJson_throwsPVE31() {
        val exception: ProximityVerificationException = assertFailsWith {
            CredentialConfigurationParser.parseSingle("not valid json")
        }

        assertEquals("PVE31", exception.code.code)
    }

    @Test
    fun parseSingle_missingDoctype_throwsPVE31() {
        val exception: ProximityVerificationException = assertFailsWith {
            CredentialConfigurationParser.parseSingle("""{ "credential_metadata": { "display": [] } }""")
        }

        assertEquals("PVE31", exception.code.code)
    }

    @Test
    fun detectAndParse_singleFormat_returnsSingleConfig() {
        val inputJson = """
        {
          "doctype": "test.doc.type",
          "credential_metadata": {
            "display": [],
            "claims": []
          }
        }
        """.trimIndent()

        val actualConfigs: List<CredentialConfiguration> =
            CredentialConfigurationParser.detectAndParse(inputJson)

        assertEquals(1, actualConfigs.size)
        assertEquals("test.doc.type", actualConfigs.first().docType)
    }

    @Test
    fun detectAndParse_issuerMetadataFormat_returnsMultipleConfigs() {
        val inputJson = """
        {
          "credential_configurations_supported": {
            "mdl": {
              "format": "mso_mdoc",
              "doctype": "org.iso.18013.5.1.mDL",
              "credential_metadata": {
                "display": [],
                "claims": []
              }
            },
            "wa": {
              "format": "mso_mdoc",
              "doctype": "org.iso.18013.5.1.IT.WalletAttestation",
              "credential_metadata": {
                "display": [],
                "claims": []
              }
            }
          }
        }
        """.trimIndent()

        val actualConfigs: List<CredentialConfiguration> =
            CredentialConfigurationParser.detectAndParse(inputJson)

        assertEquals(2, actualConfigs.size)
    }

    @Test
    fun parseSingle_unknownFieldsIgnored_returnsConfiguration() {
        val inputJson = """
        {
          "doctype": "test.type",
          "format": "mso_mdoc",
          "credential_metadata": {
            "unknown_field": "should be ignored",
            "display": [{ "name": "Test", "locale": "en", "extra": "ignored" }],
            "claims": [
              {
                "path": ["ns1", "field1"],
                "mandatory": false,
                "value_type": "string",
                "placeholder": "ignored",
                "source": "user",
                "display": [{ "name": "Field 1", "locale": "en" }]
              }
            ]
          }
        }
        """.trimIndent()

        val actualConfig: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals("test.type", actualConfig.docType)
        assertEquals(1, actualConfig.claims.size)
        assertEquals("field1", actualConfig.claims.first().identifier)
        assertEquals("ns1", actualConfig.claims.first().ns)
    }

    @Test
    fun parseSingle_multipleNamespaces_flattenedCorrectly() {
        val inputJson = """
        {
          "doctype": "test.type",
          "credential_metadata": {
            "display": [],
            "claims": [
              { "path": ["ns.one", "a"], "mandatory": true, "display": [] },
              { "path": ["ns.one", "b"], "mandatory": false, "display": [] },
              { "path": ["ns.two", "c"], "mandatory": true, "display": [] }
            ]
          }
        }
        """.trimIndent()

        val actualConfig: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals(3, actualConfig.claims.size)
        val expectedNamespaces: Set<String> = setOf("ns.one", "ns.two")
        assertEquals(expectedNamespaces, actualConfig.claims.map { it.ns }.toSet())
    }

    @Test
    fun parseIssuerMetadata_claimsArrayWithRealIssuerShape_parsesCorrectly() {
        val inputJson = """
        {
          "credential_configurations_supported": {
            "it.infocert.ipzs.mdl_mdoc": {
              "format": "mso_mdoc",
              "doctype": "org.iso.18013.5.1.mDL",
              "credential_metadata": {
                "display": [{ "name": "Driving License", "locale": "en" }],
                "claims": [
                  {
                    "path": ["org.iso.18013.5.1", "family_name"],
                    "mandatory": true,
                    "display": [{ "name": "Family Name", "locale": "en" }],
                    "value_type": "string",
                    "placeholder": "Rossi",
                    "source": "user"
                  },
                  {
                    "path": ["org.iso.18013.5.1", "given_name"],
                    "mandatory": true,
                    "display": [{ "name": "Given Names", "locale": "en" }],
                    "value_type": "string"
                  },
                  {
                    "path": ["org.iso.18013.5.1.IT", "sub"],
                    "mandatory": true,
                    "display": [{ "name": "Subject Identifier", "locale": "en" }]
                  }
                ]
              }
            }
          }
        }
        """.trimIndent()

        val actualConfigs: List<CredentialConfiguration> =
            CredentialConfigurationParser.parseIssuerMetadata(inputJson)

        assertEquals(1, actualConfigs.size)
        val config: CredentialConfiguration = actualConfigs.first()
        assertEquals("org.iso.18013.5.1.mDL", config.docType)
        assertEquals(3, config.claims.size)
        val familyName: ClaimDescriptor = config.claims.first { it.identifier == "family_name" }
        assertEquals("org.iso.18013.5.1", familyName.ns)
        assertTrue(familyName.isMandatory)
        val sub: ClaimDescriptor = config.claims.first { it.identifier == "sub" }
        assertEquals("org.iso.18013.5.1.IT", sub.ns)
    }

    @Test
    fun parseClaims_deduplicatesByNsAndIdentifier() {
        val inputJson = """
        {
          "doctype": "org.iso.18013.5.1.mDL",
          "credential_metadata": {
            "display": [],
            "claims": [
              { "path": ["org.iso.18013.5.1", "family_name"], "mandatory": true, "display": [] },
              { "path": ["org.iso.18013.5.1", "driving_privileges"], "value_type": "complex", "mandatory": true, "display": [] },
              { "path": ["org.iso.18013.5.1", "driving_privileges"], "value_type": "complex", "mandatory": true, "display": [] }
            ]
          }
        }
        """.trimIndent()

        val config: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals(2, config.claims.size)
        assertEquals(1, config.claims.count { it.identifier == "driving_privileges" })
    }

    @Test
    fun parseClaims_skipsEntriesWithInvalidPath() {
        val inputJson = """
        {
          "doctype": "test.type",
          "credential_metadata": {
            "display": [],
            "claims": [
              { "path": ["only_one_element"], "mandatory": true, "display": [] },
              { "path": ["ns", "valid"], "mandatory": false, "display": [] },
              { "mandatory": true, "display": [] }
            ]
          }
        }
        """.trimIndent()

        val actualConfig: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals(1, actualConfig.claims.size)
        assertEquals("valid", actualConfig.claims.first().identifier)
    }

    @Test
    fun parse_labelFieldFallback_usedWhenNameAbsent() {
        // Issuer non-conformance: display objects carry "label" (SD-JWT-VC field)
        // instead of the spec-mandated "name". Parser must fall back to "label" so
        // credential/claim names are not blank in the UI.
        val inputJson = """
        {
          "doctype": "org.iso.18013.5.1.mDL",
          "credential_metadata": {
            "display": [{ "label": "Patente di guida", "locale": "it-IT" }],
            "claims": [
              { "path": ["org.iso.18013.5.1", "given_name"], "display": [{ "label": "Nome", "locale": "it-IT" }] }
            ]
          }
        }
        """.trimIndent()

        val config: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals("Patente di guida", config.display.first().name)
        assertEquals(1, config.claims.size)
        assertEquals("Nome", config.claims.first().display.first().name)
    }

    @Test
    fun parse_namePreferredOverLabel_whenBothPresent() {
        val inputJson = """
        {
          "doctype": "org.iso.18013.5.1.mDL",
          "credential_metadata": {
            "display": [],
            "claims": [
              { "path": ["org.iso.18013.5.1", "given_name"], "display": [{ "name": "Given Name", "label": "ignored", "locale": "en" }] }
            ]
          }
        }
        """.trimIndent()

        val config: CredentialConfiguration = CredentialConfigurationParser.parseSingle(inputJson)

        assertEquals("Given Name", config.claims.first().display.first().name)
    }
}
