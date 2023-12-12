package it.ipzs.scan.detail.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import it.ipzs.theme.specs.ThemeSpecs

private val elevation = 4.dp

@Composable
internal fun DetailCard(
    modifier: Modifier = Modifier,
    title: String,
    image: Painter,
    rows: List<DetailRowSpec>
){

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                modifier = Modifier
                    .padding(ThemeSpecs.Dimensions.u100)
                    .weight(1f),
                text = title,
                style = MaterialTheme.typography.labelLarge
            )

            Image(
                modifier = Modifier.padding(
                    top = ThemeSpecs.Dimensions.u25,
                    end = ThemeSpecs.Dimensions.u25
                ),
                painter = image,
                contentDescription = null
            )

        }

        rows.forEach {
            DetailRow(
                label = it.label,
                value = it.value,
                divider = it.divider,
                showBubble = it.showBubble,
                bubbleColor = it.bubbleColor,
                trailingButtonSpecs = it.trailingButtonSpecs
            )
        }

    }

}