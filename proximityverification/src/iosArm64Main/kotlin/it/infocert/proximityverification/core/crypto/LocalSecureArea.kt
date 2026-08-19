@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package it.infocert.proximityverification.core.crypto

import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.multipaz.crypto.*
import org.multipaz.securearea.*
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import it.infocert.proximityverification.core.models.KeyCreationResult
import it.infocert.proximityverification.core.models.KeyCreationStatus
import it.infocert.proximityverification.core.models.LocalKeystoreKeyInfo
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.time.Clock
import org.multipaz.asn1.ASN1
import org.multipaz.asn1.ASN1BitString
import org.multipaz.asn1.ASN1Encoding
import org.multipaz.asn1.ASN1Integer
import org.multipaz.asn1.ASN1Object
import org.multipaz.asn1.ASN1ObjectIdentifier
import org.multipaz.asn1.ASN1OctetString
import org.multipaz.asn1.ASN1Sequence
import org.multipaz.asn1.ASN1Set
import org.multipaz.asn1.ASN1TagClass
import org.multipaz.asn1.ASN1TaggedObject
import org.multipaz.asn1.ASN1Time
import org.multipaz.prompt.Reason
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

private const val TAG = "LocalSecureArea"

class LocalSecureArea private constructor(
    private val storageTable: StorageTable,
    private val partitionId: String
) : ExtendedSecureArea, ExtendedSecureAreaWithTTL {

    companion object {
        private const val METADATA_PREFIX = "@"
        private const val IDENTIFIER = "LocalSecureArea"

        private val tableSpec = StorageTableSpec(
            name = IDENTIFIER,
            supportPartitions = true,
            supportExpiration = false
        )

        suspend fun create(storage: Storage): LocalSecureArea {
            val table = storage.getTable(tableSpec)
            val area = LocalSecureArea(table, IDENTIFIER)
            area.secureEnclave = SecureEnclaveSecureArea.create(storage, IDENTIFIER)
            return area
        }

        private suspend fun computePublicKeyHash(pub: EcPublicKey): ByteString {
            val dataItem = pub.toCoseKey().toDataItem()
            val rawCbor: ByteArray = org.multipaz.cbor.Cbor.encode(dataItem)
            return ByteString(Crypto.digest(Algorithm.SHA256, rawCbor))
        }

        private object ByteStringAsByteArraySerializer : KSerializer<ByteString> {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ByteString", PrimitiveKind.BYTE)

            override fun serialize(encoder: Encoder, value: ByteString) {
                encoder.encodeSerializableValue(ByteArraySerializer(), value.toByteArray())
            }

            override fun deserialize(decoder: Decoder): ByteString {
                val bytes = decoder.decodeSerializableValue(ByteArraySerializer())
                return ByteString(bytes)
            }
        }

        private object X509CertAsByteArraySerializer : KSerializer<X509Cert> {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("X509Cert", PrimitiveKind.BYTE)

            override fun serialize(encoder: Encoder, value: X509Cert) {
                encoder.encodeSerializableValue(ByteArraySerializer(), value.encoded.toByteArray())
            }

            override fun deserialize(decoder: Decoder): X509Cert {
                val bytes = decoder.decodeSerializableValue(ByteArraySerializer())
                return X509Cert(ByteString(bytes))
            }
        }

        private val moduleAwareCbor = Cbor {
            serializersModule = SerializersModule {
                contextual(ByteString::class, ByteStringAsByteArraySerializer)
                contextual(X509Cert::class, X509CertAsByteArraySerializer)
            }
        }
    }

    private lateinit var secureEnclave: SecureEnclaveSecureArea

    override val identifier: String = IDENTIFIER
    override val displayName: String = "Local Secure Area (iOS)"
    override val supportedAlgorithms: List<Algorithm>
        get() = secureEnclave.supportedAlgorithms

    override suspend fun createKey(alias: String?, createKeySettings: CreateKeySettings): KeyInfo {
        val realAlias = alias ?: throw ProximityVerificationException("Alias cannot be null",
            code = ProximityVerificationExceptionCode.PVE03())

        val metadataKey = METADATA_PREFIX + realAlias

        if (storageTable.get(realAlias, partitionId) != null) {
            storageTable.delete(realAlias, partitionId)
        }
        if (storageTable.get(metadataKey, partitionId) != null) {
            storageTable.delete(metadataKey, partitionId)
        }

        storageTable.insert(realAlias, ByteString(), partitionId)

        val keyInfo = secureEnclave.createKey(realAlias, createKeySettings)

        val hash = computePublicKeyHash(keyInfo.publicKey)

        val metadata = KeyMetadata(
            algorithm = keyInfo.algorithm,
            validFrom = null,
            validUntil = null,
            publicKeyHash = hash,
            certChain = keyInfo.attestation.certChain?.certificates,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )

        val encoded = moduleAwareCbor.encodeToByteArray(metadata)

        try {
            storageTable.insert(metadataKey, ByteString(encoded), partitionId)
        } catch (e: org.multipaz.storage.KeyExistsStorageException) {
            Logger.warn("LocalSecureArea") {
                "Metadata '$metadataKey' already exists. Updating instead of inserting."
            }
            storageTable.update(metadataKey, ByteString(encoded), partitionId)
        }

        Logger.debug("LocalSecureArea") { "Key '$realAlias' created and metadata persisted." }

        return LocalKeystoreKeyInfo(
            alias = realAlias,
            algorithm = keyInfo.algorithm,
            publicKey = keyInfo.publicKey,
            validFrom = null,
            validUntil = null,
            attestation = KeyAttestation(publicKey = keyInfo.publicKey, certChain = null),
            attestKeyAlias = null,
            userAuthenticationTimeoutMillis = 0,
            isStrongBoxBacked = false,
            isUserAuthenticationRequired = false,
            certChain = null
        )
    }

    override suspend fun createKeyWithTtl(
        alias: String,
        createKeySettings: CreateKeySettings,
        ttlMillis: Long,
        isOnline: Boolean
    ): KeyCreationResult {
        val mn = "createKeyWithTtl"
        val metadataKey = METADATA_PREFIX + alias
        val now = Clock.System.now().toEpochMilliseconds()

        val existingMetadataBytes = storageTable.get(metadataKey, partitionId)
        Logger.debug(TAG) { "$mn - Checking existing metadata for key '$alias'" }
        if (existingMetadataBytes != null) {
            val metadata = moduleAwareCbor.decodeFromByteArray<KeyMetadata>(existingMetadataBytes.toByteArray())
            val creationTime = metadata.createdAt
            val stillValid = now - creationTime < ttlMillis

            Logger.debug(TAG) { "$mn - Key '$alias' metadata found. Still valid: $stillValid" }
            if (stillValid) {
                Logger.debug(TAG) { "$mn - Reusing existing key '$alias' as it is still valid." }
                val keyInfo = getKeyInfo(alias)
                return KeyCreationResult(keyInfo, KeyCreationStatus.REUSED_EXISTING)
            }
            if (!isOnline) {
                Logger.warn(TAG) { "$mn - Key '$alias' is expired, but device is offline, reusing key with expired certificate." }
                val keyInfo = getKeyInfo(alias)
                return KeyCreationResult(keyInfo, KeyCreationStatus.REUSED_EXISTING)
            }

            Logger.debug(TAG) { "$mn - Key '$alias' is expired, deleting and creating a new one." }
            deleteKey(alias)
        }
        if (!isOnline) {
            Logger.error(TAG) { "$mn - Cannot create key '$alias' while offline, and no existing key found." }
            throw ProximityVerificationException(
                "Key with alias '$alias' does not exist, and online creation is required while the device is offline.",
                code = ProximityVerificationExceptionCode.PVE14()
            )
        }
        Logger.debug(TAG) { "$mn - Creating new key '$alias' with TTL $ttlMillis ms." }
        val keyInfo = createKey(alias, createKeySettings)

        val metadataBytes = storageTable.get(metadataKey, partitionId)
            ?: error("Metadata should exist after key creation")

        val updatedMetadata = moduleAwareCbor.decodeFromByteArray<KeyMetadata>(metadataBytes.toByteArray())
            .copy(createdAt = now)

        val encoded = moduleAwareCbor.encodeToByteArray(updatedMetadata)
        storageTable.update(metadataKey, ByteString(encoded), partitionId)

        return KeyCreationResult(keyInfo, KeyCreationStatus.CREATED_NEW)
    }

    override suspend fun getKeyInfo(alias: String): KeyInfo {
        val metadataBytes = storageTable.get(METADATA_PREFIX + alias, partitionId)
            ?: throw ProximityVerificationException("Key metadata not found",
                code = ProximityVerificationExceptionCode.PVE03())

        val metadata = moduleAwareCbor.decodeFromByteArray<KeyMetadata>(metadataBytes.toByteArray())
        val keyInfo = secureEnclave.getKeyInfo(alias)
        val hash = computePublicKeyHash(keyInfo.publicKey)

        check(metadata.publicKeyHash == hash) {
            "Key integrity check failed: public key hash mismatch"
        }

        return LocalKeystoreKeyInfo(
            alias = alias,
            algorithm = metadata.algorithm,
            publicKey = keyInfo.publicKey,
            validFrom = metadata.validFrom?.let { Instant.fromEpochMilliseconds(it) },
            validUntil = metadata.validUntil?.let { Instant.fromEpochMilliseconds(it) },
            certChain = metadata.certChain,
            attestKeyAlias = null,
            userAuthenticationTimeoutMillis = 0,
            isStrongBoxBacked = false,
            isUserAuthenticationRequired = false,
            attestation = KeyAttestation(publicKey = keyInfo.publicKey, certChain = null)
        )
    }

    override suspend fun deleteKey(alias: String) {
        secureEnclave.deleteKey(alias)
        storageTable.delete(alias, partitionId)
        storageTable.delete(METADATA_PREFIX + alias, partitionId)
    }

    override suspend fun sign(alias: String, dataToSign: ByteArray, unlockReason: Reason): EcSignature {
        return secureEnclave.sign(alias, dataToSign, unlockReason)
    }

    override suspend fun keyAgreement(alias: String, otherKey: EcPublicKey, unlockReason: Reason): ByteArray {
        return secureEnclave.keyAgreement(alias, otherKey, unlockReason)
    }

    override suspend fun getKeyInvalidated(alias: String): Boolean {
        return secureEnclave.getKeyInvalidated(alias)
    }

    override suspend fun setKeyCertChain(alias: String, certChain: X509CertChain) {
        val metadataBytes = storageTable.get(METADATA_PREFIX + alias, partitionId)
            ?: throw ProximityVerificationException("Key metadata not found",
                code = ProximityVerificationExceptionCode.PVE03())

        val metadata = moduleAwareCbor.decodeFromByteArray<KeyMetadata>(metadataBytes.toByteArray())
        val updated = metadata.copy(certChain = certChain.certificates)

        val encoded = moduleAwareCbor.encodeToByteArray(updated)
        storageTable.update(METADATA_PREFIX + alias, ByteString(encoded), partitionId)
    }

    @Serializable
    data class KeyMetadata(
        val algorithm: Algorithm,
        val validFrom: Long?,
        val validUntil: Long?,
        @Contextual val publicKeyHash: ByteString,
        val certChain: List<@Contextual X509Cert>? = null,
        val createdAt: Long
    )

    fun buildCertChain(
        deviceCert: X509Cert,
        rootCert: X509Cert
    ): X509CertChain {
        return X509CertChain(listOf(deviceCert, rootCert))
    }

    suspend fun generateCertSignedByRoot(
        deviceKeyAlias: String,
        subject: X500Name,
        rootKeyAlias: String,
        rootCert: X509Cert,
        validityDays: Int = 365
    ): X509Cert {
        val deviceKey = secureEnclave.getKeyInfo(deviceKeyAlias).publicKey as EcPublicKey
        val rootKey = secureEnclave.getKeyInfo(rootKeyAlias).publicKey as EcPublicKey
        val algo = secureEnclave.getKeyInfo(rootKeyAlias).algorithm

        val serial = ASN1Integer(Random.nextLong())
        val validFrom = Clock.System.now()
        val validUntil = validFrom.plus(kotlin.time.Duration.parse("${validityDays}d"))

        val devicePublicKeyBytes = when (deviceKey) {
            is EcPublicKeyDoubleCoordinate -> deviceKey.asUncompressedPointEncoding
            is EcPublicKeyOkp -> deviceKey.x
        }

        val publicKeyInfo = ASN1Sequence(listOf(
            deviceKey.curve.toPublicKeyAlgorithmIdentifier(),
            ASN1BitString(0, devicePublicKeyBytes)
        ))

        val subjectKeyId = Crypto.digest(Algorithm.INSECURE_SHA1, devicePublicKeyBytes)

        val authorityKeyId = rootCert.subjectKeyIdentifier?.let {
            ASN1Sequence(listOf(
                ASN1TaggedObject(
                    ASN1TagClass.CONTEXT_SPECIFIC,
                    ASN1Encoding.PRIMITIVE,
                    0,
                    it
                )
            ))
        }

        val extensions = mutableListOf<ASN1Sequence>()
        extensions += ASN1Sequence(listOf(
            ASN1ObjectIdentifier("2.5.29.14"), // SubjectKeyIdentifier
            ASN1OctetString(ASN1.encode(ASN1OctetString(subjectKeyId)))
        ))

        if (authorityKeyId != null) {
            extensions += ASN1Sequence(listOf(
                ASN1ObjectIdentifier("2.5.29.35"), // AuthorityKeyIdentifier
                ASN1OctetString(ASN1.encode(authorityKeyId))
            ))
        }

        val tbs = ASN1Sequence(listOf(
            ASN1TaggedObject(ASN1TagClass.CONTEXT_SPECIFIC, ASN1Encoding.CONSTRUCTED, 0, ASN1.encode(ASN1Integer(2))),
            serial,
            algo.toSignatureAlgorithmIdentifier(rootKey.curve),
            generateName(rootCert.subject),
            ASN1Sequence(listOf(
                ASN1Time(validFrom),
                ASN1Time(validUntil)
            )),
            generateName(subject),
            publicKeyInfo,
            ASN1TaggedObject(
                ASN1TagClass.CONTEXT_SPECIFIC,
                ASN1Encoding.CONSTRUCTED,
                3,
                ASN1.encode(ASN1Sequence(extensions))
            )
        ))

        val tbsBytes = ASN1.encode(tbs)
        val sig = sign(rootKeyAlias, tbsBytes).toDerEncoded()

        val cert = ASN1Sequence(listOf(
            tbs,
            algo.toSignatureAlgorithmIdentifier(rootKey.curve),
            ASN1BitString(0, sig)
        ))

        return X509Cert(ByteString(ASN1.encode(cert)))
    }


    suspend fun generateSelfSignedCert(
        alias: String,
        subject: X500Name
    ): X509Cert {
        val info = secureEnclave.getKeyInfo(alias)
        val pubKey = info.publicKey as EcPublicKey
        val validFrom = Clock.System.now()
        val validUntil = validFrom.plus(365.days)
        val serial = ASN1Integer(Random.nextLong())
        val algo = info.algorithm

        val publicKeyEncoding = when (pubKey) {
            is EcPublicKeyDoubleCoordinate -> pubKey.asUncompressedPointEncoding
            is EcPublicKeyOkp -> pubKey.x
        }

        val publicKeyInfo = ASN1Sequence(listOf(
            pubKey.curve.toPublicKeyAlgorithmIdentifier(),
            ASN1BitString(0, publicKeyEncoding)
        ))

        val tbs = ASN1Sequence(listOf(
            ASN1TaggedObject(ASN1TagClass.CONTEXT_SPECIFIC, ASN1Encoding.CONSTRUCTED, 0, ASN1.encode(ASN1Integer(2))),
            serial,
            algo.toSignatureAlgorithmIdentifier(pubKey.curve),
            generateName(subject),
            ASN1Sequence(listOf(
                ASN1Time(validFrom),
                ASN1Time(validUntil)
            )),
            generateName(subject),
            publicKeyInfo
        ))

        val tbsBytes = ASN1.encode(tbs)
        val sig = sign(alias, tbsBytes).toDerEncoded()

        val cert = ASN1Sequence(listOf(
            tbs,
            algo.toSignatureAlgorithmIdentifier(pubKey.curve),
            ASN1BitString(0, sig)
        ))

        return X509Cert(ByteString(ASN1.encode(cert)))
    }

    private fun generateName(name: X500Name): ASN1Sequence {
        val objs = mutableListOf<ASN1Object>()
        for ((oid, value) in name.components) {
            objs.add(
                ASN1Set(listOf(
                    ASN1Sequence(listOf(
                        ASN1ObjectIdentifier(oid),
                        value
                    ))
                ))
            )
        }
        return ASN1Sequence(objs)
    }

    private fun EcCurve.toPublicKeyAlgorithmIdentifier(): ASN1Sequence {
        return when (this) {
            EcCurve.P256 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.2.840.10045.3.1.7")
            ))
            EcCurve.P384 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.3.132.0.34")
            ))
            EcCurve.P521 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.3.132.0.35")
            ))
            EcCurve.BRAINPOOLP256R1 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.3.36.3.3.2.8.1.1.7")
            ))
            EcCurve.BRAINPOOLP320R1 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.3.36.3.3.2.8.1.1.9")
            ))
            EcCurve.BRAINPOOLP384R1 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.3.36.3.3.2.8.1.1.11")
            ))
            EcCurve.BRAINPOOLP512R1 -> ASN1Sequence(listOf(
                ASN1ObjectIdentifier("1.2.840.10045.2.1"),
                ASN1ObjectIdentifier("1.3.36.3.3.2.8.1.1.13")
            ))
            EcCurve.ED25519 -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.112")))
            EcCurve.ED448 -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.113")))
            EcCurve.X25519 -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.110")))
            EcCurve.X448 -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.111")))
        }
    }

    private fun Algorithm.toSignatureAlgorithmIdentifier(curve: EcCurve): ASN1Sequence {
        val signatureAlgorithmOid = when (this) {
            Algorithm.ES256, Algorithm.ESP256, Algorithm.ESB256 -> "1.2.840.10045.4.3.2"
            Algorithm.ES384, Algorithm.ESP384, Algorithm.ESB384, Algorithm.ESB320 -> "1.2.840.10045.4.3.3"
            Algorithm.ES512, Algorithm.ESP512, Algorithm.ESB512 -> "1.2.840.10045.4.3.4"
            Algorithm.EDDSA -> when (curve) {
                EcCurve.ED25519 -> "1.3.101.112"
                EcCurve.ED448 -> "1.3.101.113"
                else -> throw ProximityVerificationException("Unsupported curve $curve for $this",
                    code = ProximityVerificationExceptionCode.PVE03())
            }
            else -> throw ProximityVerificationException("Unsupported signature algorithm $this",
                code = ProximityVerificationExceptionCode.PVE03())
        }
        return ASN1Sequence(listOf(ASN1ObjectIdentifier(signatureAlgorithmOid)))
    }

    suspend fun generateAssertionFromClientDataSwift(
        keyId: String,
        clientData: ByteArray
    ): ByteArray {
        val digest = Crypto.digest(Algorithm.SHA256, clientData)
        return generateDeviceAssertionFromSwift(keyId, digest)
    }

}


