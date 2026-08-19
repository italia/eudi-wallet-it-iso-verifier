package it.infocert.proximityverification.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusListUriFilterTest {

    @Test
    fun extractCredentialType_basicMatch_returnsCredentialType() {
        val result = StatusListUriFilter.extractCredentialType(
            "https://issuer.example/statuslists/mDL/0"
        )
        assertEquals("mDL", result)
    }

    @Test
    fun extractCredentialType_statuslistsNotFirstSegment_returnsNull() {
        assertNull(
            StatusListUriFilter.extractCredentialType("https://issuer.example/api/statuslists/mDL/0")
        )
    }

    @Test
    fun extractCredentialType_statuslistsAtThirdSegment_returnsNull() {
        assertNull(
            StatusListUriFilter.extractCredentialType("https://issuer.example/v1/api/statuslists/mDL/0")
        )
    }

    @Test
    fun extractCredentialType_emptyCredentialTypeSegment_returnsNull() {
        assertNull(
            StatusListUriFilter.extractCredentialType("https://issuer.example/statuslists//0")
        )
    }

    @Test
    fun extractCredentialType_onlyStatuslistsSegment_returnsNull() {
        assertNull(
            StatusListUriFilter.extractCredentialType("https://issuer.example/statuslists")
        )
    }

    @Test
    fun extractCredentialType_twoSegmentsNoPartition_returnsCredentialType() {
        assertEquals(
            "mDL",
            StatusListUriFilter.extractCredentialType("https://issuer.example/statuslists/mDL")
        )
    }

    @Test
    fun extractCredentialType_caseSensitiveStatuslistsKeyword_returnsNull() {
        assertNull(
            StatusListUriFilter.extractCredentialType("https://issuer.example/StatusLists/mDL/0")
        )
        assertNull(
            StatusListUriFilter.extractCredentialType("https://issuer.example/STATUSLISTS/mDL/0")
        )
    }

    @Test
    fun extractCredentialType_withQueryString_returnsCredentialType() {
        assertEquals(
            "mDL",
            StatusListUriFilter.extractCredentialType(
                "https://issuer.example/statuslists/mDL/0?foo=bar"
            )
        )
    }

    @Test
    fun extractCredentialType_withTrailingSlash_returnsCredentialType() {
        assertEquals(
            "mDL",
            StatusListUriFilter.extractCredentialType(
                "https://issuer.example/statuslists/mDL/0/"
            )
        )
    }

    @Test
    fun extractCredentialType_hostWithExplicitPort_returnsCredentialType() {
        assertEquals(
            "mDL",
            StatusListUriFilter.extractCredentialType(
                "https://issuer.example:8443/statuslists/mDL/0"
            )
        )
    }

    @Test
    fun extractCredentialType_httpScheme_returnsCredentialType() {
        assertEquals(
            "mDL",
            StatusListUriFilter.extractCredentialType("http://issuer.example/statuslists/mDL/0")
        )
    }

    @Test
    fun extractCredentialType_unsupportedScheme_returnsNull() {
        assertNull(
            StatusListUriFilter.extractCredentialType("ftp://issuer.example/statuslists/mDL/0")
        )
        assertNull(
            StatusListUriFilter.extractCredentialType("file:///statuslists/mDL/0")
        )
    }

    @Test
    fun extractCredentialType_relativeUri_returnsNull() {
        assertNull(StatusListUriFilter.extractCredentialType("/statuslists/mDL/0"))
        assertNull(StatusListUriFilter.extractCredentialType("statuslists/mDL/0"))
    }

    @Test
    fun extractCredentialType_malformedUri_returnsNull() {
        assertNull(StatusListUriFilter.extractCredentialType(""))
        assertNull(StatusListUriFilter.extractCredentialType("not-a-url"))
    }

    @Test
    fun extractCredentialType_urlEncodedCredentialType_returnsDecoded() {
        assertEquals(
            "education degree",
            StatusListUriFilter.extractCredentialType(
                "https://issuer.example/statuslists/education%20degree/0"
            )
        )
    }

    @Test
    fun extractCredentialType_realIpzsExamples_returnExpectedTypes() {
        val cases = mapOf(
            "https://pre.issuer.wallet.ipzs.it/statuslists/EuropeanDisabilityCard/0" to "EuropeanDisabilityCard",
            "https://pre.issuer.wallet.ipzs.it/statuslists/EuropeanHealthInsuranceCard/0" to "EuropeanHealthInsuranceCard",
            "https://pre.issuer.wallet.ipzs.it/statuslists/education_degree/0" to "education_degree",
            "https://pre.issuer.wallet.ipzs.it/statuslists/education_enrollment/0" to "education_enrollment",
            "https://pre.issuer.wallet.ipzs.it/statuslists/mDL/0" to "mDL",
            "https://pre.issuer.wallet.ipzs.it/statuslists/pid/0" to "pid",
            "https://pre.issuer.wallet.ipzs.it/statuslists/residency/0" to "residency"
        )
        cases.forEach { (uri, expected) ->
            assertEquals(expected, StatusListUriFilter.extractCredentialType(uri), "uri=$uri")
        }
    }

    @Test
    fun retain_emptyExpected_returnsAllAsKept() {
        val uris = listOf(
            "https://issuer.example/statuslists/mDL/0",
            "https://issuer.example/api/v1/sl/0"
        )
        val outcome = StatusListUriFilter.retain(uris, emptySet())
        assertEquals(uris, outcome.kept)
        assertEquals(emptyList<String>(), outcome.droppedAsIrrelevant)
    }

    @Test
    fun retain_mixOfPatternAndNonPatternAndIrrelevant_partitionsCorrectly() {
        val uris = listOf(
            "https://issuer.example/statuslists/mDL/0",
            "https://issuer.example/statuslists/pid/0",
            "https://issuer.example/api/v1/statuslists/mDL/0",
            "https://issuer.example/legacy-sl/foo/0"
        )
        val outcome = StatusListUriFilter.retain(uris, setOf("mDL"))

        assertEquals(
            listOf(
                "https://issuer.example/statuslists/mDL/0",
                "https://issuer.example/api/v1/statuslists/mDL/0",
                "https://issuer.example/legacy-sl/foo/0"
            ),
            outcome.kept
        )
        assertEquals(
            listOf("https://issuer.example/statuslists/pid/0"),
            outcome.droppedAsIrrelevant
        )
    }

    @Test
    fun retain_allMatchPatternAndAllIrrelevant_keepsZero() {
        val uris = listOf(
            "https://issuer.example/statuslists/EHIC/0",
            "https://issuer.example/statuslists/pid/0"
        )
        val outcome = StatusListUriFilter.retain(uris, setOf("mDL"))
        assertEquals(emptyList<String>(), outcome.kept)
        assertEquals(uris, outcome.droppedAsIrrelevant)
    }
}
