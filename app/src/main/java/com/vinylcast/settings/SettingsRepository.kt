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
import com.vinylcast.model.EqPreset
import com.vinylcast.model.EqSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vinylcast_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val STREAM_PORT = intPreferencesKey("stream_port")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val GAIN_MULTIPLIER = floatPreferencesKey("gain_multiplier")
        val LAST_SONOS_UDN = stringPreferencesKey("last_sonos_udn")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BASS = floatPreferencesKey("eq_bass")
        val EQ_MID = floatPreferencesKey("eq_mid")
        val EQ_TREBLE = floatPreferencesKey("eq_treble")
        val PREFERRED_OUTPUT_ID = intPreferencesKey("preferred_output_id")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val presetName = prefs[Keys.EQ_PRESET]
        val preset = EqPreset.entries.firstOrNull { it.name == presetName } ?: EqPreset.FLAT
        AppSettings(
            streamPort = prefs[Keys.STREAM_PORT] ?: 8765,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            gainMultiplier = (prefs[Keys.GAIN_MULTIPLIER] ?: 1f).coerceIn(1f, 16f),
            eq = EqSettings(
                preset = preset,
                bassDb = (prefs[Keys.EQ_BASS] ?: preset.bassDb).coerceIn(-12f, 12f),
                midDb = (prefs[Keys.EQ_MID] ?: preset.midDb).coerceIn(-12f, 12f),
                trebleDb = (prefs[Keys.EQ_TREBLE] ?: preset.trebleDb).coerceIn(-12f, 12f)
            ),
            preferredOutputDeviceId = prefs[Keys.PREFERRED_OUTPUT_ID]?.takeIf { it > 0 }
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

    suspend fun updateGainMultiplier(gain: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GAIN_MULTIPLIER] = gain.coerceIn(1f, 16f)
        }
    }

    suspend fun updateEqSettings(eq: EqSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EQ_PRESET] = eq.preset.name
            prefs[Keys.EQ_BASS] = eq.bassDb.coerceIn(-12f, 12f)
            prefs[Keys.EQ_MID] = eq.midDb.coerceIn(-12f, 12f)
            prefs[Keys.EQ_TREBLE] = eq.trebleDb.coerceIn(-12f, 12f)
        }
    }

    suspend fun updatePreferredOutputDevice(deviceId: Int?) {
        context.dataStore.edit { prefs ->
            if (deviceId == null || deviceId <= 0) {
                prefs.remove(Keys.PREFERRED_OUTPUT_ID)
            } else {
                prefs[Keys.PREFERRED_OUTPUT_ID] = deviceId
            }
        }
    }

    suspend fun updateLastSonosUdn(udn: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SONOS_UDN] = udn
        }
    }
}
