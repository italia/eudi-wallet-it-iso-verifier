package it.infocert.proximityverification.core.models

import kotlinx.coroutines.test.runTest
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.X509Cert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderCertificateInfoTest {

    // Root self-signed InfoCert "SS IoT ECDSA" (NotAfter 2058) — fixture X509 stabile.
    private val samplePem = """
-----BEGIN CERTIFICATE-----
MIIC4zCCAomgAwIBAgIUJqKbEpSMtD6QGzZ8wMqSUIN5UjkwCgYIKoZIzj0EAwIwVTELMAkGA1UEBhMCSVQxGTAXBgNVBAoMEElvVCBJbmZvQ2VydCBTcEExFDASBgNVBAUTCzA3OTQ1MjExMDA2MRUwEwYDVQQDDAxTUyBJb1QgRUNEU0EwIBcNMjMwMTIzMTYwMTQxWhgPMjA1ODAxMjMxNzAxNDFaMFUxCzAJBgNVBAYTAklUMRkwFwYDVQQKDBBJb1QgSW5mb0NlcnQgU3BBMRQwEgYDVQQFEwswNzk0NTIxMTAwNjEVMBMGA1UEAwwMU1MgSW9UIEVDRFNBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECwrwSnCOWlMlldkpd4jPAQ9jKi/uDjx9UXgD6cCCwTihkD8m32ypL9cd/DCQeG/cT5Eod7OmajnhTzCNORHQG6OCATMwggEvMA8GA1UdEwEB/wQFMAMBAf8wgYoGCCsGAQUFBwEBBH4wfDAsBggrBgEFBQcwAYYgaHR0cDovL2lvdC1vY3NwLmluZm9jZXJ0LmRpZ2l0YWwwTAYIKwYBBQUHMAKGQGh0dHA6Ly9pb3QuaW5mb2NlcnQuZGlnaXRhbC9jYS9zZWxmc2lnbmVkL2VjZHNhL1NTX0lvVF9FQ0RTQS5jcnQwEgYDVR0gBAswCTAHBgUrBgEEAjBMBgNVHR8ERTBDMEGgP6A9hjtodHRwOi8vaW90LmluZm9jZXJ0LmRpZ2l0YWwvY3JsL3NlbGZzaWduZWQvZWNkc2EvY2EvQ1JMLmNybDAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFNRpUVlZfiFynkPiuProhZrst02CMAoGCCqGSM49BAMCA0gAMEUCIQC8jPcmBcUpuSpRqWxi6vH/Iy8PbZsFMQOUgolQzR5CXgIgaC8NzOgOtFl3dZ2PzP5Splz1IRN1LzqYKoqawDRuANI=
-----END CERTIFICATE-----
""".trimIndent()

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun from_mapsAllFieldsFromCertificate() = runTest {
        val cert = X509Cert.fromPem(samplePem)
        val info = ReaderCertificateInfo.from(cert)

        assertEquals("SS IoT ECDSA", info.subjectCommonName)
        assertEquals(cert.subject.name, info.subjectDn)
        assertEquals("SS IoT ECDSA", info.issuerCommonName) // self-signed
        assertEquals(cert.issuer.name, info.issuerDn)
        assertEquals(
            Crypto.digest(Algorithm.SHA256, cert.encoded.toByteArray()).toHexString(),
            info.sha256Fingerprint
        )
        assertTrue(info.serialNumber.isNotEmpty())
        assertFalse(info.isExpired) // valido fino al 2058
        assertTrue(info.notAfter > info.notBefore)
        // PEM round-trip: si riparsa allo stesso subject.
        assertEquals(cert.subject.name, X509Cert.fromPem(info.pem).subject.name)
    }
}
