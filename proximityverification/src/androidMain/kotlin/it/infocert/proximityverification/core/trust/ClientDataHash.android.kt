package it.infocert.proximityverification.core.trust

import java.security.MessageDigest

actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)