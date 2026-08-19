package it.infocert.proximityverification.core.trust

import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate


object JwkUtils {

    fun ecPublicKeyToJwk(pub: EcPublicKey, crv: String = "P-256", kty: String = "EC"): Jwk {
        require(pub is EcPublicKeyDoubleCoordinate) {
            "ECPublicKey must be EcPublicKeyDoubleCoordinate"
        }
        val uncompressed = pub.asUncompressedPointEncoding
        require(uncompressed.isNotEmpty() && uncompressed[0] == 0x04.toByte()) {
            "EC public key must be uncompressed (starts with 0x04)"
        }
        val coordLen = (uncompressed.size - 1) / 2
        val x = uncompressed.sliceArray(1 until (1 + coordLen))
        val y = uncompressed.sliceArray((1 + coordLen) until uncompressed.size)
        return Jwk(
            kty = kty,
            crv = crv,
            x = x.encodeBase64UrlNoPadding(),
            y = y.encodeBase64UrlNoPadding()
        )
    }
}