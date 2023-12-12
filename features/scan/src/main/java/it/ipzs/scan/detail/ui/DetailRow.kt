package it.ipzs.scan.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import it.ipzs.theme.specs.ThemeSpecs

internal data class DetailRowSpec(
    val label: String,
    val value: String,
    val divider: Boolean = true,
    val trailingButtonSpecs: TrailingButtonSpecs? = null,
    val showBubble: Boolean = false,
    val bubbleColor: Color? = null
)

internal data class TrailingButtonSpecs(
    val type: TrailingButtonType,
    val onClick: () -> Unit
)

internal sealed interface TrailingButtonType{
    data class TrailingTextButton(val text: String): TrailingButtonType
    data class TrailingIconButton(val painter: Painter): TrailingButtonType
}

@Composable
internal fun DetailRow(
    label: String,
    value: String,
    divider: Boolean,
    showBubble: Boolean,
    bubbleColor: Color?,
    trailingButtonSpecs: TrailingButtonSpecs?
){

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top = ThemeSpecs.Dimensions.u100,
                start = ThemeSpecs.Dimensions.u100,
                end = ThemeSpecs.Dimensions.u100
            )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ){
            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall
                )

                Text(
                    modifier = Modifier.then(
                        if(showBubble){
                            Modifier
                                .clip(CircleShape)
                                .background(
                                    color = bubbleColor ?: Color.Transparent
                                ).padding(
                                    horizontal = ThemeSpecs.Dimensions.u25,
                                    vertical = ThemeSpecs.Dimensions.u12
                                )
                        } else Modifier
                    ),
                    text = value,
                    style = if(showBubble)MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ) else MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

            }

            trailingButtonSpecs?.let {

                when(val type = it.type){
                    is TrailingButtonType.TrailingIconButton -> Icon(
                        modifier = Modifier
                            .clickable { CircleShape }
                            .clickable {
                                it.onClick()
                            },
                        painter = type.painter,
                        tint = Color.Unspecified,
                        contentDescription = null
                    )
                    is TrailingButtonType.TrailingTextButton -> Text(
                        modifier = Modifier.clickable {
                            it.onClick()
                        },
                        text = type.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

            }

        }

        Spacer(Modifier.size(ThemeSpecs.Dimensions.u100))

        if(divider) Divider()

    }

}