package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val platform: String?,
    val appVersion: String?,
    val buildVersion: String?,
    val lang: String?
)
