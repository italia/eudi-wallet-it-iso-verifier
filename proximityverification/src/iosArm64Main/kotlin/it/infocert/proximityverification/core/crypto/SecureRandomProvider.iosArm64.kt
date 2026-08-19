package it.infocert.proximityverification.core.crypto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

actual object SecureRandomProvider {
    @OptIn(ExperimentalForeignApi::class)
    actual fun nextBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        val status = SecRandomCopyBytes(kSecRandomDefault, length.toULong(), bytes.refTo(0))
        check(status == 0) { "Failed to generate secure random bytes" }
        return bytes
    }
}