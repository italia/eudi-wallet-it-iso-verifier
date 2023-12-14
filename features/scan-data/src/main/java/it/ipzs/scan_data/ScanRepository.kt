package it.ipzs.scan_data

import android.util.Log
import it.ipzs.scan_data.model.DriveLicenseData
import it.ipzs.scan_data.managers.TransferManager
import it.ipzs.scan_data.utils.TransferStatus
import it.ipzs.utils.DoNothing
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val transferManager: TransferManager
) {

    val canSetName = transferManager.canSetName

    var lastDriveLicenseScanned: DriveLicenseData? = null

    suspend fun setQrDeviceEngagement(
        qr: String,
        onDocumentReceived: () -> Unit,
        onError: () -> Unit
    ){

        transferManager.setQrDeviceEngagement(qr)

        try{
            transferManager
                .getTransferStatus()
                .collect{

                    Log.d("test_status", it.name)

                    when(it){
                        TransferStatus.READER_ENGAGEMENT_READY -> {

                        }
                        TransferStatus.ENGAGED -> {
                            transferManager.connect()
                        }
                        TransferStatus.CONNECTED -> {
                            transferManager.sendRequest()
                        }
                        TransferStatus.RESPONSE -> {

                            val result = transferManager.parseResult()

                            lastDriveLicenseScanned = result

                            if(lastDriveLicenseScanned != null){
                                transferManager.stopVerification(true, true)
                                onDocumentReceived()
                            }
                        }
                        TransferStatus.DISCONNECTED -> {
                            if (!transferManager.hasDocuments())
                                onError()
                            else {
                                val result = transferManager.parseResult()
                                lastDriveLicenseScanned = result
                                if(lastDriveLicenseScanned != null){
                                    transferManager.stopVerification(true, true)
                                    onDocumentReceived()
                                }
                            }
                        }
                        TransferStatus.ERROR -> {

                            try{

                                val result = transferManager.parseResult()

                                lastDriveLicenseScanned = result

                                if(lastDriveLicenseScanned != null){
                                    transferManager.stopVerification(true, true)
                                    onDocumentReceived()
                                } else onError()

                            } catch (e: Exception){
                                e.printStackTrace()
                                onError()
                            }

                        }
                        TransferStatus.IDLE -> DoNothing
                    }
                }

        } finally {
            transferManager.stopVerification(true, true)
        }

    }

    fun setCustomName(customName: String){
        transferManager.setCustomName(customName)
    }

}