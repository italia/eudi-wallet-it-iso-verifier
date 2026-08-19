package it.infocert.proximityverification.crl

import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.util.toBase64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IssuerCertChainExtractorTest {

    private val noOpLogger: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    @BeforeTest fun setUp() { LoggerConfig.delegateLogger = noOpLogger }
    @AfterTest fun tearDown() { LoggerConfig.delegateLogger = null }

    private fun metadata(jwks: JsonObject?): IssuerResolvedMetadata =
        IssuerResolvedMetadata(
            entityId = "https://issuer.example",
            statusListAggregationEndpoint = null,
            credentialConfigurations = emptyList(),
            jwks = jwks
        )

    private fun buildJwks(x5c: JsonArray?): JsonObject {
        val key0Pairs: MutableMap<String, kotlinx.serialization.json.JsonElement> = mutableMapOf()
        if (x5c != null) key0Pairs["x5c"] = x5c
        val key0: JsonObject = JsonObject(key0Pairs)
        val keysArray: JsonArray = JsonArray(listOf(key0))
        return JsonObject(mapOf("keys" to keysArray))
    }

    @Test
    fun returnsAllCertsFromX5cOfSizeN() = runTest {
        val a = CrlTestSupport.buildTestCert(serial = 1L, subject = "CN=Leaf")
        val b = CrlTestSupport.buildTestCert(serial = 2L, subject = "CN=Intermediate")
        val c = CrlTestSupport.buildTestCert(serial = 3L, subject = "CN=Root")
        val x5c: JsonArray = JsonArray(listOf(
            JsonPrimitive(a.encoded.toByteArray().toBase64()),
            JsonPrimitive(b.encoded.toByteArray().toBase64()),
            JsonPrimitive(c.encoded.toByteArray().toBase64())
        ))
        val md = metadata(buildJwks(x5c))

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(3, result.certificates.size)
        assertEquals(0, result.parseFailureCount)
        val expectedSerials = setOf(1L, 2L, 3L)
        val actualSerials = result.certificates
            .map { it.serialNumber.toLong() }
            .toSet()
        assertEquals(expectedSerials, actualSerials)
    }

    @Test
    fun returnsEmptyListWhenJwksIsNull() = runTest {
        val md = metadata(jwks = null)

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(0, result.certificates.size)
        assertEquals(0, result.parseFailureCount)
    }

    @Test
    fun returnsEmptyListWhenKeysMissing() = runTest {
        val md = metadata(JsonObject(emptyMap()))

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(0, result.certificates.size)
        assertEquals(0, result.parseFailureCount)
    }

    @Test
    fun returnsEmptyListWhenX5cMissing() = runTest {
        val md = metadata(buildJwks(x5c = null))

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(0, result.certificates.size)
        assertEquals(0, result.parseFailureCount)
    }

    @Test
    fun returnsSingleCertWhenX5cHasOneElement() = runTest {
        val a = CrlTestSupport.buildTestCert(serial = 1L)
        val x5c: JsonArray = JsonArray(listOf(
            JsonPrimitive(a.encoded.toByteArray().toBase64())
        ))
        val md = metadata(buildJwks(x5c))

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(1, result.certificates.size)
        assertEquals(0, result.parseFailureCount)
        // Lock leaf inclusion explicitly: the single cert in x5c[0] is now
        // returned (it used to be dropped as the leaf).
        assertEquals(1L, result.certificates.first().serialNumber.toLong())
    }

    @Test
    fun countsParseFailures() = runTest {
        val a = CrlTestSupport.buildTestCert(serial = 1L)
        val c = CrlTestSupport.buildTestCert(serial = 3L)
        val x5c: JsonArray = JsonArray(listOf(
            JsonPrimitive(a.encoded.toByteArray().toBase64()),
            JsonPrimitive("%%not-base64%%"),
            JsonPrimitive(c.encoded.toByteArray().toBase64())
        ))
        val md = metadata(buildJwks(x5c))

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(2, result.certificates.size)
        assertEquals(1, result.parseFailureCount)
        // Leaf (serial 1L) is now included alongside the root (serial 3L);
        // the garbage middle entry is the only one that fails to parse.
        val survivingSerials = result.certificates
            .map { it.serialNumber.toLong() }
            .toSet()
        assertEquals(setOf(1L, 3L), survivingSerials)
    }

    @Test
    fun extractIncludesLeafCertificate() = runTest {
        // Per U01-SC-15: an x5c chain of [leaf, intermediate, root] now
        // surfaces all three certs, and the leaf is at position [0].
        val leaf = CrlTestSupport.buildTestCert(serial = 1L, subject = "CN=Leaf")
        val intermediate = CrlTestSupport.buildTestCert(serial = 2L, subject = "CN=Intermediate")
        val root = CrlTestSupport.buildTestCert(serial = 3L, subject = "CN=Root")
        val x5c: JsonArray = JsonArray(listOf(
            JsonPrimitive(leaf.encoded.toByteArray().toBase64()),
            JsonPrimitive(intermediate.encoded.toByteArray().toBase64()),
            JsonPrimitive(root.encoded.toByteArray().toBase64())
        ))
        val md = metadata(buildJwks(x5c))

        val result = IssuerCertChainExtractor.extract(md)

        assertEquals(3, result.certificates.size)
        assertEquals(0, result.parseFailureCount)
        // First element decodes to the leaf — original x5c order preserved.
        assertEquals(1L, result.certificates.first().serialNumber.toLong())
    }
}
