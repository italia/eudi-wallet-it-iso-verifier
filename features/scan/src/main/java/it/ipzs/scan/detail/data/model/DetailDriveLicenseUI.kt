package it.ipzs.scan.detail.data.model

import it.ipzs.scan_data.model.DriveLicenseData
import it.ipzs.scan_data.model.VerificationResult

data class DetailDriveLicenseUI(
    val name: String,
    val surname: String,
    val birthDate: String,
    val location: String,
    val types: List<String>,
    val status: VerificationResult,
    val releaseDate: String,
    val expirationDate: String,
    val number: String,
    val codes: String,
    val releasedBy: String,
    val issuedBy: String,
    val website: String
){

    companion object{
        fun from(driveLicenseData: DriveLicenseData) = DetailDriveLicenseUI(
            name = driveLicenseData.name ?: "",
            surname = driveLicenseData.surname ?: "",
            birthDate = driveLicenseData.parsedBirthDate ?: "",
            location = when{
                driveLicenseData.birthPlace != null &&
                        driveLicenseData.birthPlaceShort != null -> "${driveLicenseData.birthPlace} (${driveLicenseData.birthPlaceShort})"
                driveLicenseData.birthPlace != null -> driveLicenseData.birthPlace ?: ""
                else -> "Unknown"
            },
            types = driveLicenseData.types ?: listOf(),
            status = driveLicenseData.status,
            releaseDate = driveLicenseData.parsedReleaseDate ?: "",
            expirationDate = driveLicenseData.parsedExpirationDate ?: "",
            number = driveLicenseData.documentNumber ?: "",

            //here some doubts...
            codes = "abc123",
            releasedBy = "Ministero delle infrastrutture e dei trasporti",
            issuedBy = driveLicenseData.issuer ?: "",
            website = "https://www.ipzs.it"
        )
    }

}