package it.ipzs.scan.validation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import it.simonecascino.destinationbuilder.annotations.Destination
import it.ipzs.scan.R
import it.ipzs.theme.component.AppProgressDots
import it.ipzs.theme.specs.ThemeSpecs

@Destination(
    graphName = "ScanFlowGraph",
    paths = ["barcode"]
)
@Composable
fun ValidationScreen(
    modifier: Modifier = Modifier
){

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = ThemeSpecs.Dimensions.u100,
                vertical = ThemeSpecs.Dimensions.u150
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            text = stringResource(id = R.string.validation_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.weight(1F))
        
        AppProgressDots()
        
        Text(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100),
            text = stringResource(id = R.string.validation_description),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.weight(1F))

    }

}