package it.ipzs.scan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import it.ipzs.scan.R
import it.simonecascino.destination.ScanFlowGraph
import it.ipzs.architecture.injection.composableVM
import it.ipzs.architecture.injection.dialogVM
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.architecture.viewmodel.NoState
import it.ipzs.scan.detail.data.DetailState
import it.ipzs.scan.detail.data.DetailViewModel
import it.ipzs.scan.detail.ui.DetailScreen
import it.ipzs.scan.drivelicense.data.DriveLicenseState
import it.ipzs.scan.drivelicense.data.DriveLicenseViewModel
import it.ipzs.scan.drivelicense.ui.DriveLicenseScreen
import it.ipzs.scan.error.ErrorScreen
import it.ipzs.scan.picture.data.PictureState
import it.ipzs.scan.picture.data.PictureViewModel
import it.ipzs.scan.picture.ui.PictureDialog
import it.ipzs.scan.scan.data.ScanEvent
import it.ipzs.scan.scan.data.ScanState
import it.ipzs.scan.scan.data.ScanViewModel
import it.ipzs.scan.scan.ui.ScanScreen
import it.ipzs.scan.validation.data.ValidationEvent
import it.ipzs.scan.validation.data.ValidationViewModel
import it.ipzs.scan.validation.ui.ValidationScreen
import it.ipzs.theme.component.AppDialog
import it.ipzs.theme.component.ButtonsSpecs
import it.ipzs.theme.specs.ThemeSpecs
import it.ipzs.utils.navigateAndPop
import it.ipzs.utils.resourceToAnnotatedString

fun NavGraphBuilder.scanGraph(
    navController: NavHostController,
    onCloseScan: () -> Unit
){

    navigation(
        startDestination = ScanFlowGraph.ScanScreen.route(),
        route = ScanFlowGraph.graphRoute
    ){

        composableVM<ScanState, ScanEvent, ScanViewModel>(
            route = ScanFlowGraph.ScanScreen.route(),
            onEventDispatched = {

                when(it){
                    is ScanEvent.ValidateData -> navController.navigate(ScanFlowGraph.ValidationScreen.buildPath(it.barcode.rawValue ?: ""))
                }
            }
        ){

            var showDialog by remember {
                mutableStateOf(canSetName)
            }

            if(showDialog){

                AppDialog(
                    positiveButtonSpecs = ButtonsSpecs(
                        stringResource(id = R.string.debug_dialog_positive)
                    ) {
                        saveCustomName(it.customName)
                        showDialog = false
                    },
                    negativeButtonSpecs = ButtonsSpecs(
                        stringResource(id = R.string.debug_dialog_negative)
                    ) {
                        navController.navigateUp()
                    },
                    onDismissRequest = {  }
                ) {

                    Text(
                        text = stringResource(id = R.string.debug_dialog_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            textAlign = TextAlign.Center
                        )
                    )

                    Text(
                        modifier = Modifier.padding(top = ThemeSpecs.Dimensions.u50),
                        text = resourceToAnnotatedString(id = R.string.debug_dialog_description),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center
                        )
                    )

                    TextField(
                        modifier = Modifier.fillMaxWidth().padding(vertical = ThemeSpecs.Dimensions.u50),
                        value = it.customName,
                        placeholder = {
                            Text(text = stringResource(id = R.string.debug_dialog_placeholder))
                        },
                        onValueChange = {
                            changeCustomName(it)
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                changeCustomName(it.customName)
                            }
                        )
                    )
                }
            }

            if(!showDialog) ScanScreen{
                onBarcodesFound(it)
            }

        }

        composableVM<NoState, ValidationEvent, ValidationViewModel>(
            route = ScanFlowGraph.ValidationScreen.route(),
            onEventDispatched = {
                when(it){
                    is ValidationEvent.OnValidationEnded -> navController.navigateAndPop(
                        to = ScanFlowGraph.DriveLicenseScreen.buildPath(),
                        popTo = ScanFlowGraph.ScanScreen.route(),
                        inclusive = true
                    )

                    ValidationEvent.OnError -> navController.navigateAndPop(
                        to = ScanFlowGraph.ErrorScreen.buildPath(),
                        popTo = ScanFlowGraph.ScanScreen.route(),
                        inclusive = false
                    )
                }
            }
        ){
            ValidationScreen()
        }

        composableVM<DriveLicenseState, NoEvents, DriveLicenseViewModel>(
            route = ScanFlowGraph.DriveLicenseScreen.route()
        ){

            DriveLicenseScreen(
                driveLicense = it.driveLicense,
                verificationSpecs = it.verificationSpecs,
                onClickDetails = {
                    navController.navigate(ScanFlowGraph.DetailScreen.buildPath())
                },
                onClickClose = onCloseScan
            )
        }

        composableVM<DetailState, NoEvents, DetailViewModel>(
            route = ScanFlowGraph.DetailScreen.route()
        ){

            DetailScreen(
                name = it.driveLicense.name,
                surname = it.driveLicense.surname,
                birthDate = it.driveLicense.birthDate,
                location = it.driveLicense.location,
                types = it.driveLicense.types,
                status = it.driveLicense.status,
                releaseDate = it.driveLicense.releaseDate,
                expirationDate = it.driveLicense.expirationDate,
                number = it.driveLicense.number,
                codes = it.driveLicense.codes,
                releasedBy = it.driveLicense.releasedBy,
                issuedBy = it.driveLicense.issuedBy,
                webSite = it.driveLicense.website
            ){
                navController.navigate(ScanFlowGraph.PictureDialog.buildPath())
            }

        }

        dialogVM<PictureState, NoEvents, PictureViewModel>(
            ScanFlowGraph.PictureDialog.route()
        ){

            PictureDialog(fullName = it.fullName, picture = it.picture) {
                navController.navigateUp()
            }

        }

        composable(
            route = ScanFlowGraph.ErrorScreen.route()
        ){

            ErrorScreen{
                navController.popBackStack(
                    route = ScanFlowGraph.ScanScreen.route(),
                    inclusive = false
                )
            }

        }

    }

}