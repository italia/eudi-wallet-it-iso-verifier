package it.infocert.proximityverification.status

import it.infocert.proximityverification.core.crypto.LocalStorage

internal object StatusListTestSupport {

    fun inMemoryStorage(): LocalStorage = object : LocalStorage {
        private val map: MutableMap<String, String> = mutableMapOf()
        override suspend fun saveElement(key: String, value: String) {
            map[key] = value
        }
        override suspend fun getElement(key: String): String? = map[key]
        override suspend fun clear() {
            map.clear()
        }
        override suspend fun clearElement(logicalKey: String) {
            map.remove(logicalKey)
        }
    }
}
