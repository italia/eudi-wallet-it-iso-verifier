package it.ipzs.scan.drivelicense.data.model

import android.graphics.Bitmap
import it.ipzs.scan_data.model.DriveLicenseData

data class DriveLicenseUI(
    val name: String,
    val surname: String,
    val documentNumber: String,
    val releaseDate: String,
    val expirationDate: String,
    val types: String,
    val picture: Bitmap?
){

    companion object{
        fun from(driveLicenseData: DriveLicenseData?) = DriveLicenseUI(
            name = driveLicenseData?.name ?: "",
            surname = driveLicenseData?.surname ?: "",
            documentNumber = driveLicenseData?.documentNumber ?: "",
            releaseDate = driveLicenseData?.parsedReleaseDate ?: "",
            expirationDate = driveLicenseData?.parsedExpirationDate ?: "",
            types = driveLicenseData?.types?.joinToString(" - ") ?: "",
            picture = driveLicenseData?.photo,
        )
    }

}