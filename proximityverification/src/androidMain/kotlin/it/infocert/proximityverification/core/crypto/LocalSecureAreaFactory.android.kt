package it.infocert.proximityverification.core.crypto

import android.os.Build
import it.infocert.proximityverification.ContextInitializer
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.logging.Logger
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.storage.Storage
import org.multipaz.storage.android.AndroidStorage
import kotlin.time.ExperimentalTime

actual object LocalSecureAreaFactory {

    private const val TAG = "LocalSecureAreaFactory"

    actual fun createLocalSecureAreaProvider(): SecureAreaProvider<SecureArea> {
        val mn = "createLocalSecureAreaProvider"
        return SecureAreaProvider {
            if (platformIsEmulator) {
                Logger.warn(TAG) {
                    "$mn - WARNING: Using LocalSecureArea on emulator"
                }
            }
            LocalSecureArea.create(platformStorage())
        }
    }

    private val platformIsEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.lowercase().contains("vbox")
                || Build.FINGERPRINT.lowercase().contains("test-keys")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
    }

    @OptIn(ExperimentalTime::class)
    private fun platformStorage(): Storage {
        val path = java.io.File(
            ContextInitializer.applicationContext.noBackupFilesDir.path,
            Constants.PLATFORM_STORAGE_FILE_NAME
        ).absolutePath
        return AndroidStorage(path)
    }
}
