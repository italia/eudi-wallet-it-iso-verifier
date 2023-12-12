package it.ipzs.verifica.data

import it.ipzs.architecture.viewmodel.BaseState
import it.ipzs.preferences.DatastorePrefResult

data class MainState(
    val isOnboardingDisplayed: DatastorePrefResult<Boolean> = DatastorePrefResult.NotFetched,
    val permissionScreensStatus: PermissionScreensStatus = PermissionScreensStatus.None
): BaseState

enum class PermissionScreensStatus{
    None, ShowRationale, ShowDenied
}