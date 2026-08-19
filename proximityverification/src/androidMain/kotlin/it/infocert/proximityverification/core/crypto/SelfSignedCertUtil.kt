@file:OptIn(ExperimentalTime::class)

package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1
import org.multipaz.asn1.ASN1BitString
import org.multipaz.asn1.ASN1Boolean
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
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.EcPublicKeyOkp
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.AndroidKeystoreSecureArea
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


object SelfSignedCertUtil {
@OptIn(ExperimentalTime::class)
suspend fun AndroidKeystoreSecureArea.generateSelfSignedCertFromKeystore(
        alias: String,
        subject: X500Name
    ): X509Cert {
        val info = getKeyInfo(alias)
        val pubKey = info.publicKey as EcPublicKey
        val validFrom = info.validFrom ?: Instant.fromEpochSeconds(0)
        val validUntil = info.validUntil ?: Instant.fromEpochSeconds(253402300799)
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
            ASN1TaggedObject(ASN1TagClass.CONTEXT_SPECIFIC, ASN1Encoding.CONSTRUCTED,
                0, ASN1.encode(ASN1Integer(2))),
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
}

suspend fun AndroidKeystoreSecureArea.generateRootCaCert(
    alias: String,
    subject: X500Name
): X509Cert {
    val info = getKeyInfo(alias)
    val pubKey = info.publicKey as EcPublicKey
    val validFrom = info.validFrom ?: Clock.System.now()
    val validUntil = info.validUntil ?: validFrom.plus(Duration.parse("1y"))
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
        ASN1TaggedObject(ASN1TagClass.CONTEXT_SPECIFIC, ASN1Encoding.CONSTRUCTED,
            0, ASN1.encode(ASN1Integer(2))),
        serial,
        algo.toSignatureAlgorithmIdentifier(pubKey.curve),
        generateName(subject),
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
            ASN1.encode(
                ASN1Sequence(listOf(
                    ASN1Sequence(listOf(
                        ASN1ObjectIdentifier("2.5.29.19"), // BasicConstraints
                        ASN1Boolean(true), // critical
                        ASN1OctetString(ASN1.encode(
                            ASN1Sequence(listOf(
                                ASN1Boolean(true), // cA = TRUE
                                ASN1Integer(0)     // pathLenConstraint = 0
                            ))
                        ))
                    ))
                ))
            )
        )
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
    return ASN1Sequence(name.components.map { (oid, value) ->
        ASN1Set(listOf(
            ASN1Sequence(listOf(
                ASN1ObjectIdentifier(oid),
                value
            ))
        ))
    })
}

private fun EcCurve.toPublicKeyAlgorithmIdentifier(): ASN1Sequence = when (this) {
    EcCurve.P256 -> oidSeq("1.2.840.10045.2.1", "1.2.840.10045.3.1.7")
    EcCurve.P384 -> oidSeq("1.2.840.10045.2.1", "1.3.132.0.34")
    EcCurve.P521 -> oidSeq("1.2.840.10045.2.1", "1.3.132.0.35")
    EcCurve.BRAINPOOLP256R1 -> oidSeq("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.7")
    EcCurve.BRAINPOOLP320R1 -> oidSeq("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.9")
    EcCurve.BRAINPOOLP384R1 -> oidSeq("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.11")
    EcCurve.BRAINPOOLP512R1 -> oidSeq("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.13")
    EcCurve.ED25519 -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.112")))
    EcCurve.ED448   -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.113")))
    EcCurve.X25519  -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.110")))
    EcCurve.X448    -> ASN1Sequence(listOf(ASN1ObjectIdentifier("1.3.101.111")))
}

private fun oidSeq(algOid: String, paramOid: String): ASN1Sequence = ASN1Sequence(listOf(
    ASN1ObjectIdentifier(algOid),
    ASN1ObjectIdentifier(paramOid)
))

private fun Algorithm.toSignatureAlgorithmIdentifier(curve: EcCurve): ASN1Sequence {
    val oid = when (this) {
        Algorithm.ES256, Algorithm.ESP256, Algorithm.ESB256 -> "1.2.840.10045.4.3.2"
        Algorithm.ES384, Algorithm.ESP384, Algorithm.ESB384, Algorithm.ESB320 -> "1.2.840.10045.4.3.3"
        Algorithm.ES512, Algorithm.ESP512, Algorithm.ESB512 -> "1.2.840.10045.4.3.4"
        Algorithm.EDDSA -> when (curve) {
            EcCurve.ED25519 -> "1.3.101.112"
            EcCurve.ED448 -> "1.3.101.113"
            else -> throw ProximityVerificationException("Unsupported curve $curve for $this",
                code = ProximityVerificationExceptionCode.PVE03())
        }
        else -> throw ProximityVerificationException("Unsupported algorithm $this",
            code = ProximityVerificationExceptionCode.PVE03())
    }
    return ASN1Sequence(listOf(ASN1ObjectIdentifier(oid)))
}

    suspend fun AndroidKeystoreSecureArea.generateCertSignedByRoot(
        deviceKeyAlias: String,
        subject: X500Name,
        rootKeyAlias: String,
        rootCert: X509Cert,
        validityDays: Int = 365
    ): X509Cert {
        val deviceKey = getKeyInfo(deviceKeyAlias).publicKey as EcPublicKey
        val rootKey = getKeyInfo(rootKeyAlias).publicKey as EcPublicKey
        val algo = getKeyInfo(rootKeyAlias).algorithm

        val serial = ASN1Integer(Random.nextLong())
        val validFrom = Clock.System.now()
        val validUntil = validFrom.plus(Duration.parse("${validityDays}d"))

        val devicePublicKeyBytes = when (deviceKey) {
            is EcPublicKeyDoubleCoordinate -> deviceKey.asUncompressedPointEncoding
            is EcPublicKeyOkp -> deviceKey.x
        }

        val publicKeyInfo = ASN1Sequence(listOf(
            deviceKey.curve.toPublicKeyAlgorithmIdentifier(),
            ASN1BitString(0, devicePublicKeyBytes)
        ))

        val subjectKeyId = org.multipaz.crypto.Crypto.digest(Algorithm.INSECURE_SHA1, devicePublicKeyBytes)

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
            ASN1TaggedObject(ASN1TagClass.CONTEXT_SPECIFIC, ASN1Encoding.CONSTRUCTED,
                0, ASN1.encode(ASN1Integer(2))),
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


    suspend fun AndroidKeystoreSecureArea.buildCertChain(deviceCert: X509Cert,
                                                         rootCert: X509Cert): X509CertChain {
        return X509CertChain(listOf(deviceCert, rootCert))
    }

