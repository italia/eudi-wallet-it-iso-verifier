package it.ipzs.scan.invalidcertificate

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import it.simonecascino.destinationbuilder.annotations.Destination
import it.ipzs.scan.R
import it.ipzs.theme.AppTheme
import it.ipzs.theme.component.AppButton
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.resourceToAnnotatedString

@Destination(
    graphName = "ScanFlowGraph"
)
@Composable
fun InvalidCertificateScreen(
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
            text = stringResource(id = R.string.error_title_not_valid_certificate),
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        )

        Text(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100),
            text = resourceToAnnotatedString(id = R.string.error_description_not_valid_certificate),
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            )
        )

        Image(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            painter = painterResource(id = com.google.android.material.R.drawable.mtrl_ic_error),
            contentDescription = null
        )
    }

}

@Composable
@Preview
private fun ErrorScreenPreview(){
    AppTheme {
        InvalidCertificateScreen{}
    }
}