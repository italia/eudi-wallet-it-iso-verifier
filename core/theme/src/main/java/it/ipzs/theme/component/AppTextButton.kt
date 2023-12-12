package it.ipzs.theme.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import it.ipzs.theme.specs.ThemeSpecs

@Composable
fun AppTextButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
){

    val style = MaterialTheme.typography.bodySmall.copy(
        textDecoration = TextDecoration.Underline,
        textAlign = TextAlign.Center
    )

    Text(
        modifier = modifier.clickable {
            onClick()
        }.padding(vertical = ThemeSpecs.Dimensions.u75),
        text = text,
        style = style
    )

}