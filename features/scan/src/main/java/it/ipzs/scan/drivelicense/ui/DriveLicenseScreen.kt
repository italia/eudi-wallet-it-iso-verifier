package it.ipzs.scan.drivelicense.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.ipzs.scan.R
import it.ipzs.scan.drivelicense.data.model.DriveLicenseUI
import it.ipzs.scan.drivelicense.data.model.VerificationSpecs
import it.ipzs.theme.component.AppButton
import it.ipzs.theme.component.AppTextButton
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.toPx

private val extendedAreaHeight = 150.dp

@Composable
fun DriveLicenseScreen(
    modifier: Modifier = Modifier,
    driveLicense: DriveLicenseUI,
    verificationSpecs: VerificationSpecs,
    onClickDetails: () -> Unit,
    onClickClose: () -> Unit
){

    val extendedAreaHeightPx = extendedAreaHeight.toPx()
    val extendedAreaColor = MaterialTheme.colorScheme.primary

    Column(
        modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {

                    drawRect(
                        color = extendedAreaColor,
                        size = Size(width = size.width, height = extendedAreaHeightPx)
                    )

                }
            }
            .padding(horizontal = ThemeSpecs.Dimensions.u100)
            .padding(bottom = ThemeSpecs.Dimensions.u150)
    ) {

        DriveLicense(
            name = driveLicense.name,
            surname = driveLicense.surname,
            types = driveLicense.types,
            expiration = driveLicense.expirationDate,
            picture = driveLicense.picture,
            verificationSpecs = verificationSpecs
        )
        
        Spacer(modifier = Modifier.weight(1F))
        
        Item(stringResource(id = R.string.drive_license_label_number), driveLicense.documentNumber)
        Item(stringResource(id = R.string.drive_license_label_release_date), driveLicense.releaseDate)
        Item(stringResource(id = R.string.drive_license_label_expiration_date), driveLicense.expirationDate)

        Spacer(modifier = Modifier.weight(2F))

        AppButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ThemeSpecs.Dimensions.u50),
            text = stringResource(id = R.string.drive_license_button_details),
            onClick = onClickDetails
        )

        AppTextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ThemeSpecs.Dimensions.u50)
                .padding(top = ThemeSpecs.Dimensions.u25),
            text = stringResource(id = R.string.drive_license_button_close),
            onClick = onClickClose
        )

    }

}

@Composable
private fun Item(
    name: String,
    value: String
){

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = ThemeSpecs.Dimensions.u50,
                start = ThemeSpecs.Dimensions.u100,
                end = ThemeSpecs.Dimensions.u100
            )
    ) {

        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold
            )
        )

        Divider(
            Modifier
                .fillMaxWidth()
                .padding(top = ThemeSpecs.Dimensions.u50))

    }

}