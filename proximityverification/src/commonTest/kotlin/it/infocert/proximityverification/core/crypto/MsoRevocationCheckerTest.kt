package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.RevocationState
import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.CachedStatusList
import it.infocert.proximityverification.status.model.Usability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.revocation.RevocationStatus
import org.multipaz.revocation.StatusList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class MsoRevocationCheckerTest {

    private val noOpLoggerDelegate: LoggerDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String): Unit = Unit
    }

    @BeforeTest
    fun setUpLogger(): Unit {
        LoggerConfig.delegateLogger = noOpLoggerDelegate
    }

    @AfterTest
    fun tearDownLogger(): Unit {
        LoggerConfig.delegateLogger = null
    }

    @Test
    fun nullRevocationStatusReturnsNotDetermined() = runTest {
        val result = MsoRevocationChecker.check(
            revocationStatus = null,
            isNetworkAvailable = { true },
            fetchPayload = { error("should not be called") }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
    }

    @Test
    fun offlineWithNonNullStatusReturnsNotDetermined() = runTest {
        var fetchCalled = false
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/status", certificate = null)
        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { false },
            fetchPayload = {
                fetchCalled = true
                ByteArray(0)
            }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
        assertFalse(fetchCalled, "fetchPayload must not be called when offline")
    }

    @Test
    fun unknownRevocationStatusReturnsNotDetermined() = runTest {
        val status = RevocationStatus.UnknownCbor(
            dataItem = buildCborMap { }
        )
        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { true },
            fetchPayload = { ByteArray(0) }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
    }

    @Test
    fun fetchPayloadThrowsGenericExceptionReturnsNotDetermined() = runTest {
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/status", certificate = null)
        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { true },
            fetchPayload = { throw Exception("network error") }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
    }

    @Test
    fun fetchPayloadThrowsCancellationExceptionIsRethrown() = runTest {
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/status", certificate = null)
        assertFailsWith<CancellationException> {
            MsoRevocationChecker.check(
                revocationStatus = status,
                isNetworkAvailable = { true },
                fetchPayload = { throw CancellationException("cancelled") }
            )
        }
    }

    @Test
    fun statusListRoutingPassesCorrectUriToFetchPayload() = runTest {
        val expectedUri = "https://example.com/status-list"
        var capturedUri: String? = null
        val status = RevocationStatus.StatusList(idx = 0, uri = expectedUri, certificate = null)
        MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { true },
            fetchPayload = { uri ->
                capturedUri = uri
                throw Exception("stop after capture")
            }
        )
        assertEquals(expectedUri, capturedUri)
    }

    @Test
    fun onlineSuccessTriggersCachePut() = runTest {
        var cachedUri: String? = null
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)
        try {
            MsoRevocationChecker.check(
                revocationStatus = status,
                isNetworkAvailable = { true },
                fetchPayload = { byteArrayOf(0x01, 0x02, 0x03) },     // bytes; multipaz will fail to parse
                isOfflineEnabled = { true },
                cacheLookup = { null },
                cachePut = { uri, _ -> cachedUri = uri }
            )
        } catch (_: Throwable) { /* parse will fail; we only assert side effect */ }
        assertEquals("https://example.com/sl", cachedUri)
    }

    @Test
    fun offlineCacheMissReturnsNotDetermined() = runTest {
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)
        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { false },
            fetchPayload = { error("must not be called offline") },
            cacheLookup = { null },
            cachePut = { _, _ -> }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
    }

    @Test
    fun onlineFetchFailureFallsBackToCacheLookup() = runTest {
        var lookupUri: String? = null
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)
        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { true },
            fetchPayload = { throw Exception("network error") },
            isOfflineEnabled = { true },
            cacheLookup = { uri -> lookupUri = uri; null },
            cachePut = { _, _ -> }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
        assertEquals("https://example.com/sl", lookupUri)
    }

    @Test
    fun identifierListRoutingPassesCorrectUriToFetchPayload() = runTest {
        val expectedUri = "https://example.com/identifier-list"
        var capturedUri: String? = null
        val status = RevocationStatus.IdentifierList(
            id = ByteString(byteArrayOf(0x01, 0x02, 0x03)),
            uri = expectedUri,
            certificate = null
        )
        MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { true },
            fetchPayload = { uri ->
                capturedUri = uri
                throw Exception("stop after capture")
            }
        )
        assertEquals(expectedUri, capturedUri)
    }

    @Test
    fun offlineCacheFreshViaUsabilityLambdaTriggersFreshMode() = runTest {
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)
        val cached = CachedStatusList(
            uri = "https://example.com/sl",
            format = CachedFormat.CWT,
            payloadBase64 = "AQID",
            iat = null,
            ttlSeconds = null,
            fetchedAtEpochMillis = 0L
        )
        var seenEntry: CachedStatusList? = null
        MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { false },
            isOfflineEnabled = { true },
            cacheLookup = { cached },
            cacheUsability = { entry, _ ->
                seenEntry = entry
                Usability.Fresh
            }
        )
        assertEquals(cached, seenEntry)
    }

    @Test
    fun offlineCacheStaleViaUsabilityLambdaInvokesLambda() = runTest {
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)
        val cached = CachedStatusList(
            uri = "https://example.com/sl",
            format = CachedFormat.CWT,
            payloadBase64 = "AQID",
            iat = null,
            ttlSeconds = null,
            fetchedAtEpochMillis = 0L
        )
        var lambdaCalls = 0
        MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { false },
            isOfflineEnabled = { true },
            cacheLookup = { cached },
            cacheUsability = { _, _ ->
                lambdaCalls += 1
                Usability.StaleByTtl
            }
        )
        assertEquals(1, lambdaCalls)
    }

    @Test
    fun offlineCacheMissDoesNotInvokeUsabilityLambda() = runTest {
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)
        var lambdaCalls = 0
        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { false },
            cacheLookup = { null },
            cacheUsability = { _, _ ->
                lambdaCalls += 1
                Usability.Fresh
            }
        )
        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
        assertEquals(0, lambdaCalls)
    }

    @Test
    fun offlineDisabledWithStatusListSkipsCacheLookupAndReturnsNotDetermined() = runTest {
        var lookupCalled = false
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)

        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { false },
            fetchPayload = { error("must not be called offline") },
            isOfflineEnabled = { false },
            cacheLookup = { lookupCalled = true; null },
            cachePut = { _, _ -> }
        )

        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
        assertFalse(lookupCalled, "cacheLookup must be skipped when offline is disabled")
    }

    @Test
    fun offlineDisabledOnlineSuccessSkipsCachePut() = runTest {
        var putCalled = false
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)

        try {
            MsoRevocationChecker.check(
                revocationStatus = status,
                isNetworkAvailable = { true },
                fetchPayload = { byteArrayOf(0x01, 0x02, 0x03) },     // bytes; multipaz parse will fail
                isOfflineEnabled = { false },
                cacheLookup = { null },
                cachePut = { _, _ -> putCalled = true }
            )
        } catch (_: Throwable) { /* parse will fail; we only assert side effect */ }

        assertFalse(putCalled, "cachePut must be skipped when offline is disabled")
    }

    @Test
    fun offlineDisabledOnlineFetchFailureReturnsNotDeterminedWithoutCacheLookup() = runTest {
        var lookupCalled = false
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)

        val result = MsoRevocationChecker.check(
            revocationStatus = status,
            isNetworkAvailable = { true },
            fetchPayload = { throw Exception("network error") },
            isOfflineEnabled = { false },
            cacheLookup = { lookupCalled = true; null },
            cachePut = { _, _ -> }
        )

        assertEquals(CheckStatus.NOT_DETERMINED, result.status)
        assertFalse(lookupCalled, "cacheLookup must be skipped when offline is disabled, even on online failure")
    }

    @Test
    fun offlineEnabledOnlineSuccessTriggersCachePut() = runTest {
        var cachedUri: String? = null
        val status = RevocationStatus.StatusList(idx = 0, uri = "https://example.com/sl", certificate = null)

        try {
            MsoRevocationChecker.check(
                revocationStatus = status,
                isNetworkAvailable = { true },
                fetchPayload = { byteArrayOf(0x01, 0x02, 0x03) },
                isOfflineEnabled = { true },
                cacheLookup = { null },
                cachePut = { uri, _ -> cachedUri = uri }
            )
        } catch (_: Throwable) { /* parse will fail; we only assert side effect */ }

        assertEquals("https://example.com/sl", cachedUri)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun buildSignedStatusList(idx: Int, bit: Int, bitsPerItem: Int = 2): Pair<ByteArray, X509Cert> {
        val privateKey = Crypto.createEcPrivateKey(EcCurve.P256)
        // Truncate to whole seconds so X509Cert.Builder does not emit a fractional-seconds
        // warning via android.util.Log (which is unmocked in the Android JVM unit-test runner).
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val cert = X509Cert.Builder(
            publicKey = privateKey.publicKey,
            signingKey = AsymmetricKey.anonymous(privateKey, privateKey.curve.defaultSigningAlgorithm),
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test SL"),
            issuer = X500Name.fromName("CN=Test SL"),
            validFrom = now - 1.hours,
            validUntil = now + 365.days,
        ).includeSubjectKeyIdentifier()
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()
        val signingKey = AsymmetricKey.X509CertifiedExplicit(
            privateKey = privateKey,
            certChain = X509CertChain(listOf(cert)),
            algorithm = privateKey.curve.defaultSigningAlgorithmFullySpecified,
        )
        val builder = StatusList.Builder(bitsPerItem)
        builder.addStatus(idx, bit)
        val jwt = builder.build().compress().serializeAsJwt(signingKey, "test")
        return jwt.encodeToByteArray() to cert
    }

    @Test
    fun bitZeroMapsToValidPassed() = runTest {
        val (bytes, cert) = buildSignedStatusList(idx = 5, bit = 0)
        val status = RevocationStatus.StatusList(idx = 5, uri = "https://example.com/sl", certificate = cert)
        val outcome = MsoRevocationChecker.check(status, isNetworkAvailable = { true }, fetchPayload = { bytes })
        assertEquals(CheckStatus.PASSED, outcome.status)
        assertEquals(RevocationState.VALID, (outcome.metadata as CheckMetadata.StatusListRevocation).state)
    }

    @Test
    fun bitOneMapsToRevokedFailed() = runTest {
        val (bytes, cert) = buildSignedStatusList(idx = 5, bit = 1)
        val status = RevocationStatus.StatusList(idx = 5, uri = "https://example.com/sl", certificate = cert)
        val outcome = MsoRevocationChecker.check(status, isNetworkAvailable = { true }, fetchPayload = { bytes })
        assertEquals(CheckStatus.FAILED, outcome.status)
        assertEquals(RevocationState.REVOKED, (outcome.metadata as CheckMetadata.StatusListRevocation).state)
    }

    @Test
    fun bitTwoMapsToSuspendedFailed() = runTest {
        val (bytes, cert) = buildSignedStatusList(idx = 5, bit = 2)
        val status = RevocationStatus.StatusList(idx = 5, uri = "https://example.com/sl", certificate = cert)
        val outcome = MsoRevocationChecker.check(status, isNetworkAvailable = { true }, fetchPayload = { bytes })
        assertEquals(CheckStatus.FAILED, outcome.status)
        assertEquals(RevocationState.SUSPENDED, (outcome.metadata as CheckMetadata.StatusListRevocation).state)
    }

    @Test
    fun bitThreeMapsToUnknownFailed() = runTest {
        val (bytes, cert) = buildSignedStatusList(idx = 5, bit = 3)
        val status = RevocationStatus.StatusList(idx = 5, uri = "https://example.com/sl", certificate = cert)
        val outcome = MsoRevocationChecker.check(status, isNetworkAvailable = { true }, fetchPayload = { bytes })
        assertEquals(CheckStatus.FAILED, outcome.status)
        assertEquals(RevocationState.UNKNOWN, (outcome.metadata as CheckMetadata.StatusListRevocation).state)
    }

    /**
     * Regression for the real IPZS mDL: the MSO `status_list` carries only `{idx, uri}`
     * (no embedded `certificate`), the Status List Token is a JWT with `bits: 4`, and the
     * referenced index is revoked. The signing key must be resolved from the token's `x5c`
     * (here injected via `resolveSigningKey`, mirroring `StatusListTrust`). Before the fix
     * multipaz's `require(publicKey != null || caValidated)` threw and the outcome silently
     * collapsed to NOT_DETERMINED, hiding the revocation.
     */
    @Test
    fun jwtStatusListWithoutEmbeddedCertResolvesKeyAndDetectsRevocationWithFourBits() = runTest {
        val (bytes, cert) = buildSignedStatusList(idx = 1992, bit = 1, bitsPerItem = 4)
        val status = RevocationStatus.StatusList(idx = 1992, uri = "https://example.com/sl", certificate = null)
        val outcome = MsoRevocationChecker.check(
            status,
            isNetworkAvailable = { true },
            fetchPayload = { bytes },
            resolveSigningKey = { _, _ -> cert.ecPublicKey },
        )
        assertEquals(CheckStatus.FAILED, outcome.status)
        assertEquals(RevocationState.REVOKED, (outcome.metadata as CheckMetadata.StatusListRevocation).state)
    }

    @Test
    fun jwtStatusListWithoutEmbeddedCertUnresolvableKeyReturnsNotDetermined() = runTest {
        val (bytes, _) = buildSignedStatusList(idx = 1992, bit = 1, bitsPerItem = 4)
        val status = RevocationStatus.StatusList(idx = 1992, uri = "https://example.com/sl", certificate = null)
        val outcome = MsoRevocationChecker.check(
            status,
            isNetworkAvailable = { true },
            fetchPayload = { bytes },
            resolveSigningKey = { _, _ -> null },     // chain not anchored to any known trust point
        )
        assertEquals(CheckStatus.NOT_DETERMINED, outcome.status)
        assertNull(outcome.metadata)
    }

    @Test
    fun jwtStatusListWithoutEmbeddedCertResolvesKeyAndReportsValid() = runTest {
        val (bytes, cert) = buildSignedStatusList(idx = 1992, bit = 0, bitsPerItem = 4)
        val status = RevocationStatus.StatusList(idx = 1992, uri = "https://example.com/sl", certificate = null)
        val outcome = MsoRevocationChecker.check(
            status,
            isNetworkAvailable = { true },
            fetchPayload = { bytes },
            resolveSigningKey = { _, _ -> cert.ecPublicKey },
        )
        assertEquals(CheckStatus.PASSED, outcome.status)
        assertEquals(RevocationState.VALID, (outcome.metadata as CheckMetadata.StatusListRevocation).state)
    }

    @Test
    fun nullRevocationStatusReturnsNotDeterminedWithoutMetadata() = runTest {
        val outcome = MsoRevocationChecker.check(revocationStatus = null)
        assertEquals(CheckStatus.NOT_DETERMINED, outcome.status)
        assertNull(outcome.metadata)
    }
}
