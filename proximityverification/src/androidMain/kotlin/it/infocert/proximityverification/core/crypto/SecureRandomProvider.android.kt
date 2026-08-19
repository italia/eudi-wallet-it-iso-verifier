package it.infocert.proximityverification.core.crypto

actual object SecureRandomProvider {
    actual fun nextBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }
}