package it.ipzs.verifica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import it.ipzs.verifica.R
import it.ipzs.theme.AppTheme
import it.ipzs.theme.component.AppButton
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.resourceToAnnotatedString

@Composable
fun RationalePermissionScreen(
    modifier: Modifier = Modifier,
    onButtonClicked: () -> Unit
){

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = ThemeSpecs.Dimensions.u100,
                vertical = ThemeSpecs.Dimensions.u150
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(id = R.string.permissions_rationale_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center
            )
        )

        Text(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100),
            text = resourceToAnnotatedString(id = R.string.permissions_rationale_description),
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.weight(1F))

        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.permissions_rationale_button),
            onClick = onButtonClicked
        )

    }

}

@Composable
fun PermissionsDeniedScreen(
    modifier: Modifier = Modifier,
    onButtonClicked: () -> Unit
){
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = ThemeSpecs.Dimensions.u100,
                vertical = ThemeSpecs.Dimensions.u150
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(id = R.string.permissions_denied_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center
            )
        )

        Text(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100),
            text = resourceToAnnotatedString(id = R.string.permissions_denied_description),
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.weight(1F))

        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.permissions_denied_button),
            onClick = onButtonClicked
        )

    }
}


@Composable
@Preview
private fun RationalePermissionScreenPreview(){
    AppTheme {

        RationalePermissionScreen {

        }

    }
}

@Composable
@Preview
private fun PermissionDeniedScreenPreview(){
    AppTheme {

        PermissionsDeniedScreen {

        }

    }
}