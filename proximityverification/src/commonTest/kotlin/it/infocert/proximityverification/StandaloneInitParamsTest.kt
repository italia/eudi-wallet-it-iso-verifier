package it.infocert.proximityverification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StandaloneInitParamsTest {

    @Test
    fun defaultParamsHaveNullTrustAnchorsAndNullMetadata() {
        val params = StandaloneRelyingPartyParams()
        assertNull(params.trustAnchors)
        assertNull(params.issuerMetadata)
    }

    @Test
    fun fromJsonMetadataHoldsJson() {
        val json = """{"credential_configurations_supported":{}}"""
        val meta = StandaloneIssuerMetadata.FromJson(json)
        assertEquals(json, meta.json)
    }

    @Test
    fun fromUrlMetadataHoldsUrl() {
        val url = "https://issuer.example.com"
        val meta = StandaloneIssuerMetadata.FromUrl(url)
        assertEquals(url, meta.url)
    }

    @Test
    fun paramsWithTrustAnchorsAreStored() {
        val anchors = listOf("-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----")
        val params = StandaloneRelyingPartyParams(trustAnchors = anchors)
        assertEquals(anchors, params.trustAnchors)
    }
}
