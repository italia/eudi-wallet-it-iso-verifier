package it.ipzs.scan.scan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.google.mlkit.vision.barcode.common.Barcode
import it.ipzs.scan.R
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.resourceToAnnotatedString

@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    onBarcodesDetected: (List<Barcode>) -> Unit
){

    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            modifier = Modifier.padding(
                    top = ThemeSpecs.Dimensions.u150,
                    start = ThemeSpecs.Dimensions.u100,
                    end = ThemeSpecs.Dimensions.u100
                ),
            text = stringResource(id = R.string.scan_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center
            )
        )

        Text(
            modifier = Modifier.padding(
                top = ThemeSpecs.Dimensions.u100,
                start = ThemeSpecs.Dimensions.u100,
                end = ThemeSpecs.Dimensions.u100
            ),
            text = resourceToAnnotatedString(id = R.string.scan_description),
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            )
        )

        Camera(
            modifier = Modifier.padding(
                top = ThemeSpecs.Dimensions.u100
            ),
            onBarcodesDetected = onBarcodesDetected
        )


    }

}