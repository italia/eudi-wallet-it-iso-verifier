package it.infocert.proximityverification

import it.infocert.proximityverification.core.crypto.TrustUtils.fetchCrlBytes
import it.infocert.proximityverification.core.crypto.TrustUtils.isCertRevoked
import it.infocert.proximityverification.core.crypto.TrustUtils.loadPemCert
import kotlinx.coroutines.runBlocking
import org.multipaz.crypto.X509Cert
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustUtilsTest {

    private val revokedCert = """
        -----BEGIN CERTIFICATE-----
        MIIDXzCCAwSgAwIBAgIUBrf/+p/d9Eqgya2CL5kLrfPGbt8wCgYIKoZIzj0EAwIw
        aTELMAkGA1UEBhMCSVQxGTAXBgNVBAoMEElvVCBJbmZvQ2VydCBTcEExFDASBgNV
        BAUTCzA3OTQ1MjExMDA2MSkwJwYDVQQDDCBJb1QgU0hBUkVEIERldmljZSBJbmZv
        Q2VydCBFQ0RTQTAeFw0yNTAzMjcxNDE4MTlaFw0zMDAzMjcxNDE4MTlaMGUxCzAJ
        BgNVBAYTAklUMQ0wCwYDVQQIDARSb21lMQ0wCwYDVQQHDARSb21lMQwwCgYDVQQK
        DANIUEUxDDAKBgNVBAsMA0hQRTEcMBoGA1UEAwwTdGVsZWdyYWYuZWRnZS5wYW5l
        bDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEiwoKSEphhbeoK/TuiiclcJf88k
        cNSZUijwnQi0zdZBTfgmaZh7sOsNBWKJHwpOEKLqIYJiA/y5SvnmF02G5DWjggGM
        MIIBiDCBrAYIKwYBBQUHAQEEgZ8wgZwwMwYIKwYBBQUHMAGGJ2h0dHA6Ly9pb3Qt
        b2NzcC5pbmZvY2VydC5kaWdpdGFsL3NoYXJlZDBlBggrBgEFBQcwAoZZaHR0cDov
        L2lvdC5pbmZvY2VydC5kaWdpdGFsL2NhL2luZm9jZXJ0L2RldmljZS9lY2RzYS9J
        b1RfU0hBUkVEX0RldmljZV9JbmZvQ2VydF9FQ0RTQS5jcnQwFQYDVR0gBA4wDDAK
        BggrBgEEAgIBBDBQBgNVHR8ESTBHMEWgQ6BBhj9odHRwOi8vaW90LmluZm9jZXJ0
        LmRpZ2l0YWwvY3JsL2luZm9jZXJ0L2RldmljZS9lY2RzYS9DUkwwMS5jcmwwDgYD
        VR0PAQH/BAQDAgWgMB4GA1UdEQQXMBWCE3RlbGVncmFmLmVkZ2UucGFuZWwwHwYD
        VR0jBBgwFoAUGVZ8mF8XjPkLS2RiqvI4A60NSTowHQYDVR0OBBYEFB/SJJW/uik4
        Vi77hfxn4Zpc45QmMAoGCCqGSM49BAMCA0kAMEYCIQCcISpCA2iY3IQGClCX/FJH
        ShMy1f1Bto7AXnWngpacXQIhAN1Fn7iK+Vj/KFBIpirycgSTRGfgEftlaEw6HFS4
        n453
        -----END CERTIFICATE-----
    """.trimIndent()

    private val c2 = """
        -----BEGIN CERTIFICATE-----
        MIICKTCCAc+gAwIBAgIUe+Je2BvksVFxOAKdUc+qx8yaG1AwCgYIKoZIzj0EAwIw
        YzELMAkGA1UEBhMCSVQxGTAXBgNVBAoMEElvVCBJbmZvQ2VydCBTcEExIzAhBgNV
        BAMMGklvVCBTSEFSRUQgSVBaUyBDQk9SIEVDRFNBMRQwEgYDVQQFEwswNzk0NTIx
        MTAwNjAeFw0yNTA2MjAwNzQ5MTZaFw0zMDA2MTkwNzQ5MTVaMEUxFTATBgNVBAMM
        DENCT1IgU2VydmljZTELMAkGA1UEBhMCSVQxDDAKBgNVBAsMA0lvVDERMA8GA1UE
        CgwISW5mb0NlcnQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQtNm8fzZv1Ugn5
        rKmOfZapPU3zjcxCeWTMTl3eqSSRtdrkIIGVYdh98FQqYs+ZmrUeYw7cw93cLX3J
        4knVfHQzo38wfTAOBgNVHQ8BAf8EBAMCBLAwDAYDVR0TAQH/BAIwADAfBgNVHSME
        GDAWgBQJBaZx/EgY+A5hy/vuQ4ue50zVYDAdBgNVHSUEFjAUBggrBgEFBQcDAgYI
        KwYBBQUHAwEwHQYDVR0OBBYEFAhZPgbjG3jyBVFIaV05uiSZBK2kMAoGCCqGSM49
        BAMCA0gAMEUCICS8XLK04NIdEnlozQfULDuwSsEsqPkn9Y+aR3WpJHIlAiEA0/yd
        LjP/vgSvupVjhOOjTmO/3juCECK3yHlCHoPDn5Y=
        -----END CERTIFICATE-----
    """.trimIndent()

    private val c1 = """
        -----BEGIN CERTIFICATE-----
        MIIDHzCCAsSgAwIBAgIUPsFiNnP66t2AfJXPUtm0faqDNagwCgYIKoZIzj0EAwIw
        WTELMAkGA1UEBhMCSVQxGTAXBgNVBAoMEElvVCBJbmZvQ2VydCBTcEExFDASBgNV
        BAUTCzA3OTQ1MjExMDA2MRkwFwYDVQQDDBBJb1QgU0hBUkVEIEVDRFNBMB4XDTI1
        MDYxODEzMzEwNVoXDTQwMDYxODEzMzEwNVowYzELMAkGA1UEBhMCSVQxGTAXBgNV
        BAoMEElvVCBJbmZvQ2VydCBTcEExIzAhBgNVBAMMGklvVCBTSEFSRUQgSVBaUyBD
        Qk9SIEVDRFNBMRQwEgYDVQQFEwswNzk0NTIxMTAwNjBZMBMGByqGSM49AgEGCCqG
        SM49AwEHA0IABNTXy/s4NqxIEZxArLd7l2yMdLdA7r9G8YqpKRPUCu8e8QmZstGG
        Zh17G94tNeWgklgpMQkQyJA0DhYX+rs8zOqjggFeMIIBWjASBgNVHRMBAf8ECDAG
        AQH/AgEAMBQGA1UdIAQNMAswCQYHKwYBBAICATCBkwYIKwYBBQUHAQEEgYYwgYMw
        MwYIKwYBBQUHMAGGJ2h0dHA6Ly9pb3Qtb2NzcC5pbmZvY2VydC5kaWdpdGFsL3No
        YXJlZDBMBggrBgEFBQcwAoZAaHR0cDovL2lvdC5pbmZvY2VydC5kaWdpdGFsL2Nh
        L3NoYXJlZC9lY2RzYS9Jb1RfU0hBUkVEX0VDRFNBLmNydDBIBgNVHR8EQTA/MD2g
        O6A5hjdodHRwOi8vaW90LmluZm9jZXJ0LmRpZ2l0YWwvY3JsL3NoYXJlZC9lY2Rz
        YS9jYS9DUkwuY3JsMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUCQWmcfxIGPgO
        Ycv77kOLnudM1WAwHwYDVR0jBBgwFoAUUpJ1l86Slm1CpTnHdakkwvADLG8wCgYI
        KoZIzj0EAwIDSQAwRgIhAIc9q9FSz19Dm//HvO8uRU24KlwGLSbPuD4Bb0+aeDJp
        AiEAlnmjEe9CIWPgK/9EzFMltMXyWRXn0cjU7Y3iSSXn7Hw=
        -----END CERTIFICATE-----
    """.trimIndent()

    private val c0 = """
        -----BEGIN CERTIFICATE-----
        MIIDfjCCAySgAwIBAgIUcBAuUJQU9aS4U6tAY9bfj27FINYwCgYIKoZIzj0EAwIw
        VTELMAkGA1UEBhMCSVQxGTAXBgNVBAoMEElvVCBJbmZvQ2VydCBTcEExFDASBgNV
        BAUTCzA3OTQ1MjExMDA2MRUwEwYDVQQDDAxTUyBJb1QgRUNEU0EwHhcNMjMwMTI0
        MTIxNjM2WhcNNDgwMTI0MTIxNjM2WjBZMQswCQYDVQQGEwJJVDEZMBcGA1UECgwQ
        SW9UIEluZm9DZXJ0IFNwQTEUMBIGA1UEBRMLMDc5NDUyMTEwMDYxGTAXBgNVBAMM
        EElvVCBTSEFSRUQgRUNEU0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATNL9mA
        p7gd60TvTPcYbUXo2/Z5dzlmIubmdoNuhmd3Ivctajz1XdSpckOMzJ1tABe6QsG1
        sY9R3L+f1YucEZm5o4IBzDCCAcgwgYoGCCsGAQUFBwEBBH4wfDAsBggrBgEFBQcw
        AYYgaHR0cDovL2lvdC1vY3NwLmluZm9jZXJ0LmRpZ2l0YWwwTAYIKwYBBQUHMAKG
        QGh0dHA6Ly9pb3QuaW5mb2NlcnQuZGlnaXRhbC9jYS9zZWxmc2lnbmVkL2VjZHNh
        L1NTX0lvVF9FQ0RTQS5jcnQwEgYDVR0TAQH/BAgwBgEB/wIBATATBgNVHSAEDDAK
        MAgGBisGAQQCAjBMBgNVHR8ERTBDMEGgP6A9hjtodHRwOi8vaW90LmluZm9jZXJ0
        LmRpZ2l0YWwvY3JsL3NlbGZzaWduZWQvZWNkc2EvY2EvQ1JMLmNybDAOBgNVHQ8B
        Af8EBAMCAQYwHQYDVR0OBBYEFFKSdZfOkpZtQqU5x3WpJMLwAyxvMIGSBgNVHSME
        gYowgYeAFNRpUVlZfiFynkPiuProhZrst02CoVmkVzBVMQswCQYDVQQGEwJJVDEZ
        MBcGA1UECgwQSW9UIEluZm9DZXJ0IFNwQTEUMBIGA1UEBRMLMDc5NDUyMTEwMDYx
        FTATBgNVBAMMDFNTIElvVCBFQ0RTQYIUJqKbEpSMtD6QGzZ8wMqSUIN5UjkwCgYI
        KoZIzj0EAwIDSAAwRQIgW+U5dllEMvM+HnbZCwPCeN3uZ3dmy2fnH/nGBOwqGakC
        IQD+1B9AIccFByLex35ImPfhSxI7d6mIyjbx9/eI+Cx5Yw==
        -----END CERTIFICATE-----
    """.trimIndent()

    private val root = """
        -----BEGIN CERTIFICATE-----
        MIIC4zCCAomgAwIBAgIUJqKbEpSMtD6QGzZ8wMqSUIN5UjkwCgYIKoZIzj0EAwIwVTELMAkGA1UEBhMCSVQxGTAXBgNVBAoMEElvVCBJbmZvQ2VydCBTcEExFDASBgNVBAUTCzA3OTQ1MjExMDA2MRUwEwYDVQQDDAxTUyBJb1QgRUNEU0EwIBcNMjMwMTIzMTYwMTQxWhgPMjA1ODAxMjMxNzAxNDFaMFUxCzAJBgNVBAYTAklUMRkwFwYDVQQKDBBJb1QgSW5mb0NlcnQgU3BBMRQwEgYDVQQFEwswNzk0NTIxMTAwNjEVMBMGA1UEAwwMU1MgSW9UIEVDRFNBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECwrwSnCOWlMlldkpd4jPAQ9jKi/uDjx9UXgD6cCCwTihkD8m32ypL9cd/DCQeG/cT5Eod7OmajnhTzCNORHQG6OCATMwggEvMA8GA1UdEwEB/wQFMAMBAf8wgYoGCCsGAQUFBwEBBH4wfDAsBggrBgEFBQcwAYYgaHR0cDovL2lvdC1vY3NwLmluZm9jZXJ0LmRpZ2l0YWwwTAYIKwYBBQUHMAKGQGh0dHA6Ly9pb3QuaW5mb2NlcnQuZGlnaXRhbC9jYS9zZWxmc2lnbmVkL2VjZHNhL1NTX0lvVF9FQ0RTQS5jcnQwEgYDVR0gBAswCTAHBgUrBgEEAjBMBgNVHR8ERTBDMEGgP6A9hjtodHRwOi8vaW90LmluZm9jZXJ0LmRpZ2l0YWwvY3JsL3NlbGZzaWduZWQvZWNkc2EvY2EvQ1JMLmNybDAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFNRpUVlZfiFynkPiuProhZrst02CMAoGCCqGSM49BAMCA0gAMEUCIQC8jPcmBcUpuSpRqWxi6vH/Iy8PbZsFMQOUgolQzR5CXgIgaC8NzOgOtFl3dZ2PzP5Splz1IRN1LzqYKoqawDRuANI=
        -----END CERTIFICATE-----
    """.trimIndent()

    @Test
    fun testCertificateIsNotRevoked() = runBlocking {
        for (certPem in listOf(c0, c1, c2, root)) {
            val cert = loadPemCert(certPem)
            val revoked = isCertRevoked(cert)
            println("Revocation status for certificate (should be false): $revoked")
            assertFalse(revoked, "Certificate should not be revoked")
        }
    }

    @Test
    fun testSingleCertIsNotRevoked() = runBlocking {
        val cert = loadPemCert(c0) // cannot test c2, the leaf, because it does not have crl
        val revoked = isCertRevoked(cert)
        println("Revocation status (should be true): $revoked")
        assertFalse(revoked, "Certificate should be revoked")
    }

    @Test
    fun testCertificateIsRevoked() = runBlocking {
        val cert = loadPemCert(revokedCert)
        val revoked = isCertRevoked(cert)
        println("Revocation status (should be true): $revoked")
        assertTrue(revoked, "Certificate should be revoked")
    }

    @Test
    fun testFetchCrlBytes() = runBlocking {
        val bytes = fetchCrlBytes("http://iot.infocert.digital/crl/selfsigned/ecdsa/ca/CRL.crl")
        println("CRL fetched, size: ${bytes.size} bytes")
    }
}