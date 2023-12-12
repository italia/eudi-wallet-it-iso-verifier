package it.ipzs.scan.drivelicense.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import it.ipzs.scan.R
import it.ipzs.scan_data.model.VerificationResult

private val verificationGreen = Color(0xff256D1B).copy(alpha = 0.3f)
private val verificationOrange = Color(0xffEE8434).copy(alpha = 0.2f)
private val verificationRed = Color(0xff6A1212).copy(alpha = 0.2f)

private val verificationTextGreen = Color(0xff0D4405)
private val verificationTextOrange = Color(0xffAE4B00)
private val verificationTextRed = Color(0xff5F0B0B)

data class VerificationSpecs(
    @StringRes val textRes: Int,
    @DrawableRes val iconRes: Int,
    val color: Color,
    val textColor: Color
){

    companion object{
        fun from(verificationResult: VerificationResult): VerificationSpecs{

            val textRes: Int
            val iconRes: Int
            val color: Color
            val textColor: Color

            when(verificationResult){
                VerificationResult.Valid -> {
                    textRes = R.string.verification_result_valid
                    iconRes = it.ipzs.theme.R.drawable.icon_verification_valid
                    color = verificationGreen
                    textColor = verificationTextGreen
                }
                VerificationResult.NotVerified -> {
                    textRes = R.string.verification_result_not_verified
                    iconRes = it.ipzs.theme.R.drawable.icon_verification_failure
                    color = verificationOrange
                    textColor = verificationTextOrange
                }
                VerificationResult.NotValid -> {
                    textRes = R.string.verification_result_not_valid
                    iconRes = it.ipzs.theme.R.drawable.icon_verification_invalid
                    color = verificationRed
                    textColor = verificationTextRed
                }
                VerificationResult.Expired -> {
                    textRes = R.string.verification_result_expired
                    iconRes = it.ipzs.theme.R.drawable.icon_verification_invalid
                    color = verificationRed
                    textColor = verificationTextRed
                }
            }

            return VerificationSpecs(
                textRes, iconRes, color, textColor
            )

        }
    }

}