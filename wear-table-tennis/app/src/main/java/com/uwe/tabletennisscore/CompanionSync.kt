package com.uwe.tabletennisscore

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val COMPANION_SCORE_PATH = "/tt_score/current_state"

internal object CompanionScoreKeys {
    const val meName = "me_name"
    const val opponentName = "opponent_name"
    const val mePoints = "me_points"
    const val opponentPoints = "opponent_points"
    const val currentServerName = "current_server_name"
    const val meSetsWon = "me_sets_won"
    const val opponentSetsWon = "opponent_sets_won"
    const val currentSetNumber = "current_set_number"
    const val matchStatus = "match_status"
    const val updatedAt = "updated_at"
}

internal class WatchScorePublisher(context: Context) {
    private val dataClient = Wearable.getDataClient(context)

    suspend fun publish(
        state: MatchState,
        settings: AppSettings,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val request = PutDataMapRequest.create(COMPANION_SCORE_PATH).apply {
                dataMap.putString(CompanionScoreKeys.meName, settings.meName)
                dataMap.putString(CompanionScoreKeys.opponentName, settings.opponentName)
                dataMap.putInt(CompanionScoreKeys.mePoints, state.currentSet.uwePoints)
                dataMap.putInt(CompanionScoreKeys.opponentPoints, state.currentSet.opponentPoints)
                dataMap.putString(
                    CompanionScoreKeys.currentServerName,
                    MatchRules.currentServer(state.currentSet)?.let(settings::displayName).orEmpty(),
                )
                dataMap.putInt(CompanionScoreKeys.meSetsWon, state.uweSetsWon)
                dataMap.putInt(CompanionScoreKeys.opponentSetsWon, state.opponentSetsWon)
                dataMap.putInt(CompanionScoreKeys.currentSetNumber, state.currentSetIndex + 1)
                dataMap.putString(CompanionScoreKeys.matchStatus, state.companionStatusText(settings))
                dataMap.putLong(CompanionScoreKeys.updatedAt, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Tasks.await(dataClient.putDataItem(request))
        }
    }
}

internal fun MatchState.companionStatusText(settings: AppSettings): String = when {
    matchWinner != null -> "${settings.displayName(matchWinner)} won the match"
    currentSet.winner != null -> {
        val setWinner = requireNotNull(currentSet.winner)
        "${settings.displayName(setWinner)} won set ${currentSetIndex + 1}"
    }
    currentSet.firstServer == null -> "Waiting for first server"
    else -> "Set ${currentSetIndex + 1} in progress"
}
