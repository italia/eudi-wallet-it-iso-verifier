package it.ipzs.home

import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.architecture.viewmodel.NoState
import it.ipzs.network.repository.CheckCertificateRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.network.util.NetworkResponse
import it.ipzs.preferences.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val checkCertificateRepository: CheckCertificateRepository,
    private val preferencesRepository: PreferencesRepository
): BaseViewModel<NoState, NoEvents>(NoState) {

    init {
        fetchAndSaveCrl()
    }

    private fun fetchAndSaveCrl(){
        viewModelScope.launch(Dispatchers.IO) {
            val crlResponse = checkCertificateRepository.getCrl()
            when (crlResponse) {
                is NetworkResponse.Success -> {
                    crlResponse.data?.let {
                        saveCrlCertificate(it)
                    }
                }
                is NetworkResponse.Error -> {}
                else -> {}

            }
        }
    }

    private suspend fun saveCrlCertificate(crlString : String){
            preferencesRepository.saveCrlCertificateString(crlString)
    }
}