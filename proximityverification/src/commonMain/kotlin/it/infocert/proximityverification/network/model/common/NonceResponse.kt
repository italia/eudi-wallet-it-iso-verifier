package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.Serializable

@Serializable
data class NonceResponse(
    val nonce: String
)