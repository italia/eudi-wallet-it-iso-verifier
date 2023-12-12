package it.ipzs.verifica.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import it.simonecascino.destination.OnboardingGraph
import it.simonecascino.destination.SettingsGraph
import it.ipzs.verifica.data.MainState
import it.ipzs.verifica.utils.DestinationResolver
import it.ipzs.preferences.DatastorePrefResult
import it.ipzs.theme.component.AppBarSpecs
import it.ipzs.theme.component.AppTopAppBar

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppScaffold(
    navController: NavHostController,
    destinationDetails: DestinationResolver.DestinationDetails?,
    mainState: MainState,
    onClickHomeButton: () -> Unit
){

    val displayOnboarding = when(mainState.isOnboardingDisplayed){
        DatastorePrefResult.Missing -> true
        is DatastorePrefResult.Fetched -> !mainState.isOnboardingDisplayed.value
        DatastorePrefResult.NotFetched -> return
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {

            val title: AnnotatedString
            val icon: AppBarSpecs.IconType
            val appBarHeight: Dp

            if(destinationDetails != null){

                val destination = destinationDetails.destination
                title = destinationDetails.title
                icon = destinationDetails.icon
                appBarHeight = animateDpAsState(
                    targetValue = if(destination is OnboardingGraph.OnboardingScreen)0.dp
                    else AppBarSpecs.appBarHeight,
                    label = ""
                ).value


            } else if(!displayOnboarding){
                title = AnnotatedString("")
                icon = AppBarSpecs.IconType.Settings
                appBarHeight = AppBarSpecs.appBarHeight
            }

            else {
                title = AnnotatedString("")
                icon = AppBarSpecs.IconType.Settings
                appBarHeight = 0.dp
            }

            AppTopAppBar(
                text = title,
                iconType = icon,
                height = appBarHeight
            ) {

                when(icon){
                    AppBarSpecs.IconType.Settings -> navController.navigate(
                        SettingsGraph.SettingsScreen.buildPath()
                    )
                    AppBarSpecs.IconType.Navigation -> navController.navigateUp()
                }

            }

        }

    ) {

        AppGraph(
            contentPadding =  it,
            navController = navController,
            displayOnboarding = displayOnboarding,
            onClickHomeButton = {
                onClickHomeButton()
            }
        )

    }


}