package it.infocert.proximityverification.network.model.common

import kotlinx.serialization.Serializable

@Serializable
data class AccessCertificateResponse(
    val access_certificate: String
)