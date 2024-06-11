package it.ipzs.network.repository

import it.ipzs.network.RetrofitInterface
import it.ipzs.network.model.response.OCSPResponse
import it.ipzs.network.util.NetworkResponse
import it.ipzs.preferences.PreferencesRepository
import javax.inject.Inject

class CheckCertificateRepository @Inject constructor(
    private val api: RetrofitInterface,
    val preferencesRepository: PreferencesRepository
) {

    suspend fun getCrl() : NetworkResponse<String> {
        val response = try {
            api.getCrl()
        } catch (e: Exception) {
            return NetworkResponse.Error("An error occurred")
        }
        return NetworkResponse.Success(response)
    }

    suspend fun getOCSPBySerialNumber(certificateSerialNumber : Long) : NetworkResponse<OCSPResponse> {
        val response = try {
            api.getOCSPBySerialNumber(certificateSerialNumber)
        } catch (e: Exception) {
            return NetworkResponse.Error("An error occurred")
        }
        return NetworkResponse.Success(response)
    }

}