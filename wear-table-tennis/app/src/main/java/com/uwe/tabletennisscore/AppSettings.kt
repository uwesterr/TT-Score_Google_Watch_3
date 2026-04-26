package com.uwe.tabletennisscore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val meName: String = "Me",
    val opponentName: String = "Opponent",
    val setsToWinMatch: Int = MatchFormat.BEST_OF_THREE.setsToWinMatch,
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    val matchFormat: MatchFormat
        get() = MatchFormat.fromSetsToWin(setsToWinMatch)

    fun displayName(player: Player): String = when (player) {
        Player.UWE -> meName
        Player.OPPONENT -> opponentName
    }

    companion object {
        fun sanitize(
            meName: String,
            opponentName: String,
            setsToWinMatch: Int,
            hapticsEnabled: Boolean,
            soundsEnabled: Boolean,
            keepScreenOn: Boolean,
        ): AppSettings = AppSettings(
            meName = meName.trim().ifBlank { "Me" },
            opponentName = opponentName.trim().ifBlank { "Opponent" },
            setsToWinMatch = MatchFormat.fromSetsToWin(setsToWinMatch).setsToWinMatch,
            hapticsEnabled = hapticsEnabled,
            soundsEnabled = soundsEnabled,
            keepScreenOn = keepScreenOn,
        )
    }
}

enum class MatchFormat(val label: String, val setsToWinMatch: Int) {
    BEST_OF_THREE("Best of 3", 2),
    BEST_OF_FIVE("Best of 5", 3);

    val totalSets: Int get() = setsToWinMatch * 2 - 1

    companion object {
        fun fromSetsToWin(setsToWinMatch: Int): MatchFormat =
            entries.firstOrNull { it.setsToWinMatch == setsToWinMatch } ?: BEST_OF_THREE
    }
}

class AppSettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings.sanitize(
            meName = prefs[Keys.meName] ?: "Me",
            opponentName = prefs[Keys.opponentName] ?: "Opponent",
            setsToWinMatch = prefs[Keys.setsToWinMatch] ?: MatchFormat.BEST_OF_THREE.setsToWinMatch,
            hapticsEnabled = prefs[Keys.hapticsEnabled] ?: true,
            soundsEnabled = prefs[Keys.soundsEnabled] ?: true,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
        )
    }

    suspend fun save(settings: AppSettings) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.meName] = settings.meName
            prefs[Keys.opponentName] = settings.opponentName
            prefs[Keys.setsToWinMatch] = settings.setsToWinMatch
            prefs[Keys.hapticsEnabled] = settings.hapticsEnabled
            prefs[Keys.soundsEnabled] = settings.soundsEnabled
            prefs[Keys.keepScreenOn] = settings.keepScreenOn
        }
    }

    private object Keys {
        val meName = stringPreferencesKey("me_name")
        val opponentName = stringPreferencesKey("opponent_name")
        val setsToWinMatch = intPreferencesKey("sets_to_win_match")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val soundsEnabled = booleanPreferencesKey("sounds_enabled")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    }
}
