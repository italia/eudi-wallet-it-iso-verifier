package it.ipzs.debug.detail.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import it.ipzs.debug.R
import it.ipzs.theme.specs.ThemeSpecs

@Composable
fun DebugDetailScreen(
    modifier: Modifier = Modifier,
    date: String,
    qrCode: String,
    success: Boolean,
    requested: String,
    received: String?,
    errorMessage: String?,
    onShareClicked: () -> Unit
){

    Box(modifier = modifier){

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        top = ThemeSpecs.Dimensions.u100,
                        bottom = ThemeSpecs.Dimensions.u300,
                        start = ThemeSpecs.Dimensions.u50,
                        end = ThemeSpecs.Dimensions.u50
                    )
                )
        ) {

            val title = stringResource(id = if(success) R.string.debug_detail_success
                else R.string.debug_detail_fail)

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = if(success) Color.Green else Color.Red,
                    textAlign = TextAlign.Center
                )
            )

            Text(text = date)

            Divider(
                modifier = Modifier.padding(vertical = ThemeSpecs.Dimensions.u50)
            )

            Text(
                text = stringResource(id = R.string.debug_detail_qr),
                style =  MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Text(text = qrCode)

            Divider(
                modifier = Modifier.padding(vertical = ThemeSpecs.Dimensions.u50)
            )

            Text(
                text = stringResource(id = R.string.debug_detail_requested),
                style =  MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Text(text = requested)

            Divider(
                modifier = Modifier.padding(vertical = ThemeSpecs.Dimensions.u50)
            )

            received?.let {
                Text(
                    text = stringResource(id = R.string.debug_detail_received),
                    style =  MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Text(received)

                Divider(
                    modifier = Modifier.padding(vertical = ThemeSpecs.Dimensions.u50)
                )
            }

            errorMessage?.let {

                Text(
                    text = stringResource(id = R.string.debug_detail_errors),
                    style =  MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Text(it)

            }

        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = ThemeSpecs.Dimensions.u100,
                    bottom = ThemeSpecs.Dimensions.u100
                ),
            containerColor = MaterialTheme.colorScheme.primary,
            onClick = { onShareClicked() }
        ) {
            Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
        }

    }
}