package it.ipzs.verifica.data

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.preferences.DatastorePrefResult
import it.ipzs.preferences.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository
): BaseViewModel<MainState, Nothing>(MainState()) {

    private var job: Job? = null

    init {

        job = viewModelScope.launch {
            preferencesRepository.isOnboardingDisplayed.collect{

                changeState {
                    copy(
                        isOnboardingDisplayed = it
                    )
                }

                //once the value is fetched the coroutine can be closed, we don't care about updates here
                if(
                    it is DatastorePrefResult.Fetched ||
                    it is DatastorePrefResult.Missing
                ){
                    job?.cancel()
                }
            }
        }
    }

    fun showRationale(){
        changeState {
            copy(permissionScreensStatus = PermissionScreensStatus.ShowRationale)
        }
    }

    fun showDenied(){
        changeState {
            copy(permissionScreensStatus = PermissionScreensStatus.ShowDenied)
        }
    }

    fun hidePermissionScreen(){
        changeState {
            copy(permissionScreensStatus = PermissionScreensStatus.None)
        }
    }

}