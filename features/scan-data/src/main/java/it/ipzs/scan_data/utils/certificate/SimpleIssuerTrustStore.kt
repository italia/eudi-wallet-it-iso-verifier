package it.ipzs.scan_data.utils.certificate

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.LinkedList

internal interface IssuerTrustStore{

    fun createCertificationTrustPath(chain: List<X509Certificate>): List<X509Certificate>?

    fun validateCertificationTrustPath(certificationTrustPath: List<X509Certificate>?): Boolean


}

internal class SimpleIssuerTrustStore(trustedCertificates: List<X509Certificate>): IssuerTrustStore {

    private val DIGITAL_SIGNATURE = 0

    private val trustedCertMap: Map<X500Name, X509Certificate>

    init {
        trustedCertMap = trustedCertificates.associateBy {
            X500Name(it.subjectX500Principal.name)
        }
    }

    override fun createCertificationTrustPath(chain: List<X509Certificate>): List<X509Certificate>? {
        val certificationTrustPath = LinkedList<X509Certificate>()
        val certIterator = chain.listIterator()

        var trustedCert: X509Certificate? = null

        while (certIterator.hasNext()) {
            val currentCert = certIterator.next()
            certificationTrustPath.add(currentCert)
            val x500Name = X500Name(currentCert.issuerX500Principal.name)
            trustedCert = trustedCertMap[x500Name]
            if (trustedCert != null)
                break
        }

        return trustedCert?.let {
            certificationTrustPath.add(trustedCert)
            certificationTrustPath
        }
    }

    override fun validateCertificationTrustPath(certificationTrustPath: List<X509Certificate>?): Boolean {

        if (certificationTrustPath.isNullOrEmpty()) return false

        val certIterator = certificationTrustPath.iterator()

        val leafCert = certIterator.next()

        if (!leafCert.keyUsage[DIGITAL_SIGNATURE]) return false

        try {
            // NOTE throws multiple exceptions derived from CertificateException
            leafCert.checkValidity()
        } catch (e: CertificateException) {
            return false
        }

        var prevCert = leafCert
        var caCert: X509Certificate

        while (certIterator.hasNext()) {
            caCert = certIterator.next()

            val nameCert = X500Name(prevCert.issuerX500Principal.name)
            val nameCA = X500Name(caCert.subjectX500Principal.name)

            if (nameCert != nameCA) {
                return false
            }

            try {
                try {
                    prevCert.verify(caCert.publicKey)
                } catch (e: InvalidKeyException) {
                    // Try to decode certificate using BouncyCastleProvider
                    val factory = CertificateFactory.getInstance("X509", BouncyCastleProvider())
                    val prevCertBC = factory.generateCertificate(ByteArrayInputStream(prevCert.encoded))
                    val caCertBC = factory.generateCertificate(ByteArrayInputStream(caCert.encoded))

                    prevCertBC.verify(caCertBC.publicKey)
                }
            } catch (e: Exception) {
                return false
            }

            prevCert = caCert
        }

        return true

    }


}