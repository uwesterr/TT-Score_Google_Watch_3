package com.uwe.tabletennisscore

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val COMPANION_SCORE_PATH = "/tt_score/current_state"

internal object CompanionScoreKeys {
    const val matchMode = "match_mode"
    const val meName = "me_name"
    const val opponentName = "opponent_name"
    const val mePoints = "me_points"
    const val opponentPoints = "opponent_points"
    const val currentServerName = "current_server_name"
    const val currentServerTeam = "current_server_team"
    const val currentReceiverName = "current_receiver_name"
    const val meSetsWon = "me_sets_won"
    const val opponentSetsWon = "opponent_sets_won"
    const val setsToWinMatch = "sets_to_win_match"
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
            val doublesServer = if (state.matchMode == MatchMode.DOUBLES) MatchRules.currentDoublesServer(state.currentSet) else null
            val doublesReceiver = if (state.matchMode == MatchMode.DOUBLES) MatchRules.currentDoublesReceiver(state.currentSet) else null
            val request = PutDataMapRequest.create(COMPANION_SCORE_PATH).apply {
                dataMap.putString(CompanionScoreKeys.matchMode, state.matchMode.name)
                dataMap.putString(CompanionScoreKeys.meName, settings.displayName(Player.UWE))
                dataMap.putString(CompanionScoreKeys.opponentName, settings.displayName(Player.OPPONENT))
                dataMap.putInt(CompanionScoreKeys.mePoints, state.currentSet.uwePoints)
                dataMap.putInt(CompanionScoreKeys.opponentPoints, state.currentSet.opponentPoints)
                dataMap.putString(
                    CompanionScoreKeys.currentServerName,
                    when (state.matchMode) {
                        MatchMode.SINGLES -> MatchRules.currentServer(state.currentSet)?.let(settings::displayName).orEmpty()
                        MatchMode.DOUBLES -> doublesServer?.let(settings::seatName).orEmpty()
                    },
                )
                dataMap.putString(
                    CompanionScoreKeys.currentServerTeam,
                    when (state.matchMode) {
                        MatchMode.SINGLES -> MatchRules.currentServer(state.currentSet)?.code.orEmpty()
                        MatchMode.DOUBLES -> doublesServer?.team?.code.orEmpty()
                    },
                )
                dataMap.putString(
                    CompanionScoreKeys.currentReceiverName,
                    doublesReceiver?.let(settings::seatName).orEmpty(),
                )
                dataMap.putInt(CompanionScoreKeys.meSetsWon, state.uweSetsWon)
                dataMap.putInt(CompanionScoreKeys.opponentSetsWon, state.opponentSetsWon)
                dataMap.putInt(CompanionScoreKeys.setsToWinMatch, state.setsToWinMatch)
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
    !currentSet.isReady(matchMode) -> when (matchMode) {
        MatchMode.SINGLES -> "Waiting for first server"
        MatchMode.DOUBLES -> "Waiting for doubles opening order"
    }
    else -> "Set ${currentSetIndex + 1} in progress"
}
