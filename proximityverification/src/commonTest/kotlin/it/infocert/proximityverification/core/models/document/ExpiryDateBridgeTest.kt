package it.infocert.proximityverification.core.models.document

import it.infocert.proximityverification.core.crypto.DocumentExpiryChecker
import it.infocert.proximityverification.core.models.CheckStatus
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.MdocDataElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Blinda il "ponte" di estrazione di `expiry_date` che vive in
 * [DocumentData.toDocumentVerificationResult] (refactor in [DocumentData.extractExpiryDateRaw]),
 * senza costruire un `MdocDocument` completo. Verifica le due dipendenze fragili:
 *  (a) l'identifier esatto del claim ("expiry_date");
 *  (b) il formato "YYYY-MM-DD" prodotto da `MdocDataElement.renderValue` per
 *      [DocumentAttributeType.Date], che [DocumentExpiryChecker] deve saper parsare.
 */
class ExpiryDateBridgeTest {

    private val now = Instant.parse("2026-06-01T00:00:00Z")

    private fun dateElement(identifier: String): MdocDataElement =
        MdocDataElement(
            attribute = DocumentAttribute(
                type = DocumentAttributeType.Date,
                identifier = identifier,
                displayName = identifier,
                description = identifier,
            ),
            mandatory = false,
        )

    @Test
    fun extractReturnsTextValueWhenKeyPresent() {
        val kvPairs = listOf(
            DocumentKeyValuePair("family_name", "Rossi"),
            DocumentKeyValuePair("expiry_date", "2030-12-31"),
        )
        assertEquals("2030-12-31", DocumentData.extractExpiryDateRaw(kvPairs))
    }

    @Test
    fun extractReturnsNullWhenKeyAbsent() {
        val kvPairs = listOf(DocumentKeyValuePair("family_name", "Rossi"))
        assertNull(DocumentData.extractExpiryDateRaw(kvPairs))
    }

    @Test
    fun renderValueOfFullDateProducesYyyyMmDd() {
        // CBOR full-date (tag 1004), come emesso per `expiry_date` in mDL ISO 18013-5.
        val rendered = dateElement("expiry_date")
            .renderValue(Tagged(Tagged.FULL_DATE_STRING, Tstr("2030-12-31")))
        assertEquals("2030-12-31", rendered)
    }

    @Test
    fun renderedDateFlowsThroughBridgeToPassedCheck() {
        val rendered = dateElement("expiry_date")
            .renderValue(Tagged(Tagged.FULL_DATE_STRING, Tstr("2030-12-31")))
        val kvPairs = listOf(DocumentKeyValuePair("expiry_date", rendered))
        val raw = DocumentData.extractExpiryDateRaw(kvPairs)
        assertEquals("2030-12-31", raw)
        val check = DocumentExpiryChecker.check(expiryDateRaw = raw, now = now)
        assertEquals(CheckStatus.PASSED, check?.status)
    }

    @Test
    fun renderedPastDateFlowsThroughBridgeToFailedCheck() {
        val rendered = dateElement("expiry_date")
            .renderValue(Tagged(Tagged.FULL_DATE_STRING, Tstr("2024-01-01")))
        val check = DocumentExpiryChecker.check(
            expiryDateRaw = DocumentData.extractExpiryDateRaw(listOf(DocumentKeyValuePair("expiry_date", rendered))),
            now = now,
        )
        assertEquals(CheckStatus.FAILED, check?.status)
    }

    @Test
    fun nonDateTextValueIsNotParsableSoCheckIsOmitted() {
        // Se l'elemento non è un Date, il textValue cade su Cbor.toDiagnostics e non è parsabile.
        val kvPairs = listOf(DocumentKeyValuePair("expiry_date", "h'deadbeef'"))
        val raw = DocumentData.extractExpiryDateRaw(kvPairs)
        assertEquals("h'deadbeef'", raw)
        assertNull(DocumentExpiryChecker.check(expiryDateRaw = raw, now = now))
    }
}
