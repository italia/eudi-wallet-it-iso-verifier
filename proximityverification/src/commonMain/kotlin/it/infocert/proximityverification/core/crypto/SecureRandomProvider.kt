package it.infocert.proximityverification.core.crypto

expect object SecureRandomProvider {
    fun nextBytes(length: Int): ByteArray
}