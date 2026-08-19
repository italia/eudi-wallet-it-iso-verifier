package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.network.RelyingPartyApiImpl
import it.infocert.proximityverification.network.model.ApiResult
import it.infocert.proximityverification.network.model.common.DeviceInfo
import it.infocert.proximityverification.network.model.common.RegisterDeviceData
import it.infocert.proximityverification.network.model.common.RegisterDeviceResponse
import it.infocert.proximityverification.network.model.common.RefreshTokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object TokenManager {
    private const val TAG = "TokenManager"
    private val tokenMutex = Mutex()
    private val storage = provideLocalStorage()

    /**
     * Store attestation data from attestDevice() for later use in registration
     */
    suspend fun storeAttestationData(data: String?) {
        val mn = "storeAttestationData"
        if (data != null && data.isNotEmpty()) {
            storage.saveElement(Constants.REGISTER_DEVICE_DATA, data)
            Logger.debug(TAG) { "$mn - Attestation data stored" }
        }
    }

    /**
     * Clear attestation data (used before re-registration to force new attestation)
     */
    suspend fun clearAttestationData() {
        val mn = "clearAttestationData"
        storage.clearElement(Constants.REGISTER_DEVICE_DATA)
        storage.clearElement(Constants.IOS_DEVICE_CHECK_ATTESTATION_OBJ)
        storage.clearElement(Constants.IOS_DEVICE_CHECK_KEY_ALIAS)
        Logger.debug(TAG) { "$mn - Attestation data cleared" }
    }

    /**
     * Check if we need to register (no valid access token exists)
     */
    suspend fun needsRegistration(baseUrl: String): Boolean {
        val mn = "needsRegistration"
        
        // Check if baseUrl has changed
        val storedBaseUrl = getStoredBaseUrl()
        if (storedBaseUrl != null && storedBaseUrl != baseUrl) {
            Logger.debug(TAG) { "$mn - BaseUrl changed, registration needed" }
            return true
        }

        // Check for valid access token
        val accessToken = storage.getElement(Constants.ACCESS_TOKEN)
        if (accessToken == null || isTokenExpired(accessToken)) {
            // Check if we can refresh (refresh token is UUID, so just check existence)
            val refreshToken = storage.getElement(Constants.REFRESH_TOKEN)
            if (refreshToken.isNullOrEmpty()) {
                Logger.debug(TAG) { "$mn - No valid tokens, registration needed" }
                return true
            }

            if(!tokenCanBeRefreshed(baseUrl)){
                Logger.debug(TAG) { "$mn - Refresh token cannot be refreshed! Registration needed" }
                return true
            }

            Logger.debug(TAG) { "$mn - tokens refreshed, no registration needed" }
            return false
        }
        
        Logger.debug(TAG) { "$mn - Valid token exists, no registration needed" }
        return false
    }


    private suspend fun tokenCanBeRefreshed(baseUrl:String):Boolean{
        val mn = "tokenCanBeRefreshed"
        val refreshToken = storage.getElement(Constants.REFRESH_TOKEN)
        if (!refreshToken.isNullOrEmpty()) {
            try {
                refreshAccessToken(baseUrl)
                Logger.debug(TAG) { "$mn - Refresh token refreshed!" }
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storage.clearElement(Constants.REFRESH_TOKEN)
                Logger.warn(TAG) { "$mn - Refresh token failed, try re-registering device..." }
                return false
            }
        }
        return false
    }

    /**
     * Store DeviceInfo for re-registration scenarios
     */
    suspend fun storeDeviceInfo(deviceInfo: DeviceInfo) {
        val mn = "storeDeviceInfo"
        val json = Json.encodeToString(DeviceInfo.serializer(), deviceInfo)
        storage.saveElement(Constants.DEVICE_INFO_JSON, json)
        Logger.debug(TAG) { "$mn - DeviceInfo stored" }
    }

    /**
     * Retrieve stored DeviceInfo
     */
    suspend fun getStoredDeviceInfo(): DeviceInfo? {
        val mn = "getStoredDeviceInfo"
        val json = storage.getElement(Constants.DEVICE_INFO_JSON) ?: return null
        return try {
            Json.decodeFromString(DeviceInfo.serializer(), json)
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Failed to decode DeviceInfo: ${e.message}" }
            null
        }
    }

    /**
     * Store baseUrl to ensure consistency and detect changes
     */
    suspend fun storeBaseUrl(baseUrl: String) {
        storage.saveElement(Constants.STORED_BASE_URL, baseUrl)
    }

    /**
     * Get stored baseUrl
     */
    suspend fun getStoredBaseUrl(): String? {
        return storage.getElement(Constants.STORED_BASE_URL)
    }

    /**
     * Ensure valid access token, refreshing or re-registering as needed
     */
    suspend fun ensureValidAccessToken(baseUrl: String): String = tokenMutex.withLock {
        val mn = "ensureValidAccessToken"
        Logger.debug(TAG) { "$mn - Checking access token validity" }

        // Check if baseUrl has changed
        val storedBaseUrl = getStoredBaseUrl()
        if (storedBaseUrl != null && storedBaseUrl != baseUrl) {
            Logger.warn(TAG) { "$mn - BaseUrl changed from $storedBaseUrl to $baseUrl, clearing tokens" }
            clearTokens()
        }

        // Try to get existing access token
        val accessToken = storage.getElement(Constants.ACCESS_TOKEN)
        if (accessToken != null && !isTokenExpired(accessToken)) {
            Logger.debug(TAG) { "$mn - Valid access token found" }
            return@withLock accessToken
        }

        Logger.info(TAG) { "$mn - Access token missing or expired, attempting refresh" }

        // Try to refresh using refresh token (refresh token is UUID, not JWT, so just check existence)
        val refreshToken = storage.getElement(Constants.REFRESH_TOKEN)
        if (!refreshToken.isNullOrEmpty()) {
            try {
                val newRT = refreshAccessToken(baseUrl)
                return@withLock newRT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storage.clearElement(Constants.REFRESH_TOKEN)
                Logger.warn(TAG) { "$mn - Refresh token failed, try re-registering device..." }
            }
        }

        // Refresh token unavailable or refresh failed, re-register device
        Logger.info(TAG) { "$mn - Refresh token unavailable or refresh failed, re-registering device" }

        // check connectivity, if device is not online we cannot re-register and try continue
        if (!NativeUtils.isNetworkAvailable()) {
            Logger.warn(TAG) { "$mn - Device is offline, cannot re-register. Trying to continue with existing access token." }
            val existingToken = storage.getElement(Constants.ACCESS_TOKEN)
            if (existingToken != null) {
                Logger.info(TAG) { "$mn - Existing access token found, returning it despite being expired." }
                return@withLock existingToken
            } else {
                Logger.error(TAG) { "$mn - No existing access token available, cannot proceed while offline." }
                throw ProximityVerificationException(
                    "Device is offline and no valid access token available for re-registration.",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }
        } else {
            Logger.info(TAG) { "$mn - Device is online, proceeding with re-registration." }
        }

        return@withLock reRegisterDevice(baseUrl)
    }

    /**
     * Refresh access token using refresh token
     */
    private suspend fun refreshAccessToken(baseUrl: String): String {
        val mn = "refreshAccessToken"
        Logger.info(TAG) { "$mn - Refreshing access token" }

        val refreshToken = storage.getElement(Constants.REFRESH_TOKEN)
            ?: throw ProximityVerificationException(
                "No refresh token available",
                code = ProximityVerificationExceptionCode.PVE08.default()
            )

        val api = RelyingPartyApiImpl(baseUrl)
        return when (val result = api.refreshToken(refreshToken)) {
            is ApiResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val response = (result as ApiResult.Success<RefreshTokenResponse>).data
                val data = response.data
                val newAccessToken = data?.token
                val newRefreshToken = data?.refreshToken

                if (newAccessToken == null || newAccessToken.isEmpty()) {
                    Logger.error(TAG) { "$mn - Token refresh succeeded but no token received" }
                    throw ProximityVerificationException(
                        "Token refresh succeeded but no access token received",
                        code = ProximityVerificationExceptionCode.PVE08.default()
                    )
                }

                // Update stored tokens
                storage.saveElement(Constants.ACCESS_TOKEN, newAccessToken)
                if (newRefreshToken != null && newRefreshToken.isNotEmpty()) {
                    storage.saveElement(Constants.REFRESH_TOKEN, newRefreshToken)
                }

                Logger.info(TAG) { "$mn - Access token refreshed successfully" }
                newAccessToken
            }
            is ApiResult.Failure -> {
                val errorDesc = result.error?.errorDescription ?: "Unknown error"
                Logger.error(TAG) { "$mn - Token refresh failed: $errorDesc" }
                throw ProximityVerificationException(
                    "Token refresh failed: $errorDesc",
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            }
            is ApiResult.InternalError -> {
                Logger.error(TAG) { "$mn - Token refresh exception: ${result.exception.message}" }
                throw result.exception
            }
        }
    }

    /**
     * Re-register device when refresh token is expired
     * Note: This requires valid attestation data. If attestation is missing or invalid,
     * it clears all tokens and attestation data, then throws an exception requiring reinitialization.
     */
    private suspend fun reRegisterDevice(baseUrl: String): String {
        val mn = "reRegisterDevice"
        Logger.info(TAG) { "$mn - Re-registering device" }

        // Retrieve stored DeviceInfo
        val deviceInfo = getStoredDeviceInfo()
            ?: throw ProximityVerificationException(
                "Cannot re-register device: DeviceInfo not found. Please call initialize() again.",
                code = ProximityVerificationExceptionCode.PVE13()
            )

        // Check if we need fresh attestation (no stored attestation data)
        val attestationDataJson = storage.getElement(Constants.REGISTER_DEVICE_DATA)
        
        // If no attestation data exists, we need to re-attest
        // This can happen if tokens expired after app restart or after a long time
        if (attestationDataJson.isNullOrEmpty()) {
            Logger.warn(TAG) { "$mn - No attestation data found, clearing tokens to force fresh attestation" }
            
            // Clear all tokens and attestation to force fresh registration cycle
            storage.clearElement(Constants.ACCESS_TOKEN)
            storage.clearElement(Constants.REFRESH_TOKEN)
            storage.clearElement(Constants.DEVICE_ID)
            clearAttestationData()
            
            throw ProximityVerificationException(
                "Cannot re-register device: Attestation data not found. Please call initialize() again to re-attest.",
                code = ProximityVerificationExceptionCode.PVE13()
            )
        }

        var registerDeviceData: RegisterDeviceData? = null
        // Get stored attestation data (iOS)
        if (NativeUtils.isIos()) {
            Logger.debug(TAG) { "$mn - iOS platform detected, parsing stored attestation data" }
            registerDeviceData = try {
                Json.decodeFromString(RegisterDeviceData.serializer(), attestationDataJson)
            } catch (e: Exception) {
                Logger.warn(TAG) { "$mn - Failed to decode attestation data, clearing tokens to force fresh attestation" }

                // Clear all tokens and attestation to force fresh registration cycle
                storage.clearElement(Constants.ACCESS_TOKEN)
                storage.clearElement(Constants.REFRESH_TOKEN)
                storage.clearElement(Constants.DEVICE_ID)
                clearAttestationData()

                throw ProximityVerificationException(
                    "Cannot re-register device: Invalid attestation data. Please call initialize() again to re-attest.",
                    code = ProximityVerificationExceptionCode.PVE13()
                )
            }
        }

        val api = RelyingPartyApiImpl(baseUrl)
        return when (val result = api.registerDevice(deviceInfo, registerDeviceData)) {
            is ApiResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val response = (result as ApiResult.Success<RegisterDeviceResponse>).data
                val data = response.data
                val token = data?.token
                val refreshToken = data?.refreshToken
                val deviceId = data?.deviceId

                if (token == null || token.isEmpty()) {
                    Logger.error(TAG) { "$mn - Re-registration succeeded but no token received" }
                    throw ProximityVerificationException(
                        "Re-registration succeeded but no access token received",
                        code = ProximityVerificationExceptionCode.PVE08.default()
                    )
                }

                // Update stored tokens
                storage.saveElement(Constants.ACCESS_TOKEN, token)
                if (refreshToken != null && refreshToken.isNotEmpty()) {
                    storage.saveElement(Constants.REFRESH_TOKEN, refreshToken)
                }
                if (deviceId != null && deviceId.isNotEmpty()) {
                    storage.saveElement(Constants.DEVICE_ID, deviceId)
                }

                Logger.info(TAG) { "$mn - Device re-registered successfully" }
                token
            }
            is ApiResult.Failure -> {
                val errorDesc = result.error?.errorDescription ?: "Unknown error"
                Logger.error(TAG) { "$mn - Re-registration failed (HTTP ${result.code}): $errorDesc" }
                
                // For authentication/authorization errors, clear attestation to force fresh cycle
                if (result.code in listOf(400, 401, 403, 422)) {
                    Logger.warn(TAG) { "$mn - Authentication error, clearing tokens and attestation data" }
                    storage.clearElement(Constants.ACCESS_TOKEN)
                    storage.clearElement(Constants.REFRESH_TOKEN)
                    storage.clearElement(Constants.DEVICE_ID)
                    clearAttestationData()
                }
                
                throw ProximityVerificationException(
                    "Device re-registration failed: $errorDesc",
                    code = ProximityVerificationExceptionCode.PVE08.default()
                )
            }
            is ApiResult.InternalError -> {
                Logger.error(TAG) { "$mn - Re-registration exception: ${result.exception.message}" }
                
                // For internal errors, clear attestation to force fresh cycle on next attempt
                Logger.warn(TAG) { "$mn - Internal error, clearing tokens and attestation data" }
                storage.clearElement(Constants.ACCESS_TOKEN)
                storage.clearElement(Constants.REFRESH_TOKEN)
                storage.clearElement(Constants.DEVICE_ID)
                clearAttestationData()
                
                throw result.exception
            }
        }
    }

    /**
     * Check if JWT token is expired
     */
    private fun isTokenExpired(jwt: String): Boolean {
        return try {
            val exp = getJwtExpiration(jwt)
            val now = Clock.System.now().epochSeconds
            val isExpired = now >= exp
            Logger.debug(TAG) { "isTokenExpired - exp=$exp, now=$now, expired=$isExpired" }
            isExpired
        } catch (e: Exception) {
            Logger.error(TAG) { "isTokenExpired - Failed to check expiration: ${e.message}" }
            true // Assume expired if we can't parse
        }
    }

    /**
     * Decode JWT payload
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeJwtPayload(jwt: String): JsonObject {
        val parts = jwt.split(".")
        if (parts.size != 3) {
            throw ProximityVerificationException(
                "Invalid JWT format: expected 3 parts but got ${parts.size}",
                code = ProximityVerificationExceptionCode.PVE08.default()
            )
        }

        val normalized = parts[1].replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val decoded = Base64.decode(padded.encodeToByteArray())
        
        return Json.decodeFromString(JsonObject.serializer(), decoded.decodeToString())
    }

    /**
     * Get JWT expiration timestamp
     */
    private fun getJwtExpiration(jwt: String): Long {
        val payload = decodeJwtPayload(jwt)
        return payload["exp"]?.jsonPrimitive?.long ?: 0L
    }

    /**
     * Clear all stored tokens and related data
     */
    suspend fun clearTokens() {
        val mn = "clearTokens"
        Logger.info(TAG) { "$mn - Clearing all tokens and related data" }
        storage.clearElement(Constants.ACCESS_TOKEN)
        storage.clearElement(Constants.REFRESH_TOKEN)
        storage.clearElement(Constants.DEVICE_ID)
        storage.clearElement(Constants.STORED_BASE_URL)
        storage.clearElement(Constants.DEVICE_INFO_JSON)
        clearAttestationData()
    }
}
