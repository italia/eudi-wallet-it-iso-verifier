package it.ipzs.network.model.response

import com.google.gson.annotations.SerializedName


data class OCSPResponse(
    @SerializedName("serialNumber") val serialNumber : Long,
    @SerializedName("isRevoked") val isRevoked : Boolean,
    @SerializedName("withdrawnDate") val withdrawnDate : String,
    @SerializedName("cause") val cause :String
)