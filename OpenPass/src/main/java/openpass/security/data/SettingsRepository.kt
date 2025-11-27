package openpass.security.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create the DataStore instance at the top level
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    private val IS_APP_LOCK_ENABLED = booleanPreferencesKey("is_app_lock_enabled")
    private val IS_BIOMETRICS_ENABLED = booleanPreferencesKey("is_biometrics_enabled")
    private val APP_LOCK_PASSWORD = stringPreferencesKey("app_lock_password")
    private val SORT_ORDER = stringPreferencesKey("sort_order")
    private val PREVENT_SCREENSHOTS = booleanPreferencesKey("prevent_screenshots")

    // Expose a Flow that emits the current dark mode preference
    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_DARK_MODE] ?: false // Default to light mode
        }
    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_APP_LOCK_ENABLED] ?: false }
    val isBiometricsEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_BIOMETRICS_ENABLED] ?: false }
    val appLockPassword: Flow<String?> = context.dataStore.data.map { it[APP_LOCK_PASSWORD] }

    val sortOrder: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SORT_ORDER] ?: "name" // Default to sort by name
       }
    val preventScreenshots: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
        preferences[PREVENT_SCREENSHOTS] ?: false
    }


    // Function to update the preference
    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { settings ->
            settings[IS_DARK_MODE] = isDark
        }
    }
    suspend fun setAppLockEnabled(isEnabled: Boolean) {
        context.dataStore.edit { it[IS_APP_LOCK_ENABLED] = isEnabled }
    }
    suspend fun setBiometricsEnabled(isEnabled: Boolean) {
        context.dataStore.edit { it[IS_BIOMETRICS_ENABLED] = isEnabled }
    }
    suspend fun setAppLockPassword(password: String) {
        context.dataStore.edit { it[APP_LOCK_PASSWORD] = password }
    }
    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { it[SORT_ORDER] = order }
    }
    suspend fun setPreventScreenshots(isEnabled: Boolean) {
        context.dataStore.edit { it[PREVENT_SCREENSHOTS] = isEnabled }
    }
}