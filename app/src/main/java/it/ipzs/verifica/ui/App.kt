package it.ipzs.verifica.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import it.ipzs.verifica.utils.AppGraphResolver
import it.ipzs.architecture.injection.contentVM
import it.ipzs.scan.ScanFlowGraph
import it.ipzs.verifica.data.MainState
import it.ipzs.verifica.data.MainViewModel
import it.ipzs.verifica.data.PermissionScreensStatus
import it.ipzs.verifica.utils.DestinationResolver
import it.ipzs.theme.AppTheme
import it.ipzs.utils.BaseDestination
import java.net.URLDecoder

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun App(
    keepScreenOn: (Boolean) -> Unit
){

    AppTheme {

        contentVM<MainState, Nothing, MainViewModel> { mainState ->

            val context = LocalContext.current

            val navController = rememberNavController()

            val currentEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentEntry?.destination?.route ?: ""
            val currentDestination = AppGraphResolver.resolve(currentRoute)
            val customTitle = if(currentDestination?.dynamicTitle == true)
                URLDecoder.decode(currentEntry?.arguments?.getString(BaseDestination.ANDROID_TITLE), "UTF-8")
            else null
            val destinationDetails = DestinationResolver.resolve(currentDestination, customTitle)

            destinationDetails?.destination?.let {
                keepScreenOn(it is ScanFlowGraph.ScanScreen || it is ScanFlowGraph.ValidationScreen)
            }

            val bluetoothLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if(it.resultCode == Activity.RESULT_OK)
                    navController.navigate(ScanFlowGraph.graphRoute)
            }

            val permissionState = rememberMultiplePermissionsState(
                permissions = mutableListOf(
                    Manifest.permission.CAMERA
                ).also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        it.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        it.add(Manifest.permission.BLUETOOTH_SCAN)
                        it.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
            ){
                if(it.all { it.value }) {

                    if(isBluetoothEnabled(context))
                        navController.navigate(ScanFlowGraph.graphRoute)
                    else bluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
                else showRationale()
            }

            LaunchedEffect(
                key1 = permissionState.allPermissionsGranted
            ){

                if(mainState.permissionScreensStatus != PermissionScreensStatus.None){
                    hidePermissionScreen()
                    if(isBluetoothEnabled(context))
                        navController.navigate(ScanFlowGraph.graphRoute)
                    else bluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
            }

            Crossfade(
                targetState = mainState.permissionScreensStatus,
                label = ""
            ) {

                when(it){
                    PermissionScreensStatus.None -> AppScaffold(
                        navController,
                        destinationDetails,
                        mainState,
                    ){
                        when {
                            permissionState.allPermissionsGranted -> {
                                if(isBluetoothEnabled(context))
                                    navController.navigate(ScanFlowGraph.graphRoute)
                                else bluetoothLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                )
                            }
                            permissionState.shouldShowRationale -> showRationale()
                            else -> permissionState.launchMultiplePermissionRequest()
                        }
                    }

                    PermissionScreensStatus.ShowRationale -> RationalePermissionScreen(
                        modifier = Modifier.safeDrawingPadding()
                    ) {

                        if(!permissionState.shouldShowRationale)
                            showDenied()

                        else permissionState.launchMultiplePermissionRequest()
                    }

                    PermissionScreensStatus.ShowDenied -> PermissionsDeniedScreen(
                        modifier = Modifier.safeDrawingPadding()
                    ) {

                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        }.also {
                            context.startActivity(it)
                        }

                    }
                }
            }
        }
    }

}

private fun isBluetoothEnabled(context: Context) = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled