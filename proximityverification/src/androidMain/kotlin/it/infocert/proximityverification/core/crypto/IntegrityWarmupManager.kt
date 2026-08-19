package it.infocert.proximityverification.core.crypto

import android.Manifest
import androidx.annotation.RequiresPermission
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import it.infocert.proximityverification.ContextInitializer
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.exceptions.ProximityVerificationException
import it.infocert.proximityverification.exceptions.ProximityVerificationExceptionCode
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual object IntegrityWarmupManager {
    private const val TAG = "IntegrityWarmupManager"
    actual var alreadyWarmedUp = false
    lateinit var integrityTokenProvider: StandardIntegrityTokenProvider

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual suspend fun warmUp(data: String): String {
        val mn = "warmUp"
        if (alreadyWarmedUp) return data
        val keyTagStorage = provideLocalStorage()
        val logicalKey = Constants.ANDROID_DEVICE_CHECK_KEY_ALIAS

        try {
            val isDeviceOnline = NativeUtils.isNetworkAvailable()
            if (!isDeviceOnline) {
                throw ProximityVerificationException(
                    "Device attestation requires internet connectivity. No usable internet connection is available. Ensure the device is connected to the internet and try again.",
                    code = ProximityVerificationExceptionCode.PVE15.default()
                )
            }

            val standardIntegrityManager: StandardIntegrityManager =
                IntegrityManagerFactory.createStandard(ContextInitializer.applicationContext)

            integrityTokenProvider = suspendCancellableCoroutine { continuation ->
                standardIntegrityManager.prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(data.toLong())
                        .build()
                ).addOnSuccessListener { tokenProvider ->
                    continuation.resume(tokenProvider)
                    alreadyWarmedUp = true
                }.addOnFailureListener { exception ->
                    alreadyWarmedUp = false
                    continuation.resumeWithException(
                        ProximityVerificationException(
                            exception.message.toString(),
                            code = ProximityVerificationExceptionCode.PVE12.default()
                        )
                    )
                }
            }

            keyTagStorage.saveElement(logicalKey, data)
            return data
        } catch (e: ProximityVerificationException) {
            Logger.error(TAG) { "$mn - Error during device check warm up: ${e.message}" }
            throw e  // Propagate ProximityVerificationException as-is
        } catch (e: Exception) {
            Logger.error(TAG) { "$mn - Error during device check warm up: ${e.message}" }
            throw ProximityVerificationException(
                "Error during device check warm up: $e", e,
                code = ProximityVerificationExceptionCode.PVE12()
            )
        }
    }
}
