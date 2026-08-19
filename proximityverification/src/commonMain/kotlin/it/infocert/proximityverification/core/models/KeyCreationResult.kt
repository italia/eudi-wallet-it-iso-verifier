package it.infocert.proximityverification.core.models

import org.multipaz.securearea.KeyInfo

data class KeyCreationResult(
    val keyInfo: KeyInfo,
    val status: KeyCreationStatus
)