package it.infocert.proximityverification.nfc

import it.infocert.proximityverification.core.models.engagement.EngagementData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NfcDataModelsTest {

    @Test
    fun testEngagementDataQrCode() {
        val qrBytes = byteArrayOf(1, 2, 3, 4, 5)
        val engagementData = EngagementData.QrCode(qrBytes)
        assertIs<EngagementData.QrCode>(engagementData)
        assertEquals(qrBytes, engagementData.encodedBytes)
    }

    @Test
    fun testEngagementDataSealedClass() {
        val qrEngagement: EngagementData = EngagementData.QrCode(byteArrayOf())
        assertIs<EngagementData>(qrEngagement)
    }

    @Test
    fun testEngagementDataWhenExpression() {
        val qrData = EngagementData.QrCode(byteArrayOf(1, 2, 3))
        val result = when (qrData) {
            is EngagementData.QrCode -> "QR"
            is EngagementData.Nfc -> "NFC"
        }
        assertEquals("QR", result)
    }

    @Test
    fun testEngagementDataQrCodeNotNull() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val qrEngagement = EngagementData.QrCode(bytes)
        assertNotNull(qrEngagement.encodedBytes)
        assertTrue(qrEngagement.encodedBytes.isNotEmpty())
    }

    @Test
    fun testEngagementDataQrCodeEmpty() {
        val emptyBytes = byteArrayOf()
        val qrEngagement = EngagementData.QrCode(emptyBytes)
        assertNotNull(qrEngagement.encodedBytes)
        assertTrue(qrEngagement.encodedBytes.isEmpty())
    }
}
