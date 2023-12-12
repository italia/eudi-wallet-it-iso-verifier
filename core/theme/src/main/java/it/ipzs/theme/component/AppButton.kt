package it.ipzs.theme.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.ipzs.theme.AppTheme

object AppButtonDefaults{
    val buttonHeight = 54.dp
}

enum class ButtonType{
    Solid, Outlined
}

@Composable
fun AppButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    type: ButtonType = ButtonType.Solid,
    onClick: () -> Unit
){

    val buttonModifier = modifier
        .heightIn(min = AppButtonDefaults.buttonHeight)

    val buttonText: @Composable () -> Unit = {

        Text(
            text = text,
            style = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center
            )
        )

    }

    when(type){
        ButtonType.Solid -> Button(
            modifier = buttonModifier,
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            content = {
                buttonText()
            }
        )
        ButtonType.Outlined -> OutlinedButton(
            modifier = buttonModifier,
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            content = {
                buttonText()
            }
        )
    }
    
}

@Composable
@Preview
private fun AppButtonPreview(){
    AppTheme {

        Column(modifier = Modifier.padding(16.dp)) {

            AppButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Prova"
            ) {



            }

            AppButton(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                type = ButtonType.Outlined,
                text = "Prova"
            ) {



            }
        }

    }
}