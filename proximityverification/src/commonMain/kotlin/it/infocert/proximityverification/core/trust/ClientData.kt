package it.infocert.proximityverification.core.trust

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientData(
    @SerialName("nonce")
    val nonce: String,
    @SerialName("jwk_thumbprint")
    val jwkThumbprint: String
)