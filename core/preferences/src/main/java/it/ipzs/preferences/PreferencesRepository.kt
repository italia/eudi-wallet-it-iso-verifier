package it.ipzs.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val datastore: DataStore<Preferences>
) {

    private val onboardingDisplayedKey = booleanPreferencesKey("onboarding_displayed")
    private val crlCertificateStringKey = stringPreferencesKey("crlCertificateStringKey")

    val crlCertificateString: Flow<DatastorePrefResult<String>> = datastore.data
        .map { preferences ->
            preferences[crlCertificateStringKey]?.let {
                DatastorePrefResult.Fetched(it)
            } ?: DatastorePrefResult.Missing
        }

    val isOnboardingDisplayed: Flow<DatastorePrefResult<Boolean>> = datastore.data
        .map { preferences ->
            preferences[onboardingDisplayedKey]?.let {
                DatastorePrefResult.Fetched(it)
            } ?: DatastorePrefResult.Missing
        }


    suspend fun getCrlCertificateString() : String? {
        return datastore.data.first().get(crlCertificateStringKey)
    }

    suspend fun saveCrlCertificateString(crlCertificateString: String) {
        datastore.edit { settings ->
            settings[crlCertificateStringKey] = crlCertificateString
        }
    }

    suspend fun setOnboardingDisplayed(displayed: Boolean) {
        datastore.edit { settings ->
            settings[onboardingDisplayedKey] = displayed
        }
    }

}