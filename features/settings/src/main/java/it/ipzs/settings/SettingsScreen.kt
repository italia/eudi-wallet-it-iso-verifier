package it.ipzs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import it.ipzs.theme.AppTheme
import it.ipzs.theme.R
import it.ipzs.theme.specs.ThemeSpecs
import it.simonecascino.destinationbuilder.annotations.Destination

@Destination(graphName = "SettingsGraph")
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onItemClick: (SettingSpecs) -> Unit
) {

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            vertical = ThemeSpecs.Dimensions.u100,
            horizontal = ThemeSpecs.Dimensions.u150
        )
    ){

        items(SettingSpecs.values()){

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onItemClick(it)
                    }
                    .padding(
                        vertical = ThemeSpecs.Dimensions.u50
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    painter = painterResource(id = it.iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified
                )

                Text(
                    modifier = Modifier.weight(1F).padding(start = ThemeSpecs.Dimensions.u100),
                    text = stringResource(id = it.stringRes),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Icon(
                    painter = painterResource(
                        id = R.drawable.icon_arrow_right
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

            }

            Divider(Modifier.fillMaxWidth())

        }

    }

}

@Composable
@Preview
private fun SettingsScreenPreview(){

    AppTheme {

        SettingsScreen{}

    }

}