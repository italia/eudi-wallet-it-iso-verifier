package it.ipzs.scan_data.model

import android.graphics.Bitmap
import it.ipzs.scan_data.utils.documents.DocumentHandler
import it.ipzs.utils.atLeastOneNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DriveLicenseData(
    val isValidOnDates: Boolean,
    val name: String? = null,
    val surname: String? = null,
    private val birthDate: String? = null,
    val birthPlace: String? = null,
    val birthPlaceShort: String? = null,
    private val releaseDate: String? = null,
    private val expiryDate: String? = null,
    val issuer: String? = null,
    val documentNumber: String? = null,
    val address: String? = null,
    val types: List<String>? = null,
    val photo: Bitmap? = null
){

    val parsedReleaseDate = try{
        LocalDate.parse(releaseDate, DateTimeFormatter.ISO_DATE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (e: Exception){
        releaseDate
    }

    val parsedExpirationDate = try{
        LocalDate.parse(expiryDate, DateTimeFormatter.ISO_DATE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (e: Exception){
        expiryDate
    }

    val parsedBirthDate = try{
        LocalDate.parse(birthDate, DateTimeFormatter.ISO_DATE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (e: Exception){
        birthDate
    }

    private val isExpired = expiryDate?.let {
        val date = try {
            LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
        } catch(e: Exception){
            LocalDate.parse(it, DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
        }
        date.isBefore(LocalDate.now())
    } ?: false

    private val containsMandatoryParams = !atLeastOneNull(
        name,
        surname,
        photo,
        expiryDate,
        types,
        birthDate,
        documentNumber
    )

    val status = when{
        !isValidOnDates -> VerificationResult.NotVerified
        isExpired -> VerificationResult.Expired
        !containsMandatoryParams -> VerificationResult.NotValid
        else -> VerificationResult.Valid
    }

    companion object{

        fun fromDocumentHandler(
            documentHandler: DocumentHandler,
            isValidOnDates: Boolean
        ) = DriveLicenseData(
            isValidOnDates = isValidOnDates,
            name = documentHandler.getName(),
            surname = documentHandler.getFamilyName(),
            birthDate = documentHandler.getBirthDate(),
            birthPlace = documentHandler.getBirthPlace(),
            address = documentHandler.getAddress(),
            releaseDate = documentHandler.getIssueDate(),
            expiryDate = documentHandler.getExpiryDate(),
            issuer = documentHandler.getIssuingAuthority(),
            documentNumber = documentHandler.getDocumentNumber(),
            types = documentHandler.getTypes(),
            photo = documentHandler.portrait
        )
    }

}