package it.ipzs.theme.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.ipzs.theme.AppTheme
import it.ipzs.theme.R
import it.ipzs.theme.specs.ThemeSpecs

object AppBarSpecs{

    val appBarHeight = 73.dp

    enum class IconType(@DrawableRes val iconId: Int){

        Settings(R.drawable.icon_settings),
        Navigation(R.drawable.icon_navigation_back)

    }

}

/**
 * Top app bar styled properly. The default TopAppBar doesn't allow
 * us to customise the height, the final result is bad
 */
@Composable
fun AppTopAppBar(
    modifier: Modifier = Modifier,
    height: Dp = AppBarSpecs.appBarHeight,
    text: AnnotatedString,
    iconType: AppBarSpecs.IconType,
    onIconClick: () -> Unit
){

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(color = MaterialTheme.colorScheme.primary)
            .padding(
                horizontal = ThemeSpecs.Dimensions.u100,
                vertical = ThemeSpecs.Dimensions.u50
            )
    ){

        Icon(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable {
                    onIconClick()
                },
            painter = painterResource(
                id = iconType.iconId
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary
        )

        Text(
            modifier = Modifier.align(Alignment.Center),
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onPrimary
            )
        )

    }

}

@Composable
@Preview
private fun AppTopAppBarPreview(){

    AppTheme {

        Column {

            AppTopAppBar(
                iconType = AppBarSpecs.IconType.Navigation,
                text = AnnotatedString("Verifica patente")
            ){

            }

            AppTopAppBar(
                modifier = Modifier.padding(top = 16.dp),
                iconType = AppBarSpecs.IconType.Settings,
                text = AnnotatedString("Verifica patente")
            ){

            }

        }

    }

}