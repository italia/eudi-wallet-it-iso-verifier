package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentTypeCatalogImplTest {

    @BeforeTest
    fun setUp() {
        LoggerConfig.delegateLogger = object : LoggerDelegate {
            override fun log(level: LogLevel, tag: String, message: String) {}
        }
    }

    @AfterTest
    fun tearDown() {
        LoggerConfig.delegateLogger = null
    }

    private fun createCatalog(): DocumentTypeCatalogImpl = DocumentTypeCatalogImpl()

    private val singleConfigJson: String = """
    {
      "doctype": "org.iso.18013.5.1.mDL",
      "credential_metadata": {
        "display": [{ "name": "Driving License", "locale": "en" }],
        "claims": [
          { "path": ["org.iso.18013.5.1", "family_name"], "mandatory": true, "display": [{ "name": "Family Name", "locale": "en" }] }
        ]
      }
    }
    """.trimIndent()

    @Test
    fun registerFromJson_singleConfig_isQueryable() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()

        catalog.registerFromJson(singleConfigJson)

        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))
        val actualConfig: CredentialConfiguration? = catalog.getConfiguration("org.iso.18013.5.1.mDL")
        assertNotNull(actualConfig)
        assertEquals("org.iso.18013.5.1.mDL", actualConfig.docType)
    }

    @Test
    fun registerFromJson_duplicateDocType_lastWriteWins() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        catalog.registerFromJson(singleConfigJson)
        val updatedJson: String = """
        {
          "doctype": "org.iso.18013.5.1.mDL",
          "credential_metadata": {
            "display": [{ "name": "Updated License", "locale": "en" }],
            "claims": []
          }
        }
        """.trimIndent()

        catalog.registerFromJson(updatedJson)

        val actualConfig: CredentialConfiguration? = catalog.getConfiguration("org.iso.18013.5.1.mDL")
        assertNotNull(actualConfig)
        assertEquals("Updated License", actualConfig.display.first().name)
    }

    @Test
    fun unregister_existingDocType_returnsTrue() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        catalog.registerFromJson(singleConfigJson)

        val actualResult: Boolean = catalog.unregister("org.iso.18013.5.1.mDL")

        assertTrue(actualResult)
        assertFalse(catalog.isRegistered("org.iso.18013.5.1.mDL"))
    }

    @Test
    fun unregister_nonExistentDocType_returnsFalse() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()

        val actualResult: Boolean = catalog.unregister("nonexistent")

        assertFalse(actualResult)
    }

    @Test
    fun clear_removesAllConfigurations() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        catalog.registerFromJson(singleConfigJson)

        catalog.clear()

        assertTrue(catalog.getRegisteredConfigurations().isEmpty())
    }

    @Test
    fun getRegisteredConfigurations_returnsAllRegistered() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        catalog.registerFromJson(singleConfigJson)
        val walletJson: String = """
        {
          "doctype": "org.iso.18013.5.1.IT.WalletAttestation",
          "credential_metadata": {
            "display": [],
            "claims": []
          }
        }
        """.trimIndent()
        catalog.registerFromJson(walletJson)

        val actualConfigs: List<CredentialConfiguration> = catalog.getRegisteredConfigurations()

        assertEquals(2, actualConfigs.size)
    }

    @Test
    fun getConfiguration_nonExistent_returnsNull() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()

        val actualConfig: CredentialConfiguration? = catalog.getConfiguration("nonexistent")

        assertNull(actualConfig)
    }

    @Test
    fun registerFromJson_issuerMetadataFormat_registersMultiple() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        val issuerMetadataJson: String = """
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

        catalog.registerFromJson(issuerMetadataJson)

        assertEquals(2, catalog.getRegisteredConfigurations().size)
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.IT.WalletAttestation"))
    }

    @Test
    fun concurrentRegisterAndRead_doesNotThrow() = runBlocking {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        val iterations: Int = 100
        val jobs = (1..iterations).map { i ->
            launch(Dispatchers.Default) {
                val jsonString: String = """
                {
                  "doctype": "test.concurrent.$i",
                  "credential_metadata": {
                    "display": [],
                    "claims": []
                  }
                }
                """.trimIndent()
                catalog.registerFromJson(jsonString)
                catalog.getRegisteredConfigurations()
                catalog.isRegistered("test.concurrent.$i")
            }
        }
        jobs.forEach { it.join() }
        assertTrue(catalog.getRegisteredConfigurations().isNotEmpty())
    }

    @Test
    fun clearThenRegister_restoresCatalog() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        catalog.registerFromJson(singleConfigJson)
        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))

        catalog.clear()

        assertTrue(catalog.getRegisteredConfigurations().isEmpty())
        assertFalse(catalog.isRegistered("org.iso.18013.5.1.mDL"))

        catalog.registerFromJson(singleConfigJson)

        assertTrue(catalog.isRegistered("org.iso.18013.5.1.mDL"))
        assertEquals(1, catalog.getRegisteredConfigurations().size)
    }

    @Test
    fun clearThenGetConfigurations_returnsEmptyList() {
        val catalog: DocumentTypeCatalogImpl = createCatalog()
        catalog.registerFromJson(singleConfigJson)

        catalog.clear()

        val actualConfigs: List<CredentialConfiguration> = catalog.getRegisteredConfigurations()
        assertTrue(actualConfigs.isEmpty())
    }
}
