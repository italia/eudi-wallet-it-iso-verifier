package it.ipzs.verifica.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import it.simonecascino.destination.DebugGraph
import it.simonecascino.destination.HomeGraph
import it.simonecascino.destination.ScanFlowGraph
import it.simonecascino.destination.SettingsGraph
import it.simonecascino.destinationbuilder.base.BaseDestination
import it.ipzs.verifica.R
import it.ipzs.theme.component.AppBarSpecs

object DestinationResolver {

    data class DestinationDetails(
        val destination: BaseDestination,
        val title: AnnotatedString,
        val icon: AppBarSpecs.IconType
    )

    @Composable
    fun resolve(
        currentDestination: BaseDestination?,
        customTitle: String?
    ) = currentDestination?.let {

        val title: AnnotatedString
        val icon: AppBarSpecs.IconType

        when(currentDestination){

            is HomeGraph.HomeScreen -> {
                title = mainTitle()
                icon = AppBarSpecs.IconType.Settings
            }

            is SettingsGraph.SettingsScreen -> {
                title = AnnotatedString(stringResource(id = R.string.title_settings))
                icon = AppBarSpecs.IconType.Navigation
            }

            is ScanFlowGraph.ScanScreen,
            is ScanFlowGraph.DriveLicenseScreen,
            is ScanFlowGraph.ErrorScreen,
            is ScanFlowGraph.ValidationScreen -> {
                title = mainTitle()
                icon = AppBarSpecs.IconType.Navigation
            }

            is ScanFlowGraph.DetailScreen -> {
                title = AnnotatedString(stringResource(id = R.string.title_details))
                icon = AppBarSpecs.IconType.Navigation
            }

            is DebugGraph.DebugScreen -> {
                title = AnnotatedString(stringResource(id = R.string.title_debug))
                icon = AppBarSpecs.IconType.Navigation
            }

            is DebugGraph.DebugDetailScreen -> {
                title = AnnotatedString(customTitle ?: "")
                icon = AppBarSpecs.IconType.Navigation
            }

            else -> {
                title = AnnotatedString("")
                icon = AppBarSpecs.IconType.Navigation
            }
        }

        DestinationDetails(
            destination = currentDestination,
            title = title,
            icon = icon
        )

    }


    @Composable
    private fun mainTitle(): AnnotatedString{

        val finalPart = stringResource(id = R.string.title_home_end)
        val title = stringResource(id = R.string.title_home_template, finalPart)

        return buildAnnotatedString {
            append(title)
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Normal
                ),
                title.indexOf(finalPart), title.length
            )
        }

    }
}
