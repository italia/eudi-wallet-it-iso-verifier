package it.infocert.proximityverification.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String
)