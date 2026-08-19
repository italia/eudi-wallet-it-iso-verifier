package it.infocert.proximityverification.core.models.engagement

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tstr
import kotlin.test.Test
import kotlin.test.assertEquals

class EngagementTypeTest {

    @Test
    fun fromNfcHandover_shouldReturnStaticHandoverWhenSecondElementIsNull(): Unit {
        val inputHandover = CborArray.builder()
            .add(Tstr("version"))
            .add(Simple.NULL)
            .end()
            .build()
        val actualType: EngagementType = EngagementType.fromNfcHandover(inputHandover)
        assertEquals(EngagementType.NFC_STATIC_HANDOVER, actualType)
    }

    @Test
    fun fromNfcHandover_shouldReturnNegotiatedHandoverWhenSecondElementIsNotNull(): Unit {
        val inputHandover = CborArray.builder()
            .add(Tstr("version"))
            .add(Tstr("negotiated_data"))
            .end()
            .build()
        val actualType: EngagementType = EngagementType.fromNfcHandover(inputHandover)
        assertEquals(EngagementType.NFC_NEGOTIATED_HANDOVER, actualType)
    }
}
