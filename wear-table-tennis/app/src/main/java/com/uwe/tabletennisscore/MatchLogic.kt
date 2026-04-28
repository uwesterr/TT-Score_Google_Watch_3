package com.uwe.tabletennisscore

enum class Player(val code: String) {
    UWE("U"),
    OPPONENT("O");

    fun other(): Player = if (this == UWE) OPPONENT else UWE

    companion object {
        fun fromCode(code: String): Player? = entries.firstOrNull { it.code == code }
    }
}

enum class DoublesSeat(
    val code: String,
    val team: Player,
) {
    HOME_1("H1", Player.UWE),
    HOME_2("H2", Player.UWE),
    AWAY_1("A1", Player.OPPONENT),
    AWAY_2("A2", Player.OPPONENT);

    fun partner(): DoublesSeat = when (this) {
        HOME_1 -> HOME_2
        HOME_2 -> HOME_1
        AWAY_1 -> AWAY_2
        AWAY_2 -> AWAY_1
    }

    companion object {
        fun fromCode(code: String): DoublesSeat? = entries.firstOrNull { it.code == code }
    }
}

data class SetScore(
    val uwePoints: Int = 0,
    val opponentPoints: Int = 0,
    val firstServer: Player? = null,
    val doublesFirstServer: DoublesSeat? = null,
    val doublesFirstReceiver: DoublesSeat? = null,
    val doublesReceiverSwapTeam: Player? = null,
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

    fun isReady(mode: MatchMode): Boolean = when (mode) {
        MatchMode.SINGLES -> firstServer != null
        MatchMode.DOUBLES -> doublesFirstServer != null && doublesFirstReceiver != null
    }

    fun withFirstServer(player: Player): SetScore = copy(firstServer = player)

    fun withDoublesOpening(
        firstServer: DoublesSeat,
        firstReceiver: DoublesSeat,
    ): SetScore = copy(
        doublesFirstServer = firstServer,
        doublesFirstReceiver = firstReceiver,
        doublesReceiverSwapTeam = null,
    )

    fun withDoublesReceiverSwap(team: Player): SetScore = copy(doublesReceiverSwapTeam = team)

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
    val matchMode: MatchMode,
)

data class MatchState(
    val sets: List<SetScore> = listOf(SetScore()),
    val currentSetIndex: Int = 0,
    val matchWinner: Player? = null,
    val undoStack: List<MatchSnapshot> = emptyList(),
    val setsToWinMatch: Int = MatchFormat.BEST_OF_THREE.setsToWinMatch,
    val matchMode: MatchMode = MatchMode.SINGLES,
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
        matchMode = matchMode,
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
        MatchState(
            setsToWinMatch = settings.setsToWinMatch,
            matchMode = settings.matchMode,
        )

    fun chooseFirstServer(state: MatchState, player: Player): MatchState {
        if (state.matchMode != MatchMode.SINGLES) return state
        if (state.matchWinner != null || state.currentSet.firstServer != null) return state
        return state.copy(
            sets = state.sets.replaceAt(state.currentSetIndex, state.currentSet.withFirstServer(player)),
        )
    }

    fun chooseDoublesOpening(
        state: MatchState,
        firstServer: DoublesSeat,
        firstReceiver: DoublesSeat,
    ): MatchState {
        if (state.matchMode != MatchMode.DOUBLES) return state
        if (state.matchWinner != null || state.currentSet.winner != null || state.currentSet.isReady(MatchMode.DOUBLES)) {
            return state
        }
        if (firstServer.team == firstReceiver.team) return state

        val entitledServingTeam = entitledServingTeam(state)
        if (entitledServingTeam != null && firstServer.team != entitledServingTeam) return state

        val derivedReceiver = derivedFirstReceiverForCurrentSet(state, firstServer)
        if (derivedReceiver != null && derivedReceiver != firstReceiver) return state

        return state.copy(
            sets = state.sets.replaceAt(
                state.currentSetIndex,
                state.currentSet.withDoublesOpening(firstServer, firstReceiver),
            ),
        )
    }

    fun entitledServingTeam(state: MatchState): Player? {
        if (state.matchMode != MatchMode.DOUBLES || state.currentSetIndex == 0) return null
        return state.sets.getOrNull(state.currentSetIndex - 1)?.doublesFirstReceiver?.team
    }

    fun derivedFirstReceiverForCurrentSet(
        state: MatchState,
        firstServer: DoublesSeat,
    ): DoublesSeat? {
        if (state.matchMode != MatchMode.DOUBLES || state.currentSetIndex == 0) return null
        val previousSet = state.sets.getOrNull(state.currentSetIndex - 1) ?: return null
        return receiverServedBy(previousSet, firstServer)
    }

    fun addPoint(state: MatchState, winner: Player): MatchState {
        if (state.matchWinner != null) return state
        if (!state.currentSet.isReady(state.matchMode) || state.currentSet.winner != null) return state

        val updatedSet = applyDoublesReceiverSwapIfNeeded(
            state = state,
            updatedSet = state.currentSet.withPointFor(winner),
        )
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
        val nextSet = when (state.matchMode) {
            MatchMode.SINGLES -> SetScore(firstServer = state.currentSet.firstServer?.other())
            MatchMode.DOUBLES -> SetScore()
        }
        return state.copy(
            sets = state.sets + nextSet,
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
            matchMode = previous.matchMode,
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

    fun currentServer(state: MatchState): Player? = when (state.matchMode) {
        MatchMode.SINGLES -> currentServer(state.currentSet)
        MatchMode.DOUBLES -> currentDoublesServer(state.currentSet)?.team
    }

    fun currentDoublesServer(set: SetScore): DoublesSeat? {
        val cycle = effectiveDoublesCycle(set) ?: return null
        val turnIndex = doublesTurnIndex(set.totalPoints)
        return cycle[turnIndex % cycle.size]
    }

    fun currentDoublesReceiver(set: SetScore): DoublesSeat? {
        val cycle = effectiveDoublesCycle(set) ?: return null
        val turnIndex = doublesTurnIndex(set.totalPoints)
        return cycle[(turnIndex + 1) % cycle.size]
    }

    fun cueForTransition(
        previousState: MatchState,
        newState: MatchState,
        settings: AppSettings,
    ): MatchCue? {
        if (newState.currentSet.winner != null || newState.matchWinner != null) return null

        return when {
            shouldSwapDoublesReceivers(previousState, newState) ->
                MatchCue(MatchCueKind.CHANGE_ENDS, "Change ends + swap receivers")

            shouldChangeEnds(previousState, newState) ->
                MatchCue(MatchCueKind.CHANGE_ENDS, "Change ends")

            isMatchPoint(newState, Player.UWE) || isMatchPoint(newState, Player.OPPONENT) ->
                MatchCue(MatchCueKind.MATCH_POINT, "Match point")

            isSetPoint(newState, Player.UWE) || isSetPoint(newState, Player.OPPONENT) ->
                MatchCue(MatchCueKind.SET_POINT, "Set point")

            reachedDeuce(previousState.currentSet, newState.currentSet) ->
                MatchCue(MatchCueKind.DEUCE, "Deuce")

            serveChanged(previousState, newState) ->
                MatchCue(MatchCueKind.SERVE_CHANGE, serveChangeText(newState, settings) ?: return null)

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

    fun shouldSwapDoublesReceivers(previousState: MatchState, newState: MatchState): Boolean {
        if (newState.matchMode != MatchMode.DOUBLES) return false
        if (!shouldChangeEnds(previousState, newState)) return false
        return previousState.currentSet.doublesReceiverSwapTeam == null &&
            newState.currentSet.doublesReceiverSwapTeam != null
    }

    private fun isSetPoint(set: SetScore, player: Player): Boolean {
        if (set.winner != null) return false
        val playerPoints = set.pointsFor(player)
        val opponentPoints = set.pointsFor(player.other())
        return playerPoints >= POINTS_TO_WIN_SET - 1 && playerPoints - opponentPoints >= MINIMUM_LEAD - 1
    }

    private fun serveChanged(previousState: MatchState, newState: MatchState): Boolean = when (newState.matchMode) {
        MatchMode.SINGLES -> currentServer(previousState.currentSet) != currentServer(newState.currentSet)
        MatchMode.DOUBLES ->
            currentDoublesServer(previousState.currentSet) != currentDoublesServer(newState.currentSet) ||
                currentDoublesReceiver(previousState.currentSet) != currentDoublesReceiver(newState.currentSet)
    }

    private fun serveChangeText(
        state: MatchState,
        settings: AppSettings,
    ): String? = when (state.matchMode) {
        MatchMode.SINGLES -> {
            val server = currentServer(state.currentSet) ?: return null
            "${settings.displayName(server)} serves"
        }

        MatchMode.DOUBLES -> {
            val server = currentDoublesServer(state.currentSet) ?: return null
            val receiver = currentDoublesReceiver(state.currentSet) ?: return null
            "Serve ${settings.seatName(server)} -> ${settings.seatName(receiver)}"
        }
    }

    private fun applyDoublesReceiverSwapIfNeeded(
        state: MatchState,
        updatedSet: SetScore,
    ): SetScore {
        if (state.matchMode != MatchMode.DOUBLES) return updatedSet
        if (updatedSet.doublesReceiverSwapTeam != null) return updatedSet
        if (state.currentSetIndex != state.matchFormat.totalSets - 1) return updatedSet
        if (!reachedFirstFive(state.currentSet, updatedSet)) return updatedSet

        val swapTeam = currentDoublesReceiver(updatedSet)?.team ?: return updatedSet
        return updatedSet.withDoublesReceiverSwap(swapTeam)
    }

    private fun reachedFirstFive(
        previousSet: SetScore,
        newSet: SetScore,
    ): Boolean {
        val previousMax = maxOf(previousSet.uwePoints, previousSet.opponentPoints)
        return previousMax < CHANGE_ENDS_POINTS &&
            (newSet.uwePoints == CHANGE_ENDS_POINTS || newSet.opponentPoints == CHANGE_ENDS_POINTS)
    }

    private fun receiverServedBy(
        set: SetScore,
        server: DoublesSeat,
    ): DoublesSeat? {
        val cycle = effectiveDoublesCycle(set) ?: return null
        val index = cycle.indexOf(server)
        if (index == -1) return null
        return cycle[(index + 1) % cycle.size]
    }

    private fun effectiveDoublesCycle(set: SetScore): List<DoublesSeat>? {
        val firstServer = set.doublesFirstServer ?: return null
        val firstReceiver = set.doublesFirstReceiver ?: return null
        val base = mutableListOf(firstServer, firstReceiver, firstServer.partner(), firstReceiver.partner())
        val swapTeam = set.doublesReceiverSwapTeam ?: return base
        val swapIndexes = base.mapIndexedNotNull { index, seat ->
            index.takeIf { seat.team == swapTeam }
        }
        if (swapIndexes.size != 2) return base
        val firstIndex = swapIndexes[0]
        val secondIndex = swapIndexes[1]
        val swapped = base.toMutableList()
        val temp = swapped[firstIndex]
        swapped[firstIndex] = swapped[secondIndex]
        swapped[secondIndex] = temp
        return swapped
    }

    private fun doublesTurnIndex(totalPoints: Int): Int =
        if (totalPoints < 20) {
            totalPoints / 2
        } else {
            10 + (totalPoints - 20)
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
