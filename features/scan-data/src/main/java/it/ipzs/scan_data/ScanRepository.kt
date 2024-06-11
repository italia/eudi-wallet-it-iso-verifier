package it.ipzs.scan_data

import android.util.Log
import it.ipzs.network.repository.CheckCertificateRepository
import it.ipzs.network.util.NetworkResponse
import it.ipzs.preferences.PreferencesRepository
import it.ipzs.scan_data.model.DriveLicenseData
import it.ipzs.scan_data.managers.TransferManager
import it.ipzs.scan_data.utils.TransferStatus
import it.ipzs.scan_data.utils.certificate.KeysAndCertificates
import it.ipzs.utils.DoNothing
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val transferManager: TransferManager,
    private val checkCertificateRepository: CheckCertificateRepository,
    private val preferencesRepository: PreferencesRepository
) {

    val canSetName = transferManager.canSetName

    var lastDriveLicenseScanned: DriveLicenseData? = null

    suspend fun setQrDeviceEngagement(
        qr: String,
        onDocumentReceived: () -> Unit,
        onError: () -> Unit,
        onCertificateNotValid: () -> Unit
    ){

        transferManager.setQrDeviceEngagement(qr)

        try {
            transferManager
                .getTransferStatus()
                .collect{

                    Log.d("test_status", it.name)

                    when (it) {
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

                            val isCertificateValid =  checkCertificateValidity(result?.serialNumberCertificate?.toLong())
                            if (!isCertificateValid) {
                                onCertificateNotValid()
                                return@collect
                            }

                            if (lastDriveLicenseScanned != null) {
                                transferManager.stopVerification(true, true)
                                onDocumentReceived()
                            }
                        }
                        TransferStatus.DISCONNECTED -> {
                            if (!transferManager.hasDocuments())
                                onError()
                        }

                        TransferStatus.ERROR -> {

                            try {

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
    private suspend fun checkCertificateValidity(serialNumber : Long?): Boolean{
        serialNumber?.let {
            val resp = checkCertificateRepository.getOCSPBySerialNumber(serialNumber)
            when(resp){
                is NetworkResponse.Success -> {
                    var isCertificateValid = !resp.data!!.isRevoked
                    return isCertificateValid
                }
                else -> {
                    return isCertifcateValidByCRL(serialNumber)
                }
            }
        }
        return false
    }

   suspend fun isCertifcateValidByCRL(serialNumber: Long?) : Boolean{
       serialNumber?.let {
           var crlStringFromStorage = preferencesRepository.getCrlCertificateString() ?:""
           val x509CRL = KeysAndCertificates.parseCRL(crlStringFromStorage)
           val isCertificateValid = x509CRL?.getRevokedCertificate(serialNumber.toBigInteger()) == null
           return isCertificateValid
       } ?: return false

    }


    fun setCustomName(customName: String){
        transferManager.setCustomName(customName)
    }

}