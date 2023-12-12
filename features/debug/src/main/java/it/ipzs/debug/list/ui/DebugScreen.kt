package it.ipzs.debug.list.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.simonecascino.destinationbuilder.annotations.Destination
import it.ipzs.debug.list.data.models.LogUI
import it.ipzs.theme.specs.ThemeSpecs

@Destination(
    graphName = "DebugGraph"
)
@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    logs: List<LogUI>,
    onDeleteClicked: (Long) -> Unit,
    onShareClicked: (Long) -> Unit,
    onItemClicked: (LogUI) -> Unit
){

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ThemeSpecs.Dimensions.u100),
        contentPadding = PaddingValues(
            ThemeSpecs.Dimensions.u100
        )
    ){

        items(logs){

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onItemClicked(it)
                    }
                    .drawBehind {

                        drawRect(
                            color = when (it.success) {
                                true -> Color.Green
                                false -> Color.Red
                            },
                            size = size.copy(
                                width = 2.dp.toPx()
                            )
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = ThemeSpecs.Dimensions.u50)
                        .weight(1F)
                ) {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Text(
                        text = it.date,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.width(ThemeSpecs.Dimensions.u50))

                IconButton(onClick = { onDeleteClicked(it.id) }) {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = null)
                }

                Spacer(modifier = Modifier.width(ThemeSpecs.Dimensions.u50))

                IconButton(onClick = { onShareClicked(it.id) }) {
                    Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
                }

            }
        }

    }

}