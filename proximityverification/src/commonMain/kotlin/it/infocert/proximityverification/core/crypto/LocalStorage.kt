package it.infocert.proximityverification.core.crypto

interface LocalStorage {
    suspend fun saveElement(key: String, value: String)
    suspend fun getElement(key: String): String?
    suspend fun clear()
    suspend fun clearElement(logicalKey: String)
}

expect fun provideLocalStorage(): LocalStorage