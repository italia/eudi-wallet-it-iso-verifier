package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.models.KeyCreationResult
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea

interface ExtendedSecureAreaWithTTL : SecureArea {
    suspend fun createKeyWithTtl(
        alias: String,
        createKeySettings: CreateKeySettings,
        ttlMillis: Long,
        isOnline: Boolean = false
    ): KeyCreationResult
}