package it.infocert.proximityverification.core.crypto

import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import it.infocert.proximityverification.ContextInitializer
import it.infocert.proximityverification.core.crypto.SelfSignedCertUtil.generateSelfSignedCertFromKeystore
import it.infocert.proximityverification.core.models.KeyCreationResult
import it.infocert.proximityverification.core.models.KeyCreationStatus
import it.infocert.proximityverification.core.models.LocalKeystoreKeyInfo
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.multipaz.cbor.DataItem
import org.multipaz.crypto.*
import org.multipaz.prompt.Reason
import org.multipaz.securearea.*
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "LocalSecureArea"
@OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class, InternalSerializationApi::class)
class LocalSecureArea private constructor(
    private val storageTable: StorageTable
) : ExtendedSecureArea, ExtendedSecureAreaWithTTL {

    companion object {
        const val IDENTIFIER = "LocalSecureArea"
        private const val METADATA_PREFIX = "@"
        private val tableSpec = StorageTableSpec(
            name = IDENTIFIER,
            supportPartitions = true,
            supportExpiration = false
        )

        suspend fun create(storage: Storage): LocalSecureArea {
            val lsa = LocalSecureArea(storage.getTable(tableSpec))
            lsa.androidSecureArea = AndroidKeystoreSecureArea.create(storage, IDENTIFIER)
            return lsa
        }

        suspend fun computePublicKeyHash(publicKeyDataItem: DataItem): ByteArray {
            val rawCbor: ByteArray = org.multipaz.cbor.Cbor.encode(publicKeyDataItem)
            return Crypto.digest(Algorithm.SHA256, rawCbor)
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

        @OptIn(ExperimentalSerializationApi::class)
        private val moduleAwareCbor = Cbor {
            serializersModule = SerializersModule {
                contextual(ByteString::class, ByteStringAsByteArraySerializer)
                contextual(X509Cert::class, X509CertAsByteArraySerializer)
            }
        }
    }

    private lateinit var androidSecureArea: AndroidKeystoreSecureArea

    override val identifier: String = IDENTIFIER
    override val displayName: String = "Local Secure Area (Android)"
    override val supportedAlgorithms: List<Algorithm>
        get() = androidSecureArea.supportedAlgorithms

    override suspend fun createKey(
        alias: String?,
        createKeySettings: CreateKeySettings
    ): KeyInfo {
        val realAlias = alias ?: throw ProximityVerificationException(
            "Alias cannot be null",
            code = ProximityVerificationExceptionCode.PVE03()
        )

        val existingMetadata = storageTable.get(METADATA_PREFIX + realAlias, IDENTIFIER)

        if (existingMetadata != null) {

            Logger.debug("LocalSecureArea") {
                "Alias '$realAlias' already exists. Removing key and metadata."
            }

            storageTable.delete(realAlias, IDENTIFIER)
            storageTable.delete(METADATA_PREFIX + realAlias, IDENTIFIER)
        }

        storageTable.insert(realAlias, ByteString(), IDENTIFIER)

        val keyInfo =
            androidSecureArea.createKey(realAlias, createKeySettings)

        val hash = computePublicKeyHash(keyInfo.publicKey.toDataItem())

        val metadata = KeyMetadata(
            algorithm = keyInfo.algorithm,
            validFrom = keyInfo.validFrom?.toEpochMilliseconds(),
            validUntil = keyInfo.validUntil?.toEpochMilliseconds(),
            useStrongBox = keyInfo.isStrongBoxBacked,
            publicKeyHash = ByteString(hash),
            certChain = keyInfo.attestation.certChain?.certificates,
            createdAt = System.currentTimeMillis()
        )

        val encodedBytes = moduleAwareCbor.encodeToByteArray(KeyMetadata.serializer(), metadata)
        val encodedByteString = ByteString(encodedBytes)

        try {
            storageTable.insert(
                key = METADATA_PREFIX + realAlias,
                data = encodedByteString,
                partitionId = IDENTIFIER
            )
        } catch (e: org.multipaz.storage.KeyExistsStorageException) {
            Logger.warn("LocalSecureArea") {
                "Key metadata '@$realAlias' already exists. Updating instead of inserting."
            }
            storageTable.update(
                key = METADATA_PREFIX + realAlias,
                data = encodedByteString,
                partitionId = IDENTIFIER
            )
        }

        Logger.debug("LocalSecureArea") {
            "Key '$realAlias' created and metadata persisted."
        }

        return LocalKeystoreKeyInfo(
            alias = realAlias,
            algorithm = metadata.algorithm,
            publicKey = keyInfo.publicKey,
            attestation = keyInfo.attestation,
            attestKeyAlias = keyInfo.alias,
            isUserAuthenticationRequired = keyInfo.isUserAuthenticationRequired,
            userAuthenticationTimeoutMillis = keyInfo.userAuthenticationTimeout.inWholeMilliseconds,
            isStrongBoxBacked = keyInfo.isStrongBoxBacked,
            validFrom = keyInfo.validFrom,
            validUntil = keyInfo.validUntil,
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
        val now = System.currentTimeMillis()

        val existingMetadataBytes = storageTable.get(metadataKey, IDENTIFIER)
        Logger.debug(TAG) { "$mn - Checking existing metadata for key '$alias'" }
        if (existingMetadataBytes != null) {
            val metadata =
                moduleAwareCbor.decodeFromByteArray<KeyMetadata>(existingMetadataBytes.toByteArray())
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

        val keyInfo = createKey(alias, createKeySettings)

        val metadataBytes = storageTable.get(metadataKey, IDENTIFIER)
            ?: error("Metadata should exist after key creation")

        val updatedMetadata =
            moduleAwareCbor.decodeFromByteArray<KeyMetadata>(metadataBytes.toByteArray())
                .copy(createdAt = now)

        val encoded = moduleAwareCbor.encodeToByteArray(updatedMetadata)

        storageTable.update(metadataKey, ByteString(encoded), IDENTIFIER)

        return KeyCreationResult(keyInfo, KeyCreationStatus.CREATED_NEW)
    }

    override suspend fun getKeyInfo(alias: String): KeyInfo {
        val metadataBytes = storageTable.get(METADATA_PREFIX + alias, IDENTIFIER)
            ?: throw ProximityVerificationException(
                "Key metadata not found",
                code = ProximityVerificationExceptionCode.PVE03()
            )

        val metadata = moduleAwareCbor.decodeFromByteArray<KeyMetadata>(metadataBytes.toByteArray())
        val keyInfo = androidSecureArea.getKeyInfo(alias)
        val hash = computePublicKeyHash(keyInfo.publicKey.toDataItem())

        check(metadata.publicKeyHash == ByteString(hash)) {
            "Key integrity check failed: public key hash mismatch"
        }

        return LocalKeystoreKeyInfo(
            alias = alias,
            algorithm = metadata.algorithm,
            publicKey = keyInfo.publicKey,
            attestation = keyInfo.attestation,
            attestKeyAlias = keyInfo.alias,
            isUserAuthenticationRequired = keyInfo.isUserAuthenticationRequired,
            userAuthenticationTimeoutMillis = keyInfo.userAuthenticationTimeout.inWholeMilliseconds,
            isStrongBoxBacked = keyInfo.isStrongBoxBacked,
            validFrom = keyInfo.validFrom,
            validUntil = keyInfo.validUntil,
            certChain = metadata.certChain
        )
    }

    override suspend fun deleteKey(alias: String) {
        androidSecureArea.deleteKey(alias)
        storageTable.delete(alias, IDENTIFIER)
        storageTable.delete(METADATA_PREFIX + alias, IDENTIFIER)
    }

    override suspend fun sign(
        alias: String, dataToSign: ByteArray,
        unlockReason: Reason
    ): EcSignature {
        return androidSecureArea.sign(alias, dataToSign, unlockReason)
    }

    override suspend fun keyAgreement(
        alias: String, otherKey: EcPublicKey,
        unlockReason: Reason
    ): ByteArray {
        return androidSecureArea.keyAgreement(alias, otherKey, unlockReason)
    }

    override suspend fun getKeyInvalidated(alias: String): Boolean {
        return androidSecureArea.getKeyInvalidated(alias)
    }

    override suspend fun setKeyCertChain(alias: String, certChain: X509CertChain) {
        val metadataBytes = storageTable.get(METADATA_PREFIX + alias, IDENTIFIER)
            ?: throw ProximityVerificationException(
                "Key metadata not found",
                code = ProximityVerificationExceptionCode.PVE03()
            )

        val metadata = moduleAwareCbor.decodeFromByteArray<KeyMetadata>(metadataBytes.toByteArray())
        val updated = metadata.copy(certChain = certChain.certificates)

        val encodedBytes = moduleAwareCbor.encodeToByteArray(KeyMetadata.serializer(), updated)
        val encodedByteString = ByteString(encodedBytes)

        storageTable.update(
            key = METADATA_PREFIX + alias,
            data = encodedByteString,
            partitionId = IDENTIFIER
        )
    }

    @Serializable
    private data class KeyMetadata(
        val algorithm: Algorithm,
        val validFrom: Long?,
        val validUntil: Long?,
        val useStrongBox: Boolean,
        @Contextual val publicKeyHash: ByteString,
        val certChain: List<@Contextual X509Cert>? = null,
        val createdAt: Long
    )

    suspend fun generateSelfSignedCert(
        alias: String,
        subject: X500Name
    ): X509Cert {
        return androidSecureArea.generateSelfSignedCertFromKeystore(alias, subject)
    }

    suspend fun generateCertSignedByRoot(
        deviceKeyAlias: String,
        subject: X500Name,
        rootKeyAlias: String,
        rootCert: X509Cert
    ): X509Cert {
        return androidSecureArea.generateCertSignedByRoot(
            deviceKeyAlias,
            subject,
            rootKeyAlias,
            rootCert
        )
    }

    suspend fun buildCertChain(deviceCert: X509Cert, rootCert: X509Cert): X509CertChain {
        return androidSecureArea.buildCertChain(deviceCert, rootCert)
    }


    @OptIn(ExperimentalUuidApi::class)
    internal suspend fun generateAssertionFromClientData(
        androidCloudProjectNumber: String,
        clientData: ByteArray
    ): String {
        "generateAssertionFromClientData"
        val digest = Crypto.digest(Algorithm.SHA256, clientData)
        val nonceBase64 = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP)

        val context = ContextInitializer.applicationContext

        return suspendCancellableCoroutine { cont ->
            val provider = runCatching { IntegrityWarmupManager.integrityTokenProvider }.getOrNull()

            if (IntegrityWarmupManager.alreadyWarmedUp && provider != null) {
                val request = StandardIntegrityTokenRequest.builder()
                    .setRequestHash(nonceBase64)
                    .build()

                provider.request(request)
                    .addOnSuccessListener { response ->
                        cont.resume(response.token())
                    }
                    .addOnFailureListener { ex ->
                        cont.resumeWithException(
                            ProximityVerificationException(
                                "StandardIntegrityToken failed", ex,
                                code = ProximityVerificationExceptionCode.PVE12()
                            )
                        )
                    }
            } else {
                val integrityManager = IntegrityManagerFactory.create(context)
                val fallbackRequest = IntegrityTokenRequest.builder()
                    .setNonce(nonceBase64)
                    .setCloudProjectNumber(androidCloudProjectNumber.toLong())
                    .build()

                integrityManager.requestIntegrityToken(fallbackRequest)
                    .addOnSuccessListener { response ->
                        cont.resume(response.token())
                    }
                    .addOnFailureListener { ex ->
                        cont.resumeWithException(
                            ProximityVerificationException(
                                "Fallback Play Integrity failed", ex,
                                code = ProximityVerificationExceptionCode.PVE12()
                            )
                        )
                    }
            }
        }
    }
}