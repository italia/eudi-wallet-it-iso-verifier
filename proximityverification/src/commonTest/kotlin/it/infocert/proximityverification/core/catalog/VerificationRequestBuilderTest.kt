package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.VerificationRequest
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VerificationRequestBuilderTest {

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

    private fun createPopulatedCatalog(): DocumentTypeCatalogImpl {
        val catalog = DocumentTypeCatalogImpl()
        catalog.registerFromJson(
            """
            {
              "doctype": "org.iso.18013.5.1.mDL",
              "credential_metadata": {
                "display": [{ "name": "Driving License", "locale": "en" }],
                "claims": [
                  { "path": ["org.iso.18013.5.1", "family_name"], "mandatory": true, "display": [{ "name": "Family Name", "locale": "en" }] },
                  { "path": ["org.iso.18013.5.1", "given_name"], "mandatory": true, "display": [{ "name": "Given Names", "locale": "en" }] },
                  { "path": ["org.iso.18013.5.1", "portrait"], "mandatory": true, "display": [{ "name": "Portrait", "locale": "en" }] },
                  { "path": ["org.iso.18013.5.1", "age_over_18"], "mandatory": false, "display": [{ "name": "Over 18", "locale": "en" }] },
                  { "path": ["org.iso.18013.5.1.IT", "sub"], "mandatory": true, "display": [{ "name": "Subject", "locale": "en" }] }
                ]
              }
            }
            """.trimIndent()
        )
        return catalog
    }

    @Test
    fun forDocType_unknownDocType_throwsPVE35() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val exception: ProximityVerificationException = assertFailsWith {
            builder.forDocType("nonexistent.doc.type")
        }

        assertEquals("PVE35", exception.code.code)
    }

    @Test
    fun build_withValidClaims_returnsRequest() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val actualRequest: VerificationRequest = builder.forDocType("org.iso.18013.5.1.mDL")
            .requestClaim("org.iso.18013.5.1", "family_name")
            .requestClaim("org.iso.18013.5.1", "given_name")
            .build()

        assertEquals("org.iso.18013.5.1.mDL", actualRequest.docType)
        assertEquals(1, actualRequest.requestedClaims.size)
        assertEquals(2, actualRequest.requestedClaims["org.iso.18013.5.1"]?.size)
    }

    @Test
    fun build_withUnknownClaim_throwsPVE34() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val exception: ProximityVerificationException = assertFailsWith {
            builder.forDocType("org.iso.18013.5.1.mDL")
                .requestClaim("org.iso.18013.5.1", "nonexistent_field")
                .build()
        }

        assertEquals("PVE34", exception.code.code)
    }

    @Test
    fun requestAllClaims_selectsAllClaims() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val actualRequest: VerificationRequest = builder.forDocType("org.iso.18013.5.1.mDL")
            .requestAllClaims()
            .build()

        val totalClaims: Int = actualRequest.requestedClaims.values.sumOf { it.size }
        assertEquals(5, totalClaims)
    }

    @Test
    fun requestMandatoryClaims_selectsOnlyMandatory() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val actualRequest: VerificationRequest = builder.forDocType("org.iso.18013.5.1.mDL")
            .requestMandatoryClaims()
            .build()

        val totalClaims: Int = actualRequest.requestedClaims.values.sumOf { it.size }
        assertEquals(4, totalClaims)
        val mdlClaims: Map<String, Boolean> = actualRequest.requestedClaims["org.iso.18013.5.1"]!!
        assertTrue(mdlClaims.containsKey("family_name"))
        assertTrue(mdlClaims.containsKey("given_name"))
        assertTrue(mdlClaims.containsKey("portrait"))
    }

    @Test
    fun requestNamespace_selectsClaimsInNamespace() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val actualRequest: VerificationRequest = builder.forDocType("org.iso.18013.5.1.mDL")
            .requestNamespace("org.iso.18013.5.1.IT")
            .build()

        assertEquals(1, actualRequest.requestedClaims.size)
        assertTrue(actualRequest.requestedClaims.containsKey("org.iso.18013.5.1.IT"))
        assertEquals(1, actualRequest.requestedClaims["org.iso.18013.5.1.IT"]?.size)
    }

    @Test
    fun requestClaim_withIntentToRetain_setsFlag() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val actualRequest: VerificationRequest = builder.forDocType("org.iso.18013.5.1.mDL")
            .requestClaim("org.iso.18013.5.1", "family_name", intentToRetain = true)
            .build()

        val intentToRetain: Boolean = actualRequest.requestedClaims["org.iso.18013.5.1"]!!["family_name"]!!
        assertTrue(intentToRetain)
    }

    @Test
    fun build_emptyRequest_succeeds() {
        val catalog: DocumentTypeCatalogImpl = createPopulatedCatalog()
        val builder = VerificationRequestBuilder(catalog)

        val actualRequest: VerificationRequest = builder.forDocType("org.iso.18013.5.1.mDL")
            .build()

        assertTrue(actualRequest.requestedClaims.isEmpty())
    }
}
