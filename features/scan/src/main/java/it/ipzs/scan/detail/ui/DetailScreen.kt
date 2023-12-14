package it.ipzs.scan.detail.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import it.ipzs.scan.R
import it.ipzs.scan_data.model.VerificationResult
import it.ipzs.theme.component.AppButton
import it.ipzs.theme.component.ButtonType
import it.ipzs.theme.specs.ThemeSpecs

@Composable
fun DetailScreen(
    modifier: Modifier = Modifier,
    name: String,
    surname: String,
    birthDate: String,
    location: String,
    types: List<String>,
    status: VerificationResult,
    releaseDate: String,
    expirationDate: String,
    number: String,
    codes: String,
    releasedBy: String,
    issuedBy: String,
    webSite: String,
    showPicture: () -> Unit
){

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                PaddingValues(
                    horizontal = ThemeSpecs.Dimensions.u100,
                    vertical = ThemeSpecs.Dimensions.u150
                )
            )
    ) {

        DetailCard(
            title = stringResource(id = R.string.detail_personal_title),
            image = painterResource(id = R.drawable.image_details_personal),
            rows = listOf(
                DetailRowSpec(label = stringResource(id = R.string.detail_personal_name_label), value = name),
                DetailRowSpec(label = stringResource(id = R.string.detail_personal_surname_label), value = surname),
                DetailRowSpec(label = stringResource(id = R.string.detail_personal_birthdate_label), value = birthDate),
                DetailRowSpec(label = stringResource(id = R.string.detail_personal_place_label), value = location, divider = false)
            )
        )

        AppButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ThemeSpecs.Dimensions.u100),
            type = ButtonType.Outlined,
            text = stringResource(id = R.string.detail_button_picture),
            onClick = showPicture
        )

        DetailCard(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u200),
            title = stringResource(id = R.string.drive_license_title),
            image = painterResource(id = R.drawable.image_details_licenses),
            rows = types.mapIndexed { index, s ->
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_licenses_label),
                    value = s,
                    divider = index < types.size -1,
                    trailingButtonSpecs = TrailingButtonSpecs(
                        type = TrailingButtonType.TrailingTextButton(
                            text = stringResource(id = R.string.detail_licenses_button)
                        ),
                        onClick = {

                        }
                    )
                )
            }
        )

        DetailCard(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u200),
            title = stringResource(id = R.string.detail_data_title),
            image = painterResource(id = R.drawable.image_details_data),
            rows = listOf(
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_status_label),
                    value = stringResource(id = R.string.detail_status_active),
                    showBubble = true,
                    bubbleColor = when(status){
                        VerificationResult.Valid -> ThemeSpecs.Colors.validColor
                        else -> MaterialTheme.colorScheme.error
                    }
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_release_date_label),
                    value = releaseDate,
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_expiration_date_label),
                    value = expirationDate,
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_number_label),
                    value = number,
                    divider = false
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_codes_label),
                    value = codes,
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_released_by_label),
                    value = releasedBy,
                    trailingButtonSpecs = TrailingButtonSpecs(
                        type = TrailingButtonType.TrailingIconButton(
                            painter = painterResource(id = it.ipzs.theme.R.drawable.icon_info)
                        ),
                        onClick = {}
                    )
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_emission_label),
                    value = issuedBy,
                    trailingButtonSpecs = TrailingButtonSpecs(
                        type = TrailingButtonType.TrailingIconButton(
                            painter = painterResource(id = it.ipzs.theme.R.drawable.icon_info)
                        ),
                        onClick = {}
                    )
                ),
                DetailRowSpec(
                    label = stringResource(id = R.string.detail_data_additional_info_label),
                    value = webSite,
                    trailingButtonSpecs = TrailingButtonSpecs(
                        type = TrailingButtonType.TrailingIconButton(
                            painter = painterResource(id = it.ipzs.theme.R.drawable.icon_web)
                        ),
                        onClick = {}
                    )
                ),
            )
        )


    }

}