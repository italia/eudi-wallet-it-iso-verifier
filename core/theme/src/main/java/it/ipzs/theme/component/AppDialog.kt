package it.ipzs.theme.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.ipzs.theme.AppTheme
import it.ipzs.theme.specs.ThemeSpecs

data class ButtonsSpecs(
    val buttonText: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    positiveButtonSpecs: ButtonsSpecs,
    negativeButtonSpecs: ButtonsSpecs? = null,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
){

    AlertDialog(
        onDismissRequest = onDismissRequest
    ) {

        Surface(
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = ThemeSpecs.Dimensions.u150,
                        horizontal = ThemeSpecs.Dimensions.u100
                    )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1F, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    this.content()
                }

                Spacer(modifier = Modifier.height(ThemeSpecs.Dimensions.u100))

                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = positiveButtonSpecs.buttonText,
                    onClick = positiveButtonSpecs.onClick
                )

                negativeButtonSpecs?.let {

                    AppTextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ThemeSpecs.Dimensions.u50),
                        text = it.buttonText,
                        onClick = it.onClick
                    )

                }


            }
        }

    }


}

@Composable
@Preview
private fun AppDialogPreview(){

    AppTheme {

        Column(modifier = Modifier.fillMaxSize()) {

            Text("prova")

        }


        AppDialog(
            positiveButtonSpecs = ButtonsSpecs(
                buttonText = "Ciao", onClick = {}
            ),
            negativeButtonSpecs = ButtonsSpecs(
                buttonText = "Ciao", onClick = {}
            ),
            onDismissRequest = {
            }
        ) {
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")
            Text("Ciao")

        }

    }

}