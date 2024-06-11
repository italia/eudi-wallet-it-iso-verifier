package it.ipzs.network

import it.ipzs.network.model.request.OCSPRequest
import it.ipzs.network.model.response.OCSPResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.math.BigInteger


interface RetrofitInterface {
    @GET("root-ca/crl")
    suspend fun getCrl() : String

    @GET("root-ca/ocsp/checkBySerialNumber")
    suspend fun getOCSPBySerialNumber(
        @Query ("serialNumber") serialNumber: Long
    ) : OCSPResponse
    @POST("root-ca/ocsp")
    suspend fun checkOcsp(
        @Body requestData: OCSPRequest
    ): OCSPResponse
}

typealias PEMFormattedString = String