package it.infocert.proximityverification.core.trust

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import it.infocert.proximityverification.core.crypto.LocalSecureAreaFactory
import it.infocert.proximityverification.core.models.initialization.CSRParam
import it.infocert.proximityverification.logging.Logger
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.EcSignature
import org.multipaz.util.toBase64Url

object CSRCreator {

    private const val TAG = "CSRCreator"

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun createCsr(
        csrParam: CSRParam,
        pubKey: EcPublicKey,
        alg: Algorithm = Algorithm.ESP256,
        crv: String = "P-256",
        keyAlias: String
    ): ByteArray {
        val mn = "createCsr"

        Logger.info(TAG) { "$mn - Start" }

        var signatureAlgorithm: X509SignatureAlgorithm? = null
        var ecCurve: ECCurve? = null

        //TODO (Future use) : Map others Algorithm (multipaz) type to X509SignatureAlgorithm (signum) type.
        // Map ECCurve (multipaz) to ECCurve (signum)
        // for now we assume that P-256 is the only choice...

        /*        if (alg == Algorithm.ESP256) {
                    signatureAlgorithm = X509SignatureAlgorithm.ES256
                    ecCurve = ECCurve.SECP_256_R_1
                } else {
                    ...........
                }*/

        signatureAlgorithm = X509SignatureAlgorithm.ES256
        ecCurve = ECCurve.SECP_256_R_1

        require(pubKey is EcPublicKeyDoubleCoordinate) {
            "ECPublicKey must be EcPublicKeyDoubleCoordinate"
        }

        Logger.info(TAG) { "$mn - Preparing uncompressed key from key alias: $keyAlias..." }

        val uncompressed = pubKey.asUncompressedPointEncoding

        require(uncompressed.isNotEmpty() && uncompressed[0] == 0x04.toByte()) {
            "EC public key must be uncompressed (starts with 0x04)"
        }
        val coordLen = (uncompressed.size - 1) / 2

        val x = uncompressed.sliceArray(1 until (1 + coordLen))
        val y = uncompressed.sliceArray((1 + coordLen) until uncompressed.size)
        val cryptoPublicKey = CryptoPublicKey.EC.fromUncompressed(ecCurve, x, y)

        Logger.info(TAG) { "$mn - Uncompressed key created" }

        Logger.info(TAG) { "$mn - Preparing TbsCertificationRequest..." }

        val tbsCSR = TbsCertificationRequest(
            version = 0,
            subjectName = listOf(
                RelativeDistinguishedName(
                    AttributeTypeAndValue.CommonName(
                        Asn1String.UTF8(csrParam.commonName)
                    )
                ),
                RelativeDistinguishedName(
                    AttributeTypeAndValue.Organization(
                        Asn1String.UTF8(csrParam.organization)
                    )
                ),
                RelativeDistinguishedName(
                    AttributeTypeAndValue.OrganizationalUnit(
                        Asn1String.UTF8(csrParam.organizationalUnit)
                    )
                ),
                RelativeDistinguishedName(
                    AttributeTypeAndValue.OrganizationalUnit(
                        Asn1String.UTF8(csrParam.country)
                    )
                )
            ),
            publicKey = cryptoPublicKey
        )

        Logger.info(TAG) { "$mn - TbsCertificationRequest created" }

        Logger.info(TAG) { "$mn - Preparing hardware signature for TbsCertificationRequest..." }

        val signedCSR: EcSignature =
            LocalSecureAreaFactory.createLocalSecureAreaProvider().get().sign(
                alias = keyAlias,
                dataToSign = tbsCSR.encodeToDer()
            )

        Logger.info(TAG) { "$mn - Hardware signature for TbsCertificationRequest created" }

        //Logger.info(TAG) { "$mn - Value for ec signature: $signedCSR" }

        Logger.info(TAG) { "$mn - Preparing Pkcs10CertificationRequest..." }

        val csr = Pkcs10CertificationRequest(
            tbsCSR,
            signatureAlgorithm,
            CryptoSignature.decodeFromDer(signedCSR.toDerEncoded())
        )

        //Logger.info(TAG) { "$mn - Value for Pkcs10CertificationRequest: $csr" }

        Logger.info(TAG) { "$mn - Pkcs10CertificationRequest created" }

        Logger.debug(TAG) { "$mn - DER Encoded csr as Base64Url: ${csr.encodeToDer().toBase64Url()}" }

        return csr.encodeToDer()
    }
}