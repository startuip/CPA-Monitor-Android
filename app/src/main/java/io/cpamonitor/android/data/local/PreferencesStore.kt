package io.cpamonitor.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("cpa_monitor_preferences")

data class ConnectionConfig(val baseUrl: String, val adminKey: String)

data class UserSettings(
    val refreshSeconds: Int = 30,
    val quotaThreshold: Int = 20,
    val alertsEnabled: Boolean = true,
    val quotaAlerts: Boolean = true,
    val failureAlerts: Boolean = true,
    val connectionAlerts: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
)

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: SecretCipher,
) {
    val hasConnection: Flow<Boolean> = context.dataStore.data.map {
        !it[Keys.BASE_URL].isNullOrBlank() && !it[Keys.ENCRYPTED_KEY].isNullOrBlank()
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { it[Keys.BASE_URL] }

    val settings: Flow<UserSettings> = context.dataStore.data.map {
        UserSettings(
            refreshSeconds = it[Keys.REFRESH_SECONDS] ?: 30,
            quotaThreshold = it[Keys.QUOTA_THRESHOLD] ?: 20,
            alertsEnabled = it[Keys.ALERTS_ENABLED] ?: true,
            quotaAlerts = it[Keys.QUOTA_ALERTS] ?: true,
            failureAlerts = it[Keys.FAILURE_ALERTS] ?: true,
            connectionAlerts = it[Keys.CONNECTION_ALERTS] ?: true,
            notificationPermissionAsked = it[Keys.NOTIFICATION_PERMISSION_ASKED] ?: false,
        )
    }

    suspend fun connection(): ConnectionConfig? {
        val prefs = context.dataStore.data.first()
        val url = prefs[Keys.BASE_URL]?.takeIf { it.isNotBlank() } ?: return null
        val encrypted = prefs[Keys.ENCRYPTED_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val key = try {
            cipher.decrypt(encrypted)
        } catch (_: GeneralSecurityException) {
            clearConnection()
            return null
        } catch (_: IllegalArgumentException) {
            clearConnection()
            return null
        } catch (_: IllegalStateException) {
            clearConnection()
            return null
        }
        return ConnectionConfig(url, key)
    }

    suspend fun saveConnection(baseUrl: String, adminKey: String) {
        val encrypted = cipher.encrypt(adminKey)
        context.dataStore.edit {
            it[Keys.BASE_URL] = baseUrl
            it[Keys.ENCRYPTED_KEY] = encrypted
        }
    }

    suspend fun clearConnection() {
        context.dataStore.edit {
            it.remove(Keys.BASE_URL)
            it.remove(Keys.ENCRYPTED_KEY)
        }
        cipher.deleteKey()
    }

    suspend fun updateSettings(value: UserSettings) {
        context.dataStore.edit {
            it[Keys.REFRESH_SECONDS] = value.refreshSeconds.coerceIn(10, 300)
            it[Keys.QUOTA_THRESHOLD] = value.quotaThreshold.coerceIn(1, 100)
            it[Keys.ALERTS_ENABLED] = value.alertsEnabled
            it[Keys.QUOTA_ALERTS] = value.quotaAlerts
            it[Keys.FAILURE_ALERTS] = value.failureAlerts
            it[Keys.CONNECTION_ALERTS] = value.connectionAlerts
            it[Keys.NOTIFICATION_PERMISSION_ASKED] = value.notificationPermissionAsked
        }
    }

    suspend fun markNotificationPermissionAsked() {
        context.dataStore.edit { it[Keys.NOTIFICATION_PERMISSION_ASKED] = true }
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val ENCRYPTED_KEY = stringPreferencesKey("encrypted_admin_key")
        val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        val QUOTA_THRESHOLD = intPreferencesKey("quota_threshold")
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val QUOTA_ALERTS = booleanPreferencesKey("quota_alerts")
        val FAILURE_ALERTS = booleanPreferencesKey("failure_alerts")
        val CONNECTION_ALERTS = booleanPreferencesKey("connection_alerts")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
    }
}
