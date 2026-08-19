package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.trust.encodeBase64UrlNoPadding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface HardwareKeyTagGenerator {
    fun generateRandomTag(): String
}

object DefaultHardwareKeyTagGenerator : HardwareKeyTagGenerator {
    override fun generateRandomTag(): String {
        val bytes = SecureRandomProvider.nextBytes(32)
        return bytes.encodeBase64UrlNoPadding()
    }
}