package it.infocert.proximityverification.core.trust


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Jwk(
    @SerialName("kty") val kty: String,
    @SerialName("crv") val crv: String,
    @SerialName("x") val x: String,
    @SerialName("y") val y: String
)