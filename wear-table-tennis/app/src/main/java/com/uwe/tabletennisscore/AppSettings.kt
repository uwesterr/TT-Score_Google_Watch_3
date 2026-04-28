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
    val matchMode: MatchMode = MatchMode.SINGLES,
    val meName: String = "Me",
    val myPartnerName: String = "Partner",
    val opponentName: String = "Opponent",
    val opponentPartnerName: String = "Opponent 2",
    val setsToWinMatch: Int = MatchFormat.BEST_OF_THREE.setsToWinMatch,
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    val matchFormat: MatchFormat
        get() = MatchFormat.fromSetsToWin(setsToWinMatch)

    fun displayName(player: Player): String = when (player) {
        Player.UWE -> if (matchMode == MatchMode.SINGLES) meName else "$meName / $myPartnerName"
        Player.OPPONENT -> if (matchMode == MatchMode.SINGLES) opponentName else "$opponentName / $opponentPartnerName"
    }

    fun seatName(seat: DoublesSeat): String = when (seat) {
        DoublesSeat.HOME_1 -> meName
        DoublesSeat.HOME_2 -> myPartnerName
        DoublesSeat.AWAY_1 -> opponentName
        DoublesSeat.AWAY_2 -> opponentPartnerName
    }

    fun pointButtonLabel(player: Player): String = when {
        matchMode == MatchMode.SINGLES -> displayName(player)
        player == Player.UWE -> "Home"
        else -> "Away"
    }

    fun phoneTeamLabel(player: Player): String = when (player) {
        Player.UWE -> if (matchMode == MatchMode.SINGLES) meName else "$meName\n$myPartnerName"
        Player.OPPONENT -> if (matchMode == MatchMode.SINGLES) opponentName else "$opponentName\n$opponentPartnerName"
    }

    companion object {
        fun sanitize(
            meName: String,
            opponentName: String,
            setsToWinMatch: Int,
            hapticsEnabled: Boolean,
            soundsEnabled: Boolean,
            keepScreenOn: Boolean,
        ): AppSettings = sanitize(
            matchMode = MatchMode.SINGLES,
            meName = meName,
            myPartnerName = "Partner",
            opponentName = opponentName,
            opponentPartnerName = "Opponent 2",
            setsToWinMatch = setsToWinMatch,
            hapticsEnabled = hapticsEnabled,
            soundsEnabled = soundsEnabled,
            keepScreenOn = keepScreenOn,
        )

        fun sanitize(
            matchMode: MatchMode,
            meName: String,
            myPartnerName: String,
            opponentName: String,
            opponentPartnerName: String,
            setsToWinMatch: Int,
            hapticsEnabled: Boolean,
            soundsEnabled: Boolean,
            keepScreenOn: Boolean,
        ): AppSettings = AppSettings(
            matchMode = matchMode,
            meName = meName.trim().ifBlank { "Me" },
            myPartnerName = myPartnerName.trim().ifBlank { "Partner" },
            opponentName = opponentName.trim().ifBlank { "Opponent" },
            opponentPartnerName = opponentPartnerName.trim().ifBlank { "Opponent 2" },
            setsToWinMatch = MatchFormat.fromSetsToWin(setsToWinMatch).setsToWinMatch,
            hapticsEnabled = hapticsEnabled,
            soundsEnabled = soundsEnabled,
            keepScreenOn = keepScreenOn,
        )
    }
}

enum class MatchMode(val label: String) {
    SINGLES("Singles"),
    DOUBLES("Doubles"),
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
            matchMode = prefs[Keys.matchMode]?.let {
                MatchMode.entries.firstOrNull { mode -> mode.name == it }
            } ?: MatchMode.SINGLES,
            meName = prefs[Keys.meName] ?: "Me",
            myPartnerName = prefs[Keys.myPartnerName] ?: "Partner",
            opponentName = prefs[Keys.opponentName] ?: "Opponent",
            opponentPartnerName = prefs[Keys.opponentPartnerName] ?: "Opponent 2",
            setsToWinMatch = prefs[Keys.setsToWinMatch] ?: MatchFormat.BEST_OF_THREE.setsToWinMatch,
            hapticsEnabled = prefs[Keys.hapticsEnabled] ?: true,
            soundsEnabled = prefs[Keys.soundsEnabled] ?: true,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
        )
    }

    suspend fun save(settings: AppSettings) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.matchMode] = settings.matchMode.name
            prefs[Keys.meName] = settings.meName
            prefs[Keys.myPartnerName] = settings.myPartnerName
            prefs[Keys.opponentName] = settings.opponentName
            prefs[Keys.opponentPartnerName] = settings.opponentPartnerName
            prefs[Keys.setsToWinMatch] = settings.setsToWinMatch
            prefs[Keys.hapticsEnabled] = settings.hapticsEnabled
            prefs[Keys.soundsEnabled] = settings.soundsEnabled
            prefs[Keys.keepScreenOn] = settings.keepScreenOn
        }
    }

    private object Keys {
        val matchMode = stringPreferencesKey("match_mode")
        val meName = stringPreferencesKey("me_name")
        val myPartnerName = stringPreferencesKey("my_partner_name")
        val opponentName = stringPreferencesKey("opponent_name")
        val opponentPartnerName = stringPreferencesKey("opponent_partner_name")
        val setsToWinMatch = intPreferencesKey("sets_to_win_match")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val soundsEnabled = booleanPreferencesKey("sounds_enabled")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    }
}
