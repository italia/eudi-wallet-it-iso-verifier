package it.infocert.proximityverification.crl

import it.infocert.proximityverification.core.crypto.LocalStorage
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509Crl
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Shared helpers for CRL tests. Mirrors `StatusListTestSupport`:
 * provides an in-memory `LocalStorage`, plus convenience helpers that
 * build real signed `X509Cert` and `X509Crl` instances via multipaz
 * builders so tests exercise the same code paths as production.
 */
internal object CrlTestSupport {

    fun inMemoryStorage(): LocalStorage = object : LocalStorage {
        private val map: MutableMap<String, String> = mutableMapOf()
        override suspend fun saveElement(key: String, value: String) {
            map[key] = value
        }
        override suspend fun getElement(key: String): String? = map[key]
        override suspend fun clear() {
            map.clear()
        }
        override suspend fun clearElement(logicalKey: String) {
            map.remove(logicalKey)
        }
    }

    /**
     * Builds a self-signed X.509 cert deterministic enough for tests.
     * Uses an ephemeral P-256 key (regenerated every call — the cert
     * bytes therefore differ per call, but [computeCertKey] only depends
     * on `issuer` + `serialNumber`, which we control).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun buildTestCert(
        serial: Long = 0x123L,
        issuer: String = "CN=Test Issuer",
        subject: String = "CN=Test Subject",
        notBeforeEpochSeconds: Long = 1_700_000_000L,
        notAfterEpochSeconds: Long = 1_900_000_000L
    ): X509Cert {
        val key: AsymmetricKey = AsymmetricKey.ephemeral()
        return X509Cert.Builder(
            publicKey = key.publicKey,
            signingKey = key,
            serialNumber = ASN1Integer(serial),
            subject = X500Name.fromName(subject),
            issuer = X500Name.fromName(issuer),
            validFrom = Instant.fromEpochSeconds(notBeforeEpochSeconds),
            validUntil = Instant.fromEpochSeconds(notAfterEpochSeconds)
        ).build()
    }

    /**
     * Round-trips [cert] through encode → parse to produce a structurally
     * identical (but distinct) instance — useful for verifying that
     * `computeCertKey` is stable across encoder pipelines.
     */
    fun reparse(cert: X509Cert): X509Cert = X509Cert(ByteString(cert.encoded.toByteArray()))

    /**
     * Builds a signed CRL DER blob with the requested `thisUpdate` /
     * `nextUpdate` and revoked-serial entries. Returns the encoded bytes
     * directly so tests can feed them straight into `CrlMetaExtractor` or
     * `CrlStore.put`.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun buildTestCrlBytes(
        issuer: String = "CN=Test CRL Issuer",
        thisUpdate: Instant = Instant.fromEpochSeconds(1_700_000_000L),
        nextUpdate: Instant? = Instant.fromEpochSeconds(1_700_000_000L + 86_400L),
        revokedSerials: List<Long> = emptyList()
    ): ByteArray {
        val key: AsymmetricKey = AsymmetricKey.ephemeral()
        val builder = X509Crl.Builder(
            signingKey = key,
            issuer = X500Name.fromName(issuer),
            thisUpdate = thisUpdate,
            nextUpdate = nextUpdate
        )
        revokedSerials.forEach { serial ->
            builder.addRevoked(ASN1Integer(serial), thisUpdate)
        }
        val crl: X509Crl = builder.build()
        return crl.encoded.toByteArray()
    }
}
