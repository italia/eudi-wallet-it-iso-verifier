package it.ipzs.scan.scan.data

import com.google.mlkit.vision.barcode.common.Barcode
import it.ipzs.architecture.viewmodel.BaseEvent

sealed interface ScanEvent: BaseEvent{

    data class ValidateData(val barcode: Barcode): ScanEvent

}