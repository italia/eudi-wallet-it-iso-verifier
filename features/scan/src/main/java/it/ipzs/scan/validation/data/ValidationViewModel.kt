package it.ipzs.scan.validation.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.simonecascino.destination.ScanFlowGraph
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoState
import it.ipzs.scan_data.ScanRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ValidationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val scanRepository: ScanRepository
): BaseViewModel<NoState, ValidationEvent>(NoState) {

    init {

        val barcode = savedStateHandle.get<String>(
            ScanFlowGraph.ValidationScreen.KEY_BARCODE
        ) ?: throw IllegalStateException("SavedStateHandle must have a valid value for the key ${ScanFlowGraph.ValidationScreen.KEY_BARCODE}")

        viewModelScope.launch {
            scanRepository.setQrDeviceEngagement(
                qr = barcode,
                onDocumentReceived = {
                    dispatchEvent(ValidationEvent.OnValidationEnded)
                },
                onError = {
                    dispatchEvent(ValidationEvent.OnError)
                }
            )
        }

    }

}