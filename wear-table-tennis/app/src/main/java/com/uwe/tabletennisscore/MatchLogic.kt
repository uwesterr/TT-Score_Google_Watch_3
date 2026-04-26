package com.uwe.tabletennisscore

enum class Player(val code: String) {
    UWE("U"),
    OPPONENT("O");

    fun other(): Player = if (this == UWE) OPPONENT else UWE

    companion object {
        fun fromCode(code: String): Player? = entries.firstOrNull { it.code == code }
    }
}

data class SetScore(
    val uwePoints: Int = 0,
    val opponentPoints: Int = 0,
    val firstServer: Player? = null,
) {
    val totalPoints: Int get() = uwePoints + opponentPoints
    val winner: Player?
        get() = when {
            uwePoints >= MatchRules.POINTS_TO_WIN_SET &&
                uwePoints - opponentPoints >= MatchRules.MINIMUM_LEAD -> Player.UWE
            opponentPoints >= MatchRules.POINTS_TO_WIN_SET &&
                opponentPoints - uwePoints >= MatchRules.MINIMUM_LEAD -> Player.OPPONENT
            else -> null
        }

    fun pointsFor(player: Player): Int = when (player) {
        Player.UWE -> uwePoints
        Player.OPPONENT -> opponentPoints
    }

    fun withFirstServer(player: Player): SetScore = copy(firstServer = player)

    fun withPointFor(player: Player): SetScore = when (player) {
        Player.UWE -> copy(uwePoints = uwePoints + 1)
        Player.OPPONENT -> copy(opponentPoints = opponentPoints + 1)
    }
}

data class MatchSnapshot(
    val sets: List<SetScore>,
    val currentSetIndex: Int,
    val matchWinner: Player?,
    val setsToWinMatch: Int,
)

data class MatchState(
    val sets: List<SetScore> = listOf(SetScore()),
    val currentSetIndex: Int = 0,
    val matchWinner: Player? = null,
    val undoStack: List<MatchSnapshot> = emptyList(),
    val setsToWinMatch: Int = MatchFormat.BEST_OF_THREE.setsToWinMatch,
) {
    val currentSet: SetScore get() = sets[currentSetIndex]
    val uweSetsWon: Int get() = sets.count { it.winner == Player.UWE }
    val opponentSetsWon: Int get() = sets.count { it.winner == Player.OPPONENT }
    val matchFormat: MatchFormat get() = MatchFormat.fromSetsToWin(setsToWinMatch)

    fun snapshot(): MatchSnapshot = MatchSnapshot(
        sets = sets,
        currentSetIndex = currentSetIndex,
        matchWinner = matchWinner,
        setsToWinMatch = setsToWinMatch,
    )
}

enum class MatchCueKind {
    SERVE_CHANGE,
    DEUCE,
    SET_POINT,
    MATCH_POINT,
    CHANGE_ENDS,
}

data class MatchCue(
    val kind: MatchCueKind,
    val text: String,
)

object MatchRules {
    const val POINTS_TO_WIN_SET = 11
    const val MINIMUM_LEAD = 2
    const val CHANGE_ENDS_POINTS = 5

    fun newMatch(settings: AppSettings = AppSettings()): MatchState =
        MatchState(setsToWinMatch = settings.setsToWinMatch)

    fun chooseFirstServer(state: MatchState, player: Player): MatchState {
        if (state.matchWinner != null || state.currentSet.firstServer != null) return state
        return state.copy(
            sets = state.sets.replaceAt(state.currentSetIndex, state.currentSet.withFirstServer(player)),
        )
    }

    fun addPoint(state: MatchState, winner: Player): MatchState {
        if (state.matchWinner != null) return state
        if (state.currentSet.firstServer == null || state.currentSet.winner != null) return state

        val updatedSet = state.currentSet.withPointFor(winner)
        val updatedSets = state.sets.replaceAt(state.currentSetIndex, updatedSet)
        val nextMatchWinner = findMatchWinner(updatedSets, state.setsToWinMatch)

        return state.copy(
            sets = updatedSets,
            matchWinner = nextMatchWinner,
            undoStack = state.undoStack + state.snapshot(),
        )
    }

    fun startNextSet(state: MatchState): MatchState {
        if (state.matchWinner != null || state.currentSet.winner == null) return state
        val nextFirstServer = state.currentSet.firstServer?.other()
        return state.copy(
            sets = state.sets + SetScore(firstServer = nextFirstServer),
            currentSetIndex = state.currentSetIndex + 1,
        )
    }

    fun undoLastPoint(state: MatchState): MatchState {
        val previous = state.undoStack.lastOrNull() ?: return state
        return MatchState(
            sets = previous.sets,
            currentSetIndex = previous.currentSetIndex,
            matchWinner = previous.matchWinner,
            undoStack = state.undoStack.dropLast(1),
            setsToWinMatch = previous.setsToWinMatch,
        )
    }

    fun currentServer(set: SetScore): Player? {
        val firstServer = set.firstServer ?: return null
        return if (set.totalPoints < 20) {
            val serverBlock = (set.totalPoints / 2) % 2
            if (serverBlock == 0) firstServer else firstServer.other()
        } else {
            if (set.totalPoints % 2 == 0) firstServer else firstServer.other()
        }
    }

    fun cueForTransition(
        previousState: MatchState,
        newState: MatchState,
        settings: AppSettings,
    ): MatchCue? {
        if (newState.currentSet.winner != null || newState.matchWinner != null) return null

        return when {
            shouldChangeEnds(previousState, newState) ->
                MatchCue(MatchCueKind.CHANGE_ENDS, "Change ends")

            isMatchPoint(newState, Player.UWE) || isMatchPoint(newState, Player.OPPONENT) ->
                MatchCue(MatchCueKind.MATCH_POINT, "Match point")

            isSetPoint(newState, Player.UWE) || isSetPoint(newState, Player.OPPONENT) ->
                MatchCue(MatchCueKind.SET_POINT, "Set point")

            reachedDeuce(previousState.currentSet, newState.currentSet) ->
                MatchCue(MatchCueKind.DEUCE, "Deuce")

            currentServer(previousState.currentSet) != currentServer(newState.currentSet) ->
                MatchCue(
                    MatchCueKind.SERVE_CHANGE,
                    "${settings.displayName(currentServer(newState.currentSet) ?: return null)} serves",
                )

            else -> null
        }
    }

    fun isSetPoint(state: MatchState, player: Player): Boolean = isSetPoint(state.currentSet, player)

    fun isMatchPoint(state: MatchState, player: Player): Boolean {
        if (!isSetPoint(state.currentSet, player)) return false
        val setsWon = if (player == Player.UWE) state.uweSetsWon else state.opponentSetsWon
        return setsWon == state.setsToWinMatch - 1
    }

    fun reachedDeuce(previousSet: SetScore, newSet: SetScore): Boolean =
        (previousSet.uwePoints != 10 || previousSet.opponentPoints != 10) &&
            newSet.uwePoints == 10 && newSet.opponentPoints == 10

    fun shouldChangeEnds(previousState: MatchState, newState: MatchState): Boolean {
        if (newState.currentSetIndex != newState.matchFormat.totalSets - 1) return false
        if (previousState.currentSetIndex != newState.currentSetIndex) return false
        return newState.currentSet.uwePoints == CHANGE_ENDS_POINTS ||
            newState.currentSet.opponentPoints == CHANGE_ENDS_POINTS
    }

    private fun isSetPoint(set: SetScore, player: Player): Boolean {
        if (set.winner != null) return false
        val playerPoints = set.pointsFor(player)
        val opponentPoints = set.pointsFor(player.other())
        return playerPoints >= POINTS_TO_WIN_SET - 1 && playerPoints - opponentPoints >= MINIMUM_LEAD - 1
    }

    private fun findMatchWinner(sets: List<SetScore>, setsToWinMatch: Int): Player? {
        val uweWins = sets.count { it.winner == Player.UWE }
        val opponentWins = sets.count { it.winner == Player.OPPONENT }
        return when {
            uweWins >= setsToWinMatch -> Player.UWE
            opponentWins >= setsToWinMatch -> Player.OPPONENT
            else -> null
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }
