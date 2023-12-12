package it.ipzs.scan_data.utils.certificate

import android.content.Context
import it.ipzs.scan_data.R
import it.ipzs.scan_data.utils.certificate.CertificateGenerator.generateCertificate
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.CertIOException
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.Optional

object ReaderCertificateGenerator {
    fun generateECDSAKeyPair(curve: String): KeyPair {
        return try {
            // NOTE older devices may not have the right BC installed for this to work
            val kpg: KeyPairGenerator
            if (listOf("Ed25519", "Ed448").any { it.equals(curve, ignoreCase = true) }) {
                kpg = KeyPairGenerator.getInstance(curve, BouncyCastleProvider())
            } else {
                kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
                kpg.initialize(ECGenParameterSpec(curve))
            }
            println(kpg.provider.info)
            kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw RuntimeException(e)
        }
    }

    fun createReaderCertificate(
        dsKeyPair: KeyPair, issuerCert: X509Certificate,
        issuerPrivateKey: PrivateKey
    ): X509Certificate {
        val data = DataMaterial(
            subjectDN = "C=UT, CN=Google mDoc Reader",

            // must match DN of issuer character-by-character
            // TODO change for other generators
            issuerDN = issuerCert.subjectX500Principal.name,
            // reorders string, do not use
            // return issuerCert.getSubjectX500Principal().getName();

            // NOTE always interpreted as URL for now
            issuerAlternativeName = Optional.of("https://www.google.com/")
        )
        val certData = CertificateMaterial(
            // TODO change
            serialNumber = BigInteger("476f6f676c655f546573745f44535f31", 16),
            startDate = EncodingUtil.parseShortISODate("2023-01-01"),
            endDate = EncodingUtil.parseShortISODate("2024-01-01"),
            pathLengthConstraint = CertificateMaterial.PATHLENGTH_NOT_A_CA,
            keyUsage = KeyUsage.digitalSignature,
            // TODO change for reader cert
            extendedKeyUsage = Optional.of("1.0.18013.5.1.6")
        )

        val keyData = KeyMaterial(
            publicKey = dsKeyPair.public,
            signingAlgorithm = "SHA384WithECDSA",
            signingKey = issuerPrivateKey,
            issuerCertificate = Optional.of(issuerCert)
        )

        // C.1.7.2
        return generateCertificate(data, certData, keyData)
    }
}

data class DataMaterial(
    val subjectDN: String,
    val issuerDN: String,
    val issuerAlternativeName: Optional<String>
)

data class CertificateMaterial(
    val serialNumber: BigInteger,
    val startDate: Date,
    val endDate: Date,
    val keyUsage: Int,
    val extendedKeyUsage: Optional<String>,
    val pathLengthConstraint: Int
) {
    companion object {
        const val PATHLENGTH_NOT_A_CA = -1
    }
}

data class KeyMaterial(
    val publicKey: PublicKey,
    val signingAlgorithm: String,
    val issuerCertificate: Optional<X509Certificate>,
    val signingKey: PrivateKey
)

object EncodingUtil {
    private val SHORT_ISO_DATEFORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    fun parseShortISODate(date: String): Date {
        return try {
            SHORT_ISO_DATEFORMAT.parse(date)!!
        } catch (e: ParseException) {
            throw RuntimeException(e)
        }
    }
}

object CertificateGenerator {
    private const val CRITICAL = true
    private const val NOT_CRITICAL = false
    @JvmStatic
    @Throws(CertIOException::class, CertificateException::class, OperatorCreationException::class)
    fun generateCertificate(
        data: DataMaterial,
        certMaterial: CertificateMaterial,
        keyMaterial: KeyMaterial
    ): X509Certificate {
        val issuerCert: Optional<X509Certificate> = keyMaterial.issuerCertificate
        val subjectDN = X500Name(data.subjectDN)
        // doesn't work, get's reordered
        // issuerCert.isPresent() ? new X500Name(issuerCert.get().getSubjectX500Principal().getName()) : subjectDN;
        val issuerDN = X500Name(data.issuerDN)
        val contentSigner =
            JcaContentSignerBuilder(keyMaterial.signingAlgorithm).build(keyMaterial.signingKey)
        val certBuilder = JcaX509v3CertificateBuilder(
            issuerDN,
            certMaterial.serialNumber,
            certMaterial.startDate, certMaterial.endDate,
            subjectDN,
            keyMaterial.publicKey
        )


        // Extensions --------------------------
        val jcaX509ExtensionUtils = JcaX509ExtensionUtils()
        if (issuerCert.isPresent) {
            try {
                // adds 3 more fields, not present in other cert
                //				AuthorityKeyIdentifier authorityKeyIdentifier = jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get());
                val authorityKeyIdentifier =
                    jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get().publicKey)
                certBuilder.addExtension(
                    Extension.authorityKeyIdentifier,
                    NOT_CRITICAL,
                    authorityKeyIdentifier
                )
            } catch (e: IOException) { // CertificateEncodingException |
                throw RuntimeException(e)
            }
        }
        val subjectKeyIdentifier: SubjectKeyIdentifier =
            jcaX509ExtensionUtils.createSubjectKeyIdentifier(keyMaterial.publicKey)
        certBuilder.addExtension(Extension.subjectKeyIdentifier, NOT_CRITICAL, subjectKeyIdentifier)
        val keyUsage = KeyUsage(certMaterial.keyUsage)
        certBuilder.addExtension(Extension.keyUsage, CRITICAL, keyUsage)

        // IssuerAlternativeName
        val issuerAlternativeName: Optional<String> = data.issuerAlternativeName
        if (issuerAlternativeName.isPresent) {
            val issuerAltName = GeneralNames(
                GeneralName(
                    GeneralName.uniformResourceIdentifier,
                    issuerAlternativeName.get()
                )
            )
            certBuilder.addExtension(Extension.issuerAlternativeName, NOT_CRITICAL, issuerAltName)
        }

        // Basic Constraints
        val pathLengthConstraint: Int = certMaterial.pathLengthConstraint
        if (pathLengthConstraint != CertificateMaterial.PATHLENGTH_NOT_A_CA) {
            // TODO doesn't work for certificate chains != 2 in size
            val basicConstraints = BasicConstraints(pathLengthConstraint)
            certBuilder.addExtension(Extension.basicConstraints, CRITICAL, basicConstraints)
        }
        val extendedKeyUsage: Optional<String> = certMaterial.extendedKeyUsage
        if (extendedKeyUsage.isPresent) {
            val keyPurpose = KeyPurposeId.getInstance(ASN1ObjectIdentifier(extendedKeyUsage.get()))
            val extKeyUsage = ExtendedKeyUsage(arrayOf(keyPurpose))
            certBuilder.addExtension(Extension.extendedKeyUsage, CRITICAL, extKeyUsage)
        }

        // DEBUG setProvider(bcProvider) removed before getCertificate
        return JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))
    }
}

object KeysAndCertificates {
    private fun getKeyBytes(keyInputStream: InputStream): ByteArray {
        val keyBytes = keyInputStream.readBytes()
        val publicKeyPEM = String(keyBytes, StandardCharsets.US_ASCII)
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")

        return Base64.getDecoder().decode(publicKeyPEM)
    }

    private fun getPrivateKey(context: Context, resourceId: Int): PrivateKey {
        val keyBytes: ByteArray = getKeyBytes(context.resources.openRawResource(resourceId))
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(spec)
    }

    private fun getCertificate(context: Context, resourceId: Int): X509Certificate {
        val certInputStream = context.resources.openRawResource(resourceId)
        val factory: CertificateFactory = CertificateFactory.getInstance("X509")
        return factory.generateCertificate(certInputStream) as X509Certificate
    }

    fun getRAGooglePrivateKey(context: Context) =
        getPrivateKey(context, R.raw.google_mdl_ra_privkey)

    fun getRAGoogleCertificate(context: Context) =
        getCertificate(context, R.raw.google_mdl_ra_cert)

    fun getGoogleReaderCA(context: Context) =
        getCertificate(context, R.raw.google_reader_ca)

    fun getTrustedIssuerCertificates(context: Context) =
        listOf(
            getCertificate(context, R.raw.apple_iaca),
            getCertificate(context, R.raw.bdr_iaca),
            getCertificate(context, R.raw.cacert_youniqx),
            getCertificate(context, R.raw.cbn_iaca),
            getCertificate(context, R.raw.cbn_iaca_ky),
            getCertificate(context, R.raw.cbn_interop_aus),
            getCertificate(context, R.raw.cbn_interop_aus_2),
            getCertificate(context, R.raw.clear_iaca_root_cert),
            getCertificate(context, R.raw.credenceid_mdl_iaca_root),
            getCertificate(context, R.raw.fast_google_root_certificate),
            getCertificate(context, R.raw.google_mdl_iaca_cert),
            getCertificate(context, R.raw.google_mdl_iaca_cert_2),
            getCertificate(context, R.raw.google_mekb_iaca_cert),
            getCertificate(context, R.raw.google_micov_csca_cert),
            getCertificate(context, R.raw.google_reader_ca),
            getCertificate(context, R.raw.hidtestcscamicov_cert),
            getCertificate(context, R.raw.hidtestiacamdl_cert),
            getCertificate(context, R.raw.hidtestiacamekb_cert),
            getCertificate(context, R.raw.iaca_nec_mdl_iaca_cert),
            getCertificate(context, R.raw.iaca_utms),
            getCertificate(context, R.raw.iaca_utms_cer),
            getCertificate(context, R.raw.iaca_zetes),
            getCertificate(context, R.raw.iaca_zetes_cer),
            getCertificate(context, R.raw.idemia_brisbane_interop_iaca),
            getCertificate(context, R.raw.iso_iaca_tmr_cer),
            getCertificate(context, R.raw.louisiana_department_of_motor_vehicles_cert),
            getCertificate(context, R.raw.mdl_iaca_thales_multicert),
            getCertificate(context, R.raw.pid_iaca_int_gen_01_cer),
            getCertificate(context, R.raw.rdw_mekb_testset),
            getCertificate(context, R.raw.samsung_iaca_test_cert_cer),
            getCertificate(context, R.raw.scytales_root_ca),
            getCertificate(context, R.raw.spruce_iaca_cert),
            getCertificate(context, R.raw.thalesinterop2022iaca),
            getCertificate(context, R.raw.ul_cert_iaca_01),
            getCertificate(context, R.raw.ul_cert_iaca_01_cer),
            getCertificate(context, R.raw.ul_cert_iaca_02),
            getCertificate(context, R.raw.ul_cert_iaca_02_cer),
            getCertificate(context, R.raw.ul_micov_testset),
        )

}