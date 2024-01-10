package it.ipzs.scan_data

import android.util.Log
import it.ipzs.scan_data.managers.TransferManager
import it.ipzs.scan_data.model.DriveLicenseData
import it.ipzs.scan_data.utils.TransferStatus
import it.ipzs.utils.DoNothing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class ScanRepository @Inject constructor(
    private val transferManager: TransferManager
) {

    val canSetName = transferManager.canSetName

    var lastDriveLicenseScanned: DriveLicenseData? = null

    private var timerJob: Job? = null

    suspend fun setQrDeviceEngagement(
        qr: String,
        onDocumentReceived: () -> Unit,
        onError: () -> Unit
    ){
        transferManager.stopVerification()
        transferManager.setQrDeviceEngagement(qr)

        withContext(Dispatchers.IO){

            timerJob = launch {
                delay(2.seconds)
                transferManager.stopVerification()
                onError()
            }

            launch {

                transferManager
                    .getTransferStatus()
                    .collect{

                        Log.d("test_status", it.name)

                        when(it){
                            TransferStatus.IDLE,
                            TransferStatus.READER_ENGAGEMENT_READY -> DoNothing

                            TransferStatus.ENGAGED -> {
                                transferManager.connect()
                            }

                            TransferStatus.CONNECTED -> {
                                transferManager.sendRequest()
                            }

                            TransferStatus.TERMINATED -> {

                                transferManager.parseResult()?.let {
                                    lastDriveLicenseScanned = it
                                    onDocumentReceived()
                                } ?: onError()

                                transferManager.stopVerification()
                                timerJob?.cancel()
                            }
                        }

                    }


            }

        }

    }

    fun setCustomName(customName: String){
        transferManager.setCustomName(customName)
    }

}