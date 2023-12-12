package it.ipzs.verifica.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import it.simonecascino.destination.DebugGraph
import it.simonecascino.destination.HomeGraph
import it.simonecascino.destination.OnboardingGraph
import it.simonecascino.destination.ScanFlowGraph
import it.simonecascino.destination.SettingsGraph
import it.simonecascino.destinationbuilder.annotations.Graph
import it.ipzs.architecture.injection.composableVM
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.architecture.viewmodel.NoState
import it.ipzs.debug.debugGraph
import it.ipzs.home.HomeScreen
import it.ipzs.onboarding.data.OnBoardingViewModel
import it.ipzs.onboarding.ui.OnboardingScreen
import it.ipzs.scan.scanGraph
import it.ipzs.settings.SettingSpecs
import it.ipzs.settings.SettingsScreen
import it.ipzs.utils.DoNothing
import it.ipzs.utils.navigateAndPop

@OptIn(
    ExperimentalLayoutApi::class
)
@Graph(
    graphs = [
        HomeGraph::class,
        ScanFlowGraph::class,
        SettingsGraph::class,
        OnboardingGraph::class,
        DebugGraph::class
    ]
)
@Composable
fun AppGraph(
    contentPadding: PaddingValues,
    navController: NavHostController,
    displayOnboarding: Boolean,
    onClickHomeButton: () -> Unit
){

    val context = LocalContext.current

    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding)
            .padding(contentPadding),
        navController = navController,
        startDestination = if(displayOnboarding)
            OnboardingGraph.OnboardingScreen.route()
        else HomeGraph.HomeScreen.route(),
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ){

        composableVM<NoState, NoEvents, OnBoardingViewModel>(
            route = OnboardingGraph.OnboardingScreen.route(),
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                ) + fadeOut()
            }
        ){
            OnboardingScreen{
                markOnboardingAsDisplayed()
                navController.navigateAndPop(
                    to = HomeGraph.HomeScreen.buildPath(),
                    popTo = OnboardingGraph.OnboardingScreen.route(),
                    inclusive = true
                )
            }
        }

        composable(
            route = HomeGraph.HomeScreen.route(),
            enterTransition = {

                if(displayOnboarding) slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                ) + fadeIn()

                else EnterTransition.None
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            }
        ){
            HomeScreen(
                onButtonClick = onClickHomeButton
            )
        }

        composable(
            route = SettingsGraph.SettingsScreen.route()
        ){

            SettingsScreen{

                when(it){
                    SettingSpecs.Privacy -> DoNothing
                    SettingSpecs.Security -> DoNothing
                    SettingSpecs.Info -> DoNothing
                    SettingSpecs.Tutorial -> DoNothing
                    else -> navController.navigate(DebugGraph.DebugScreen.buildPath())
                }

            }

        }

        debugGraph(navController){

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, it)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)

        }

        scanGraph(navController){
            navController.popBackStack(
                route = HomeGraph.HomeScreen.route(),
                inclusive = false
            )
        }

    }

}