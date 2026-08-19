package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceData(
    val keyID: String,
    val attestationObject: String,
    val challengeNonce: String
)
