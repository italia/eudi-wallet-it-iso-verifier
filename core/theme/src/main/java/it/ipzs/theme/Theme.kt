package it.ipzs.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import it.ipzs.theme.specs.colors
import it.ipzs.theme.specs.typography

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            window.navigationBarColor = colors.primary.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = {

            Surface{
                content()
            }

        }
    )

}