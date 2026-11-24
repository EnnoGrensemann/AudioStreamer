package com.vinylcast.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vinylcast.model.AppSettings
import com.vinylcast.model.AudioSourceOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vinylcast_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val STREAM_PORT = intPreferencesKey("stream_port")
        val PREFERRED_INPUT_ID = intPreferencesKey("preferred_input_id")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val GAIN_MULTIPLIER = floatPreferencesKey("gain_multiplier")
        val LAST_SONOS_UDN = stringPreferencesKey("last_sonos_udn")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val sourceName = prefs[Keys.AUDIO_SOURCE]
        val source = AudioSourceOption.entries.firstOrNull { it.name == sourceName } ?: AudioSourceOption.AUTO
        AppSettings(
            streamPort = prefs[Keys.STREAM_PORT] ?: 8765,
            preferredInputDeviceId = prefs[Keys.PREFERRED_INPUT_ID]?.takeIf { it > 0 },
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            audioSource = source,
            gainMultiplier = (prefs[Keys.GAIN_MULTIPLIER] ?: 1f).coerceIn(1f, 16f)
        )
    }

    val lastSonosUdnFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SONOS_UDN]
    }

    suspend fun updateStreamPort(port: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STREAM_PORT] = port.coerceIn(1024, 65535)
        }
    }

    suspend fun updatePreferredInputDevice(deviceId: Int?) {
        context.dataStore.edit { prefs ->
            if (deviceId == null || deviceId <= 0) {
                prefs.remove(Keys.PREFERRED_INPUT_ID)
            } else {
                prefs[Keys.PREFERRED_INPUT_ID] = deviceId
            }
        }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun updateAudioSource(source: AudioSourceOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_SOURCE] = source.name
        }
    }

    suspend fun updateGainMultiplier(gain: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GAIN_MULTIPLIER] = gain.coerceIn(1f, 16f)
        }
    }

    suspend fun updateLastSonosUdn(udn: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SONOS_UDN] = udn
        }
    }
}
