package it.ipzs.network.model.response

import it.ipzs.network.PEMFormattedString


data class CRLResponse(
    val crl: PEMFormattedString
)