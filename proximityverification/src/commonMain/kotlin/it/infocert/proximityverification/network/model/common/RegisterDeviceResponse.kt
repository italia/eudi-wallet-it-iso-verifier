package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceResponse(
    val data: RegisterDeviceResponseData? = null,
    val message: RegisterDeviceResponseMessage? = null,
    val timestamp: Long? = null
)


@Serializable
data class RegisterDeviceResponseData(
    val token: String? = null,
    val refreshToken: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null
)

@Serializable
data class RegisterDeviceResponseMessage(
    val json: Map<String, String> = emptyMap()
)
