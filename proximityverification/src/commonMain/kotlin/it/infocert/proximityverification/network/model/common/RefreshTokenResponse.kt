package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenResponse(
    val data: RefreshTokenResponseData? = null,
    val message: RefreshTokenResponseMessage? = null,
    val timestamp: Long? = null
)

@Serializable
data class RefreshTokenResponseData(
    val token: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null
)

@Serializable
data class RefreshTokenResponseMessage(
    val json: Map<String, String> = emptyMap()
)
