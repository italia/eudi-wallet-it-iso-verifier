package it.infocert.proximityverification.status

import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.StatusListPayload
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.util.toBase64Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreshnessExtractorTest {

    @Test
    fun extractFromJwt_returnsIatAndTtl() {
        val payload: String = """{"iat":1700000000,"ttl":3600}"""
        val jwt: String = listOf(
            "{}".encodeToByteArray().toBase64Url(),
            payload.encodeToByteArray().toBase64Url(),
            "sig".encodeToByteArray().toBase64Url()
        ).joinToString(".")
        val sp = StatusListPayload(jwt.encodeToByteArray(), contentType = "application/statuslist+jwt")

        val meta = FreshnessExtractor.extract(sp)

        assertEquals(CachedFormat.JWT, meta.format)
        assertEquals(1_700_000_000L, meta.iat)
        assertEquals(3600L, meta.ttlSeconds)
    }

    @Test
    fun extractFromJwt_missingClaimsAreNull() {
        val payload: String = "{}"
        val jwt: String = listOf(
            "{}".encodeToByteArray().toBase64Url(),
            payload.encodeToByteArray().toBase64Url(),
            "sig".encodeToByteArray().toBase64Url()
        ).joinToString(".")
        val sp = StatusListPayload(jwt.encodeToByteArray(), contentType = "application/statuslist+jwt")

        val meta = FreshnessExtractor.extract(sp)

        assertEquals(CachedFormat.JWT, meta.format)
        assertNull(meta.iat)
        assertNull(meta.ttlSeconds)
    }

    @Test
    fun extractFromCwt_returnsIatAndTtl() {
        val claims = buildCborMap {
            put(6L, 1_700_000_000L)        // iat (RFC 8392)
            put(65534L, 3600L)             // ttl (Token Status List draft-13)
        }
        val sign1: ByteArray = Cbor.encode(
            buildCborArray {
                add(byteArrayOf())                 // protected (empty)
                addCborMap { }                     // unprotected (empty map)
                add(Cbor.encode(claims))           // payload bstr (CBOR-encoded claims)
                add(byteArrayOf(0x00))             // signature placeholder
            }
        )
        val sp = StatusListPayload(sign1, contentType = "application/statuslist+cwt")

        val meta = FreshnessExtractor.extract(sp)

        assertEquals(CachedFormat.CWT, meta.format)
        assertEquals(1_700_000_000L, meta.iat)
        assertEquals(3600L, meta.ttlSeconds)
    }
}
