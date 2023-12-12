package it.ipzs.onboarding.data

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.architecture.viewmodel.NoState
import it.ipzs.preferences.PreferencesRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
): BaseViewModel<NoState, NoEvents>(NoState) {

    fun markOnboardingAsDisplayed(){
        viewModelScope.launch {
            preferencesRepository.setOnboardingDisplayed(true)
        }
    }

}