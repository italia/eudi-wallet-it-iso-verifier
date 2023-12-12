package it.ipzs.scan.scan.data

import com.google.mlkit.vision.barcode.common.Barcode
import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.scan_data.ScanRepository
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository
): BaseViewModel<ScanState, ScanEvent>(
    ScanState()
) {

    val canSetName = scanRepository.canSetName

    fun changeCustomName(text: String){
        changeState {
            copy(customName = text)
        }
    }

    fun saveCustomName(
        customName: String
    ){
        scanRepository.setCustomName(customName)
    }

    fun onBarcodesFound(
        barcodes: List<Barcode>
    ){
        //do something then
        dispatchEvent(ScanEvent.ValidateData(barcodes.first()))
    }

}