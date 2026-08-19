package it.infocert.proximityverification.network

import it.infocert.proximityverification.network.model.*
import it.infocert.proximityverification.network.model.common.AccessCertificateResponse
import it.infocert.proximityverification.network.model.common.DeviceInfo
import it.infocert.proximityverification.network.model.common.KeyBindingResponse
import it.infocert.proximityverification.network.model.common.NonceResponse
import it.infocert.proximityverification.network.model.common.RegisterDeviceData
import it.infocert.proximityverification.network.model.common.RegisterDeviceResponse
import it.infocert.proximityverification.network.model.common.RefreshTokenResponse
import it.infocert.proximityverification.network.model.common.WellKnownConfiguration

interface RelyingPartyApi {
    suspend fun fetchNonce(): ApiResult<NonceResponse>
    suspend fun registerDevice(deviceInfo: DeviceInfo, data: RegisterDeviceData?): ApiResult<RegisterDeviceResponse>
    suspend fun refreshToken(refreshToken: String): ApiResult<RefreshTokenResponse>
    suspend fun bindKeys(accessToken: String, jwt: String): ApiResult<KeyBindingResponse>
    suspend fun requestAccessCertificate(accessToken: String, csr: String): ApiResult<AccessCertificateResponse>
    suspend fun fetchWellKnownConfiguration(wellKnownUrl: String): ApiResult<WellKnownConfiguration>
}