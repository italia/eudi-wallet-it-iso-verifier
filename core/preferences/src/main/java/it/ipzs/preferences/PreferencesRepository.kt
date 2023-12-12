package it.ipzs.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val datastore: DataStore<Preferences>
) {

    private val onboardingDisplayedKey = booleanPreferencesKey("onboarding_displayed")

    val isOnboardingDisplayed: Flow<DatastorePrefResult<Boolean>> = datastore.data
        .map { preferences ->
            preferences[onboardingDisplayedKey]?.let {
                DatastorePrefResult.Fetched(it)
            } ?: DatastorePrefResult.Missing
        }

    suspend fun setOnboardingDisplayed(displayed: Boolean) {
        datastore.edit { settings ->
            settings[onboardingDisplayedKey] = displayed
        }
    }

}