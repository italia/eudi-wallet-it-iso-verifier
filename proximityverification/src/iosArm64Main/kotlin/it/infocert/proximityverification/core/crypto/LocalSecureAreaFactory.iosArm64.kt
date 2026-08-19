@file:OptIn(ExperimentalTime::class)

package it.infocert.proximityverification.core.crypto

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.NativeSQLiteDriver
import it.infocert.proximityverification.core.utils.Constants
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.storage.Storage
import org.multipaz.storage.sqlite.SqliteStorage
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSBundle
import kotlin.time.ExperimentalTime


actual object LocalSecureAreaFactory {

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class,
        ExperimentalForeignApi::class
    )
    private fun openDatabase(): SQLiteConnection {
        val fileManager = NSFileManager.defaultManager
        val rootPath = fileManager.URLForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        ) ?: error("Could not get documents directory URL")
        return NativeSQLiteDriver().open(rootPath.path() + "/${Constants.PLATFORM_STORAGE_FILE_NAME}")
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val platformStorage: Storage = SqliteStorage(
        connection = openDatabase(),
        coroutineContext = newSingleThreadContext("DB")
    )

    private val platformIsEmulator: Boolean by lazy {
        NSBundle.mainBundle.infoDictionary?.get("SIMULATOR_DEVICE_NAME") != null
    }


    actual fun createLocalSecureAreaProvider(): SecureAreaProvider<SecureArea> {
        return SecureAreaProvider {
            if (platformIsEmulator) {
                println("⚠️ WARNING: Running on simulator but attempting to use SecureEnclaveSecureArea. This may cause runtime errors.")
            }
            LocalSecureArea.create(platformStorage)
        }
    }
}
