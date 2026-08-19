package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.utils.Constants
import platform.Foundation.NSUserDefaults

class IosLocalStorage : LocalStorage {
    private val userDefault = NSUserDefaults(suiteName = Constants.KEY_TAGS_FILE_NAME)

    override suspend fun saveElement(key: String, value: String) {
        userDefault.setObject(value, forKey = key)
    }

    override suspend fun getElement(key: String): String? {
        return userDefault.stringForKey(key)
    }

    override suspend fun clear() {
        val dictionary = userDefault.dictionaryRepresentation()
        val keys = dictionary.keys
        for (key in keys) {
            userDefault.removeObjectForKey(key as String)
        }
        userDefault.synchronize()
    }

    override suspend fun clearElement(logicalKey: String) {
        userDefault.removeObjectForKey(logicalKey)
        userDefault.synchronize()
    }
}

actual fun provideLocalStorage(): LocalStorage = IosLocalStorage()