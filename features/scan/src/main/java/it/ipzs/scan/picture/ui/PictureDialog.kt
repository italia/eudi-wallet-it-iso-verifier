package it.ipzs.scan.picture.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import it.ipzs.scan.R
import it.ipzs.theme.component.AppDialog
import it.ipzs.theme.component.ButtonsSpecs
import it.ipzs.theme.specs.ThemeSpecs

@Composable
fun PictureDialog(
    fullName: String,
    picture: Bitmap,
    dismiss: () -> Unit
){

    AppDialog(
        positiveButtonSpecs = ButtonsSpecs(
            buttonText = stringResource(id = R.string.picture_button_positive),
            onClick = {
                dismiss()
            }
        ),
        onDismissRequest = { dismiss() }
    ) {

        Text(
            text = stringResource(id = R.string.picture_title),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = fullName,
            style = MaterialTheme.typography.labelLarge
        )

        AsyncImage(
            modifier = Modifier.fillMaxWidth().padding(top = ThemeSpecs.Dimensions.u50),
            model = picture,
            contentDescription = null
        )

    }


}