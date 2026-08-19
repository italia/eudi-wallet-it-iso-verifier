package it.infocert.proximityverification.core.catalog

import it.infocert.proximityverification.VerificationRequest
import org.multipaz.documenttype.DocumentAttributeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CatalogBridgeTest {

    @Test
    fun toDocumentType_producesValidDocumentType() {
        val inputConfig = CredentialConfiguration(
            docType = "org.iso.18013.5.1.mDL",
            display = listOf(CredentialDisplay("Driving License", "en")),
            claims = listOf(
                ClaimDescriptor(ns = "org.iso.18013.5.1", identifier = "family_name", isMandatory = true,
                    display = listOf(ClaimDisplay("Family Name", "en"))),
                ClaimDescriptor(ns = "org.iso.18013.5.1", identifier = "given_name", isMandatory = true,
                    display = listOf(ClaimDisplay("Given Names", "en")))
            )
        )

        val actualDocType = CatalogBridge.toDocumentType(inputConfig)

        assertEquals("Driving License", actualDocType.displayName)
        assertNotNull(actualDocType.mdocDocumentType)
        assertEquals("org.iso.18013.5.1.mDL", actualDocType.mdocDocumentType!!.docType)
    }

    @Test
    fun toCannedRequest_producesValidCannedRequest() {
        val inputRequest = VerificationRequest(
            docType = "org.iso.18013.5.1.mDL",
            requestedClaims = mapOf(
                "org.iso.18013.5.1" to mapOf(
                    "family_name" to false,
                    "given_name" to true
                )
            )
        )

        val actualCannedRequest = CatalogBridge.toCannedRequest(inputRequest)

        assertEquals("dynamic_request", actualCannedRequest.id)
        assertEquals("org.iso.18013.5.1.mDL", actualCannedRequest.displayName)
    }

    @Test
    fun toDocumentType_noDisplayName_usesDocType() {
        val inputConfig = CredentialConfiguration(
            docType = "some.doc.type",
            display = emptyList(),
            claims = emptyList()
        )

        val actualDocType = CatalogBridge.toDocumentType(inputConfig)

        assertEquals("some.doc.type", actualDocType.displayName)
    }

    @Test
    fun toDocumentType_portraitWithStringDataType_resolvesToPictureViaKnownElementTypes() {
        val inputConfig = CredentialConfiguration(
            docType = "org.iso.18013.5.1.mDL",
            display = listOf(CredentialDisplay("Driving License", "en")),
            claims = listOf(
                ClaimDescriptor(
                    ns = "org.iso.18013.5.1",
                    identifier = "portrait",
                    isMandatory = true,
                    dataType = ClaimDescriptor.DATA_TYPE_STRING,
                    display = listOf(ClaimDisplay("Portrait", "en"))
                )
            )
        )

        val actualDocType = CatalogBridge.toDocumentType(inputConfig)

        val mdoc = actualDocType.mdocDocumentType
        assertNotNull(mdoc)
        val portraitElement = mdoc.namespaces["org.iso.18013.5.1"]?.dataElements?.get("portrait")
        assertNotNull(portraitElement)
        assertEquals(DocumentAttributeType.Picture, portraitElement.attribute.type)
    }

    @Test
    fun toCannedRequest_multipleNamespaces_succeeds() {
        val inputRequest = VerificationRequest(
            docType = "org.iso.18013.5.1.mDL",
            requestedClaims = mapOf(
                "org.iso.18013.5.1" to mapOf(
                    "family_name" to false
                ),
                "org.iso.18013.5.1.IT" to mapOf(
                    "sub" to false
                )
            )
        )

        val actualCannedRequest = CatalogBridge.toCannedRequest(inputRequest)

        assertNotNull(actualCannedRequest)
    }
}
