@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package it.infocert.proximityverification

import io.ktor.utils.io.core.toByteArray
import it.infocert.proximityverification.core.crypto.DefaultHardwareKeyTagGenerator
import it.infocert.proximityverification.core.crypto.ExtendedSecureAreaWithTTL
import it.infocert.proximityverification.core.crypto.LocalSecureAreaFactory
import it.infocert.proximityverification.core.crypto.TokenManager
import it.infocert.proximityverification.core.crypto.generateAssertion
import it.infocert.proximityverification.core.crypto.provideLocalStorage
import it.infocert.proximityverification.core.crypto.generateSelfSignedCert
import it.infocert.proximityverification.core.crypto.withCertChain
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.models.KeyCreationResult
import it.infocert.proximityverification.core.models.KeyCreationStatus
import it.infocert.proximityverification.core.models.LocalKeystoreKeyInfo
import it.infocert.proximityverification.core.models.ReaderCertificateInfo
import it.infocert.proximityverification.core.models.initialization.RelyingPartyProviderInfo
import org.multipaz.mdoc.request.buildDeviceRequest
import it.infocert.proximityverification.core.trust.CSRCreator
import it.infocert.proximityverification.core.trust.ClientData
import it.infocert.proximityverification.core.trust.Jwk
import it.infocert.proximityverification.core.trust.JwkUtils
import it.infocert.proximityverification.core.trust.encodeBase64UrlNoPadding
import it.infocert.proximityverification.core.trust.produceBArrayFromClientData
import it.infocert.proximityverification.core.trust.produceClientData
import it.infocert.proximityverification.core.trust.produceClientDataHash
import it.infocert.proximityverification.core.trust.produceHardwareSignature
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import kotlinx.io.bytestring.encodeToByteString
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.RelyingPartyApiImpl
import it.infocert.proximityverification.network.model.ApiResult
import it.infocert.proximityverification.network.model.common.AccessCertificateResponse
import it.infocert.proximityverification.network.model.common.KeyBindingResponse
import it.infocert.proximityverification.network.model.common.WellKnownConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseKey
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcSignature
import org.multipaz.crypto.SignatureVerificationException
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.documenttype.SingleDocumentCannedRequest
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.SecureArea
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * Builds the OpenID Federation entity configuration URL from the base entity URL
 * carried by [RelyingPartyProviderInfo.federationEntityUrl].
 *
 * Centralised here so that the existing well-known fetch and the new federation
 * registry resolver derive the URL identically.
 */
internal fun federationEntityConfigUrl(federationEntityUrl: String): String {
    val base: String = federationEntityUrl.trimEnd('/')
    return "$base/.well-known/openid-federation"
}

@OptIn(ExperimentalTime::class)
internal object Utils {
    private const val TAG = "Utils"

    fun generateEncodedSessionTranscript(
        encodedDeviceEngagement: ByteArray,
        handover: DataItem,
        eReaderKey: EcPublicKey
    ): ByteArray {
        val mn = "generateEncodedSessionTranscript"

        Logger.debug(TAG) { "$mn - Generating session transcript." }

        val encodedEReaderKey = Cbor.encode(eReaderKey.toCoseKey().toDataItem())
        val transcript = Cbor.encode(
            buildCborArray {
                add(Tagged(24, Bstr(encodedDeviceEngagement)))
                add(Tagged(24, Bstr(encodedEReaderKey)))
                add(handover)
            }
        )

        Logger.info(TAG) { "$mn - Session transcript generated (size=${transcript.size} bytes)." }
        return transcript
    }

    data class ReaderMaterial(
        val certChain: X509CertChain? = null
    )

    suspend fun createReaderAuthKey(
        certChain: X509CertChain
    ): AsymmetricKey.X509Compatible {
        val secureArea: SecureArea = LocalSecureAreaFactory.createLocalSecureAreaProvider().get()
        val keyAlias: String = getOrCreateHardwareKeyTag(Constants.INIT_KEY_ALIAS)
        val keyInfo: KeyInfo = secureArea.getKeyInfo(keyAlias)
        return AsymmetricKey.X509CertifiedSecureAreaBased(
            certChain = certChain,
            secureArea = secureArea,
            keyInfo = keyInfo,
        )
    }

    suspend fun generateEncodedDeviceRequest(
        requests: List<DocumentCannedRequest>,
        sessionTranscript: DataItem,
        readerKey: AsymmetricKey.X509Compatible
    ): ByteArray {
        val deviceRequest = buildDeviceRequest(sessionTranscript = sessionTranscript) {
            for (request in requests) {
                val singleRequest: SingleDocumentCannedRequest = request as? SingleDocumentCannedRequest
                    ?: continue
                val mdocRequest = singleRequest.mdocRequest ?: continue
                val itemsToRequest = mutableMapOf<String, MutableMap<String, Boolean>>()
                for (ns in mdocRequest.namespacesToRequest) {
                    for ((de, intentToRetain) in ns.dataElementsToRequest) {
                        itemsToRequest.getOrPut(ns.namespace) { mutableMapOf() }
                            .put(de.attribute.identifier, intentToRetain)
                    }
                }
                addDocRequest(
                    docType = mdocRequest.docType,
                    nameSpaces = itemsToRequest,
                    docRequestInfo = null,
                    readerKey = readerKey,
                )
            }
        }
        return Cbor.encode(deviceRequest.toDataItem())
    }

    private suspend fun getOrCreateHardwareKeyTag(logicalKey: String): String {
        val storage = provideLocalStorage()
        storage.getElement(logicalKey)?.let { return it }
        val tag = DefaultHardwareKeyTagGenerator.generateRandomTag()
        storage.saveElement(logicalKey, tag)
        return tag
    }

    /**
     * Re-reads the reader/access certificate from the SecureArea (chain persisted at init)
     * and maps it to [ReaderCertificateInfo]. Read-only and null-safe: returns null when no
     * key/cert is registered; never throws (cancellation aside).
     */
    internal suspend fun readReaderCertificateInfo(): ReaderCertificateInfo? {
        val mn = "readReaderCertificateInfo"
        return try {
            val storage = provideLocalStorage()
            val deviceKeyTag = storage.getElement(Constants.INIT_KEY_ALIAS)
            if (deviceKeyTag == null) {
                Logger.debug(TAG) { "$mn - no init key alias stored" }
                return null
            }
            val sa = LocalSecureAreaFactory.createLocalSecureAreaProvider().get()
            val keyInfo = sa.getKeyInfo(deviceKeyTag) as? LocalKeystoreKeyInfo
            val readerCert = keyInfo?.certChain?.firstOrNull()
            if (readerCert == null) {
                Logger.debug(TAG) { "$mn - no cert chain for alias" }
                return null
            }
            ReaderCertificateInfo.from(readerCert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG) { "$mn - failed: ${e.message}" }
            null
        }
    }

    suspend fun initAndCreateKey(
        signatureAlgorithm: Algorithm,
        providerInfo: RelyingPartyProviderInfo,
        ttlMillis: Long = 24 * 60 * 60 * 1000L, // default: 24h
    ): ReaderMaterial {
        val mn = "initAndCreateKey"

        Logger.info(TAG) { "$mn - Start initAndCreateKey" }
        Logger.debug(TAG) { "$mn - Signature algorithm: $signatureAlgorithm, TTL: $ttlMillis ms" }

        val storage = provideLocalStorage()

        val sa = LocalSecureAreaFactory.createLocalSecureAreaProvider().get()

        val deviceKeyTag = getOrCreateHardwareKeyTag(Constants.INIT_KEY_ALIAS)

        val createKeySettings = CreateKeySettings(
            signatureAlgorithm,
            deviceKeyTag.encodeToByteString()
        )
        val isDeviceOnline = NativeUtils.isNetworkAvailable()
        val initResult = (sa as? ExtendedSecureAreaWithTTL)?.createKeyWithTtl(
            alias = deviceKeyTag,
            createKeySettings = createKeySettings,
            ttlMillis = ttlMillis,
            isOnline = isDeviceOnline
        ) ?: error("SecureArea does not support TTL")

        when (initResult.status) {
            KeyCreationStatus.CREATED_NEW -> Logger.info(TAG) { "$mn - Created new key for alias $deviceKeyTag" }
            KeyCreationStatus.REUSED_EXISTING -> Logger.info(TAG) { "$mn - Reused existing key for alias $deviceKeyTag" }
        }

        var certChain: X509CertChain? = null

        val isRegCompleted =
            storage.getElement(Constants.REGISTRATION_COMPLETED).equals("true", ignoreCase = true)

        if (initResult.status == KeyCreationStatus.CREATED_NEW ||
            (initResult.status == KeyCreationStatus.REUSED_EXISTING && !isRegCompleted)
        ) {
            storage.saveElement(key = Constants.REGISTRATION_COMPLETED, false.toString())
            
            if (NativeUtils.isNetworkAvailable()) {
                Logger.info(TAG) { "$mn - Network is available, registering reader..." }
                certChain = registerReader(providerInfo, initResult)
                Logger.info(TAG) { "$mn - Init completed with success!" }
            } else {
                // TODO: New key created, but no network available to register the reader so key must be discharged
                Logger.warn(TAG) { "$mn - Network is not available, skipping online registration." }
                val keyInfo: LocalKeystoreKeyInfo =
                    sa.getKeyInfo(deviceKeyTag) as LocalKeystoreKeyInfo
                certChain = X509CertChain(keyInfo.certChain!!)
                Logger.info(TAG) { "$mn - Offline init completed. Chain length: ${certChain.certificates.size}" }
            }

        } else {
            val keyInfo: LocalKeystoreKeyInfo =
                sa.getKeyInfo(deviceKeyTag) as LocalKeystoreKeyInfo
            certChain = X509CertChain(keyInfo.certChain!!)
            Logger.info(TAG) { "$mn - Init completed with success. Chain length: ${certChain.certificates.size}" }
        }

        return ReaderMaterial(
            certChain = certChain
        )
    }

    suspend fun initAndCreateKeyStandalone(): ReaderMaterial {
        val mn = "initAndCreateKeyStandalone"
        Logger.info(TAG) { "$mn - Creating standalone reader key" }
        val sa = LocalSecureAreaFactory.createLocalSecureAreaProvider().get()
        val standaloneKeyTag = getOrCreateHardwareKeyTag(Constants.STANDALONE_INIT_KEY_ALIAS)
        val createKeySettings = CreateKeySettings(
            Algorithm.ESP256,
            standaloneKeyTag.encodeToByteString()
        )
        try {
            sa.getKeyInfo(standaloneKeyTag)
            Logger.info(TAG) { "$mn - Reusing existing standalone key" }
        } catch (_: Exception) {
            sa.createKey(standaloneKeyTag, createKeySettings)
            Logger.info(TAG) { "$mn - Created new standalone key" }
        }
        val selfSignedCert = generateSelfSignedCert(
            secureArea = sa,
            alias = standaloneKeyTag,
            subject = X500Name.fromName("CN=proximity-verification-standalone")
        )
        val certChain = X509CertChain(listOf(selfSignedCert))
        sa.withCertChain(standaloneKeyTag, certChain)
        Logger.info(TAG) { "$mn - Standalone key ready. Chain length: ${certChain.certificates.size}" }
        return ReaderMaterial(certChain = certChain)
    }

    @Throws(ProximityVerificationException::class, CancellationException::class)
    suspend fun populateTrustManager(trustManager: TrustManager, trustAnchors: List<String>) {
        try {
            for (pem in trustAnchors) {
                val cert = X509Cert.fromPem(pem)
                trustManager.addX509Cert(cert, TrustMetadata(displayName = cert.subject.name))
            }
        } catch (e: Exception) {
            throw ProximityVerificationException(
                "Failed to load trust points", e,
                code = ProximityVerificationExceptionCode.PVE03()
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)
    private suspend fun registerReader(
        providerInfo: RelyingPartyProviderInfo,
        initResult: KeyCreationResult,
    ):X509CertChain{
        val mn = "registerReader"
        Logger.info(TAG) { "$mn - Preparing RelyingPartyApiImpl..." }

        val api = RelyingPartyApiImpl(providerInfo.baseUrl)

        val nonce = when (val nonceResult = api.fetchNonce()) {
            is ApiResult.Success -> nonceResult.data.nonce
            is ApiResult.Failure -> {
                Logger.error(TAG) { "$mn - Error fetching nonce: ${nonceResult.error?.errorDescription}" }
                throw ProximityVerificationException(
                    "Failed to fetch nonce: ${nonceResult.error?.errorDescription}"
                )
            }

            is ApiResult.InternalError -> {
                Logger.error(TAG) { "$mn - Exception fetching nonce: ${nonceResult.exception.message}" }
                throw nonceResult.exception
            }
        }

        Logger.info(TAG) { "$mn - Retrieving nonce..." }

        //Logger.debug(TAG) { "$mn - nonce: $nonce" }

        Logger.info(TAG) { "$mn - nonce retrieved" }

        Logger.info(TAG) { "$mn - Preparing jwkFromKey..." }

        val jwkFromKey: Jwk = JwkUtils.ecPublicKeyToJwk(initResult.keyInfo.publicKey)
        //Logger.debug(TAG) { "$mn - jwkFromKey: $jwkFromKey" }
        Logger.info(TAG) { "$mn - jwkFromKey prepared" }

        Logger.info(TAG) { "$mn - Preparing kid..." }
        val kid: String = computeJwkThumbprint(jwkFromKey)
        //Logger.debug(TAG) { "$mn - kid: $kid" }
        Logger.info(TAG) { "$mn - kid prepared" }

        Logger.info(TAG) { "$mn - Preparing clientData..." }
        val clientData: ClientData = produceClientData(nonce, jwkFromKey)
        //Logger.debug(TAG) { "$mn - clientData: $clientData" }
        Logger.info(TAG) { "$mn - clientData prepared" }

        Logger.info(TAG) { "$mn - Preparing clientDataHash..." }
        val clientDataHash: ByteArray = produceClientDataHash(clientData)
        //Logger.debug(TAG) { "$mn - clientDataHash (b64url): ${clientDataHash.encodeBase64UrlNoPadding()}" }
        Logger.info(TAG) { "$mn - clientDataHash prepared" }

        Logger.info(TAG) { "$mn - Retrieving keyAlias..." }
        val keyAlias = getOrCreateHardwareKeyTag(Constants.INIT_KEY_ALIAS)
        //Logger.debug(TAG) { "$mn - keyAlias: $keyAlias" }
        Logger.info(TAG) { "$mn - keyAlias retrieved" }

        Logger.info(TAG) { "$mn - Preparing hardware signature..." }
        val hardwareSignature: ByteArray = produceHardwareSignature(
            alias = keyAlias,
            clientDataHash = clientDataHash
        )
        //Logger.debug(TAG) { "$mn - hardwareSignature (b64url): ${hardwareSignature.encodeBase64UrlNoPadding()}" }
        Logger.info(TAG) { "$mn - hardwareSignature prepared" }

        Logger.info(TAG) { "$mn - Preparing assertion..." }
        val sa = LocalSecureAreaFactory.createLocalSecureAreaProvider().get()
        val assertionRes: Any

        assertionRes = generateAssertion(sa, produceBArrayFromClientData(clientData))
        Logger.debug(TAG) { "$mn - assertionRes: $assertionRes" }
        Logger.info(TAG) { "$mn - assertion prepared" }

        Logger.info(TAG) { "$mn - Preparing bindKeys..." }

        Logger.info(TAG) { "$mn - Constructing JWT..." }

        val baseUrl = providerInfo.baseUrl.trimEnd('/')
        val iat = Clock.System.now().epochSeconds
        val exp = iat + 900

        val jwt = JwtBuilder.build(
            baseUrl = baseUrl,
            nonce = nonce,
            kid = kid,
            jwk = jwkFromKey,
            iat = iat,
            exp = exp,
            hardwareSignature = hardwareSignature,
            integrityAssertion = when (assertionRes) {
                is ByteArray -> assertionRes.toBase64Url()
                is String -> assertionRes.encodeToByteArray().toBase64Url()
                else -> throw IllegalArgumentException("Unsupported assertion type: ${assertionRes::class}")
            },
            hardwareKeyTag = keyAlias,
            keyAlias = keyAlias
        )

        Logger.debug(TAG) { "$mn - Final JWT: $jwt" }
        logLongString("JWT_LOG", jwt)
        Logger.info(TAG) { "$mn - JWT prepared..." }

        val isSignatureValid = JwtVerifier.verify(jwt)
        Logger.info(TAG) { "$mn - JWT signature is valid: $isSignatureValid" }

        Logger.info(TAG) { "$mn - Performing bind key call..." }

        val accessToken = TokenManager.ensureValidAccessToken(
            baseUrl = providerInfo.baseUrl
        )
        Logger.debug(TAG) { "$mn - Access token obtained for bindKeys" }

        val bindKeysResult: ApiResult<KeyBindingResponse> = api.bindKeys(
            jwt = jwt,
            accessToken = accessToken
        )

        when (bindKeysResult) {
            is ApiResult.Success -> {
                Logger.info(TAG) { "$mn - Bind key succeeded" }
                Logger.info(TAG) { "$mn - Bind key performed." }
            }

            is ApiResult.Failure -> {
                val errorDesc = bindKeysResult.error?.errorDescription ?: "Unknown error"
                Logger.error(TAG) { "$mn - Bind key failed: $errorDesc" }
                throw ProximityVerificationException(
                    "Exception calling bindKeys: $errorDesc",
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            }

            is ApiResult.InternalError -> {
                Logger.error(TAG) { "$mn - Exception during bindKeys: ${bindKeysResult.exception.message}" }
                throw bindKeysResult.exception
            }
        }

        Logger.info(TAG) { "$mn - Performing fetchWellKnownConfiguration call..." }

        val wkConfigResult : ApiResult<WellKnownConfiguration> =
            api.fetchWellKnownConfiguration(
                wellKnownUrl = federationEntityConfigUrl(providerInfo.federationEntityUrl)
            )

        val certFromWK = when (wkConfigResult) {
            is ApiResult.Success -> {
                Logger.info(TAG) { "$mn - fetchWellKnownConfiguration succeeded" }
                Logger.info(TAG) { "$mn - fetchWellKnownConfiguration received" }
                Logger.debug(TAG) {
                    "$mn - fetchWellKnownConfiguration " +
                            "response: ${wkConfigResult.data.jwt}"
                }

                val trimmedJwt = wkConfigResult.data.jwt.trim()

                val cerFromWK = try {
                    val certPem = extractX5cFromJwt(trimmedJwt)
                    X509Cert.fromPem(certPem)
                } catch (e: Exception) {
                    val decoded = try {
                        Base64.decode(extractX5cFromJwt(trimmedJwt))
                    } catch (e: Exception) {
                        Logger.error(TAG) { "$mn - Cert decode failed: Base64 malformed. ${e.message}" }
                        throw ProximityVerificationException("access_certificate is not a valid base64-encoded string: ${e.message}",
                            code = ProximityVerificationExceptionCode.PVE01.default())
                    }

                    Logger.error(TAG) { "$mn - Cert parsing failed: ${e.message}" }
                    Logger.debug(TAG) { "$mn - DER length: ${decoded.size}" }
                    val sha256Hex: String = Crypto.digest(Algorithm.SHA256, decoded).toHexString()
                    Logger.debug(TAG) { "$mn - SHA256: $sha256Hex" }
                    Logger.debug(TAG) { "$mn - DER head (first 16 bytes): ${decoded.take(16).joinToString(" ") {it.toHexString()}}" }
                    throw ProximityVerificationException("Parsing X509Cert failed: ${e.message}", code = ProximityVerificationExceptionCode.PVE01.default()
                    )
                }
                Logger.info(TAG) { "$mn - Cert from WK as X509Cert (subject: ${cerFromWK.subject})" }

                cerFromWK
            }

            is ApiResult.Failure -> {
                val errorDesc = wkConfigResult.error?.errorDescription ?: "Unknown error"
                Logger.error(TAG) { "$mn - fetchWellKnownConfiguration failed: $errorDesc" }
                throw ProximityVerificationException(
                    "Exception calling fetchWellKnownConfiguration: $errorDesc",
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            }

            is ApiResult.InternalError -> {
                Logger.error(TAG) {
                    "$mn - Exception during fetchWellKnownConfiguration:" +
                            " ${wkConfigResult.exception.message}"
                }
                throw wkConfigResult.exception
            }
        }

        Logger.info(TAG) { "$mn - Preparing CSR..." }

        val cSRRes = CSRCreator.createCsr(
            csrParam = providerInfo.csrParam,
            pubKey = initResult.keyInfo.publicKey,
            keyAlias = keyAlias
        )

        //Logger.debug(TAG) { "$mn - CSR result: $cSRRes" }
        Logger.info(TAG) { "$mn - CSR prepared..." }

        Logger.info(TAG) { "$mn - csrRes ok" }

        Logger.info(TAG) { "$mn - Performing requestAccessCertificate call..." }

        val accessTokenForCert = TokenManager.ensureValidAccessToken(
            baseUrl = providerInfo.baseUrl
        )
        Logger.debug(TAG) { "$mn - Access token obtained for requestAccessCertificate" }

        val accessCertificateResult: ApiResult<AccessCertificateResponse> =
            api.requestAccessCertificate(
                csr = cSRRes.toBase64Url(),
                accessToken = accessTokenForCert
            )

        when (accessCertificateResult) {
            is ApiResult.Success -> {
                Logger.info(TAG) { "$mn - requestAccessCertificate succeeded" }
                Logger.info(TAG) { "$mn - Access certificate received." }
                Logger.debug(TAG) {
                    "$mn - Access certificate " +
                            "response: ${accessCertificateResult.data.access_certificate}"
                }

                val certPem = accessCertificateResult.data.access_certificate.trim()

                val readerCert = try {
                    X509Cert.fromPem(certPem)
                } catch (e: Exception) {
                    val decoded = try {
                        Base64.decode(certPem)
                    } catch (e: Exception) {
                        Logger.error(TAG) { "$mn - Cert decode failed: Base64 malformed. ${e.message}" }
                        throw ProximityVerificationException("access_certificate is not a valid base64-encoded string: ${e.message}",
                            code = ProximityVerificationExceptionCode.PVE01.default())
                    }

                    Logger.error(TAG) { "$mn - Cert parsing failed: ${e.message}" }
                    Logger.debug(TAG) { "$mn - DER length: ${decoded.size}" }
                    val sha256Hex: String = Crypto.digest(Algorithm.SHA256, decoded).toHexString()
                    Logger.debug(TAG) { "$mn - SHA256: $sha256Hex" }
                    Logger.debug(TAG) { "$mn - DER head (first 16 bytes): ${decoded.take(16).joinToString(" ") {it.toHexString()}}" }
                    throw ProximityVerificationException("Parsing X509Cert failed: ${e.message}", code = ProximityVerificationExceptionCode.PVE01.default()
                    )
                }
                Logger.info(TAG) { "$mn - Access certificate parsed as X509Cert (subject: ${readerCert.subject})" }

                val deviceKeyTag = getOrCreateHardwareKeyTag(Constants.INIT_KEY_ALIAS)

                var certChain: X509CertChain? = null

                Logger.debug(TAG) { "$mn - Building certificate chain for:${deviceKeyTag}..." }

                Logger.debug(TAG) { "$mn - Adding cert from WK..." }

                certChain = X509CertChain(listOf(readerCert, certFromWK))

                Logger.debug(TAG) { "$mn - Associating certChain to alias ${deviceKeyTag}..." }

                sa.withCertChain(deviceKeyTag, certChain)

                Logger.info(TAG) { "$mn - Completed initAndCreateKey. Chain length: ${certChain!!.certificates.size}" }

                Logger.info(TAG) { "$mn - Init completed with success!" }

                val storage = provideLocalStorage()

                storage.saveElement(key = Constants.REGISTRATION_COMPLETED, true.toString())

                return certChain
            }

            is ApiResult.Failure -> {
                val errorDesc = accessCertificateResult.error?.errorDescription ?: "Unknown error"
                Logger.error(TAG) { "$mn - requestAccessCertificate failed: $errorDesc" }
                throw ProximityVerificationException(
                    "Exception calling requestAccessCertificate: $errorDesc",
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            }

            is ApiResult.InternalError -> {
                Logger.error(TAG) {
                    "$mn - Exception during requestAccessCertificate:" +
                            " ${accessCertificateResult.exception.message}"
                }
                throw accessCertificateResult.exception
            }
        }
    }

    suspend fun computeJwkThumbprint(jwk: Jwk): String {
        val canonicalJson = buildJsonObject {
            put("crv", JsonPrimitive(jwk.crv))
            put("kty", JsonPrimitive(jwk.kty))
            put("x", JsonPrimitive(jwk.x))
            put("y", JsonPrimitive(jwk.y))
        }
        val canonicalString = Json.encodeToString(JsonObject.serializer(), canonicalJson)
        val digest = Crypto.digest(Algorithm.SHA256, canonicalString.encodeToByteArray())
        return digest.encodeBase64UrlNoPadding()
    }

    object JwtBuilder {
        suspend fun build(
            baseUrl: String,
            nonce: String,
            kid: String,
            jwk: Jwk,
            iat: Long,
            exp: Long,
            hardwareSignature: ByteArray,
            integrityAssertion: String,
            hardwareKeyTag: String,
            keyAlias: String
        ): String {
            val header = buildJsonObject {
                put("alg", JsonPrimitive("ES256"))
                put("kid", JsonPrimitive(kid))
                put("typ", JsonPrimitive("rp-kb+jwt"))
            }

            val jwkJson = buildJsonObject {
                put("kty", JsonPrimitive(jwk.kty))
                put("crv", JsonPrimitive(jwk.crv))
                put("x", JsonPrimitive(jwk.x))
                put("y", JsonPrimitive(jwk.y))
            }

            val payload = buildJsonObject {
                put("iss", JsonPrimitive("$baseUrl/$kid"))
                put("aud", JsonPrimitive("$baseUrl/"))
                put("iat", JsonPrimitive(iat))
                put("exp", JsonPrimitive(exp))
                put("nonce", JsonPrimitive(nonce))
                put(
                    "hardware_signature",
                    JsonPrimitive(hardwareSignature.encodeBase64UrlNoPadding())
                )
                put("integrity_assertion", JsonPrimitive(integrityAssertion))
                put("hardware_key_tag", JsonPrimitive(hardwareKeyTag))
                put("cnf", buildJsonObject {
                    put("jwk", jwkJson)
                })
            }

            val headerB64 = header.toCompactBase64()
            val payloadB64 = payload.toCompactBase64()
            val signingInput = "$headerB64.$payloadB64"

            val signature = produceHardwareSignature(
                alias = keyAlias,
                clientDataHash = signingInput.encodeToByteArray()
            )
            val signatureB64 = signature.encodeBase64UrlNoPadding()

            return "$signingInput.$signatureB64"
        }
    }

    private fun JsonObject.toCompactBase64(): String {
        return base64UrlEncode(
            Json.encodeToString(JsonObject.serializer(), this).encodeToByteArray()
        )
    }

    private fun base64UrlEncode(input: ByteArray): String = input.encodeBase64UrlNoPadding()

    fun decodeBase64Url(input: String): String {
        return decodeBase64UrlToBytes(input).decodeToString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64UrlToBytes(input: String): ByteArray {
        val normalized = input.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded.encodeToByteArray())
    }

    fun ecPublicKeyFromJwk(xB64Url: String, yB64Url: String, crv: String): EcPublicKey {
        val curve = when (crv) {
            "P-256" -> EcCurve.P256
            "P-384" -> EcCurve.P384
            "P-521" -> EcCurve.P521
            else -> throw ProximityVerificationException(
                "Unsupported curve: $crv",
                code = ProximityVerificationExceptionCode.PVE03.default()
            )
        }

        val labels: Map<CoseLabel, DataItem> = buildMap {
            put(CoseNumberLabel(Cose.COSE_KEY_KTY), Cose.COSE_KEY_TYPE_EC2.toDataItem())
            put(CoseNumberLabel(Cose.COSE_KEY_PARAM_CRV), curve.coseCurveIdentifier.toDataItem())
            put(
                CoseNumberLabel(Cose.COSE_KEY_PARAM_X),
                decodeBase64UrlToBytes(xB64Url).toDataItem()
            )
            put(
                CoseNumberLabel(Cose.COSE_KEY_PARAM_Y),
                decodeBase64UrlToBytes(yB64Url).toDataItem()
            )
        }

        val coseKey = CoseKey(labels)
        return coseKey.ecPublicKey
    }

    object JwtVerifier {
        suspend fun verify(jwt: String): Boolean {
            val parts = jwt.split(".")
            if (parts.size != 3) return false
            val payload = Json.decodeFromString(JsonObject.serializer(), decodeBase64Url(parts[1]))
            val cnf = payload["cnf"]?.jsonObject ?: return false
            val jwk = cnf["jwk"]?.jsonObject ?: return false
            val x = jwk["x"]?.jsonPrimitive?.content ?: return false
            val y = jwk["y"]?.jsonPrimitive?.content ?: return false
            val crv = jwk["crv"]?.jsonPrimitive?.content ?: return false
            val pubKey = ecPublicKeyFromJwk(x, y, crv)
            return try {
                checkJwtSignature(pubKey, jwt)
                true
            } catch (_: Exception) {
                false
            }
        }

        suspend fun checkJwtSignature(publicKey: EcPublicKey, jwt: String) {
            val index = jwt.lastIndexOf('.')
            val signature = EcSignature.fromCoseEncoded(jwt.substring(index + 1).fromBase64Url())
            val algorithm = when (publicKey.curve) {
                EcCurve.P256 -> Algorithm.ES256
                EcCurve.P384 -> Algorithm.ES384
                EcCurve.P521 -> Algorithm.ES512
                else -> throw ProximityVerificationException(
                    "Unsupported public key",
                    code = ProximityVerificationExceptionCode.PVE03.default()
                )
            }
            try {
                Crypto.checkSignature(
                    publicKey,
                    jwt.substring(0, index).toByteArray(),
                    algorithm,
                    signature
                )
            } catch (e: SignatureVerificationException) {
                throw ProximityVerificationException(
                    "Invalid JWT signature",
                    code = ProximityVerificationExceptionCode.PVE03.default()
                )
            }
        }
    }

    fun logLongString(tag: String, message: String, chunkSize: Int = 300) {
        var start = 0
        val length = message.length
        while (start < length) {
            val end = (start + chunkSize).coerceAtMost(length)
            val chunk = message.substring(start, end)
            Logger.debug(tag, message = { "[chunk] $chunk" })
            start = end
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun extractX5cFromJwt(jwt: String): String {
        val parts = jwt.split(".")
        if (parts.size != 3) {
            throw ProximityVerificationException(
                "Invalid JWT format: expected 3 parts but got ${parts.size}",
                code = ProximityVerificationExceptionCode.PVE08.default()
            )
        }

        val b64D = Base64.UrlSafe.withPadding(PaddingOption.ABSENT_OPTIONAL)
        val payload = b64D.decode(parts[1])
        val payloadJson = Json.decodeFromString<JsonObject>(payload.decodeToString())

        val x5c = payloadJson["jwks"]?.jsonObject
            ?.get("keys")?.jsonArray
            ?.getOrNull(0)?.jsonObject
            ?.get("x5c")?.jsonArray
            ?.getOrNull(0)?.jsonPrimitive?.content

        if (x5c.isNullOrBlank()) {
            throw ProximityVerificationException(
                "Missing or empty x5c[0] certificate inside JWT payload",
                code = ProximityVerificationExceptionCode.PVE08.default()
            )
        }

        return x5c
    }

}

expect object MultipazContext {
    fun initAppForMultipaz(
    )
}
