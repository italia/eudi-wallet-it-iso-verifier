package it.ipzs.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import it.ipzs.theme.AppTheme
import it.ipzs.theme.component.AppButton
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.resourceToAnnotatedString

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onButtonClick: () -> Unit
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
            text = stringResource(id = R.string.home_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center
            )
        )

        Text(
            modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u100),
            text = resourceToAnnotatedString(id = R.string.home_description),
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            )
        )

        Image(
            modifier = Modifier.fillMaxWidth()
                .weight(1f),
            painter = painterResource(id = R.drawable.image_home),
            contentDescription = null
        )

        Spacer(modifier = Modifier.height(ThemeSpecs.Dimensions.u200))

        AppButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.home_button),
            onClick = onButtonClick
        )


    }

}

@Preview
@Composable
private fun HomeScreenPreview(){

    AppTheme {

        HomeScreen{

        }

    }

}