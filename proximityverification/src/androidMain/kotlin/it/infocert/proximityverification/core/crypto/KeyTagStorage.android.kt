package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.ContextInitializer
import it.infocert.proximityverification.core.utils.Constants

class AndroidLocalStorage : LocalStorage {
    private val prefs = ContextInitializer.applicationContext.getSharedPreferences(
        Constants.KEY_TAGS_FILE_NAME,
        android.content.Context.MODE_PRIVATE
    )

    override suspend fun saveElement(logicalKey: String, hardwareKeyTag: String) {
        prefs.edit().putString(logicalKey, hardwareKeyTag).apply()
    }

    override suspend fun getElement(logicalKey: String): String? =
        prefs.getString(logicalKey, null)

    override suspend fun clearElement(logicalKey: String) {
        if (prefs.getString(logicalKey, null) != null) prefs.edit().remove(logicalKey).apply()
    }
    override suspend fun clear() {
        prefs.edit().clear().apply()
    }
}

actual fun provideLocalStorage(): LocalStorage = AndroidLocalStorage()