package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val data: RegisterDeviceData?,
    @SerialName("device_info")
    val deviceInfo: DeviceInfo
)
