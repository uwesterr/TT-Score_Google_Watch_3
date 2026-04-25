package com.uwe.tabletennisscore

enum class Player(val label: String, val code: String) {
    UWE("Me", "U"),
    OPPONENT("Opponent", "O");

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
)

data class MatchState(
    val sets: List<SetScore> = listOf(SetScore()),
    val currentSetIndex: Int = 0,
    val matchWinner: Player? = null,
    val undoStack: List<MatchSnapshot> = emptyList(),
) {
    val currentSet: SetScore get() = sets[currentSetIndex]
    val uweSetsWon: Int get() = sets.count { it.winner == Player.UWE }
    val opponentSetsWon: Int get() = sets.count { it.winner == Player.OPPONENT }

    fun snapshot(): MatchSnapshot = MatchSnapshot(
        sets = sets,
        currentSetIndex = currentSetIndex,
        matchWinner = matchWinner,
    )
}

object MatchRules {
    const val POINTS_TO_WIN_SET = 11
    const val MINIMUM_LEAD = 2
    const val SETS_TO_WIN_MATCH = 2

    fun chooseFirstServer(state: MatchState, player: Player): MatchState {
        if (state.matchWinner != null || state.currentSet.firstServer != null) return state
        return state.copy(sets = state.sets.replaceAt(state.currentSetIndex, state.currentSet.withFirstServer(player)))
    }

    fun addPoint(state: MatchState, winner: Player): MatchState {
        if (state.matchWinner != null) return state
        if (state.currentSet.firstServer == null || state.currentSet.winner != null) return state

        val updatedSet = state.currentSet.withPointFor(winner)
        val updatedSets = state.sets.replaceAt(state.currentSetIndex, updatedSet)
        val nextMatchWinner = findMatchWinner(updatedSets)

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
        )
    }

    fun newMatch(): MatchState = MatchState()

    fun currentServer(set: SetScore): Player? {
        val firstServer = set.firstServer ?: return null
        return if (set.totalPoints < 20) {
            val serverBlock = (set.totalPoints / 2) % 2
            if (serverBlock == 0) firstServer else firstServer.other()
        } else {
            if (set.totalPoints % 2 == 0) firstServer else firstServer.other()
        }
    }

    private fun findMatchWinner(sets: List<SetScore>): Player? {
        val uweWins = sets.count { it.winner == Player.UWE }
        val opponentWins = sets.count { it.winner == Player.OPPONENT }
        return when {
            uweWins >= SETS_TO_WIN_MATCH -> Player.UWE
            opponentWins >= SETS_TO_WIN_MATCH -> Player.OPPONENT
            else -> null
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }
