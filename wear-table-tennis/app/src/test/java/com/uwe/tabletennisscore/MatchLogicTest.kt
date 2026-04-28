package com.uwe.tabletennisscore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLogicTest {
    @Test
    fun normalServeChangesEveryTwoPoints() {
        var state = MatchRules.chooseFirstServer(MatchRules.newMatch(), Player.UWE)

        assertEquals(Player.UWE, MatchRules.currentServer(state.currentSet))
        state = MatchRules.addPoint(state, Player.UWE)
        assertEquals(Player.UWE, MatchRules.currentServer(state.currentSet))
        state = MatchRules.addPoint(state, Player.OPPONENT)
        assertEquals(Player.OPPONENT, MatchRules.currentServer(state.currentSet))
        state = MatchRules.addPoint(state, Player.UWE)
        assertEquals(Player.OPPONENT, MatchRules.currentServer(state.currentSet))
        state = MatchRules.addPoint(state, Player.OPPONENT)
        assertEquals(Player.UWE, MatchRules.currentServer(state.currentSet))
    }

    @Test
    fun deuceServeChangesEveryPointFromTenAll() {
        var state = MatchState(
            sets = listOf(SetScore(uwePoints = 10, opponentPoints = 10, firstServer = Player.UWE)),
        )

        assertEquals(Player.UWE, MatchRules.currentServer(state.currentSet))
        state = MatchRules.addPoint(state, Player.UWE)
        assertEquals(Player.OPPONENT, MatchRules.currentServer(state.currentSet))
        state = MatchRules.addPoint(state, Player.OPPONENT)
        assertEquals(Player.UWE, MatchRules.currentServer(state.currentSet))
    }

    @Test
    fun setEndsOnlyAtElevenWithTwoPointLead() {
        var state = MatchState(
            sets = listOf(SetScore(uwePoints = 10, opponentPoints = 10, firstServer = Player.UWE)),
        )

        state = MatchRules.addPoint(state, Player.UWE)
        assertNull(state.currentSet.winner)

        state = MatchRules.addPoint(state, Player.UWE)
        assertEquals(Player.UWE, state.currentSet.winner)
        assertNull(state.matchWinner)
    }

    @Test
    fun matchEndsAtTwoSetsInBestOfThree() {
        var state = MatchRules.chooseFirstServer(MatchRules.newMatch(), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }
        assertNull(state.matchWinner)

        state = MatchRules.startNextSet(state)
        assertEquals(Player.OPPONENT, state.currentSet.firstServer)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }

        assertEquals(Player.UWE, state.matchWinner)
        assertEquals(2, state.uweSetsWon)
        assertEquals(0, state.opponentSetsWon)
    }

    @Test
    fun matchEndsAtThreeSetsInBestOfFive() {
        val settings = AppSettings(setsToWinMatch = MatchFormat.BEST_OF_FIVE.setsToWinMatch)
        var state = MatchRules.chooseFirstServer(MatchRules.newMatch(settings), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }
        state = MatchRules.startNextSet(state)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }

        assertNull(state.matchWinner)

        state = MatchRules.startNextSet(state)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }

        assertEquals(Player.UWE, state.matchWinner)
        assertEquals(3, state.uweSetsWon)
    }

    @Test
    fun nextSetAutomaticallyAlternatesStartingServer() {
        var state = MatchRules.chooseFirstServer(MatchRules.newMatch(), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.OPPONENT) }

        state = MatchRules.startNextSet(state)

        assertEquals(Player.OPPONENT, state.currentSet.firstServer)
        assertEquals(Player.OPPONENT, MatchRules.currentServer(state.currentSet))
    }

    @Test
    fun undoRestoresPointsServerSetAndMatchState() {
        var state = MatchRules.chooseFirstServer(MatchRules.newMatch(), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }
        state = MatchRules.startNextSet(state)
        repeat(10) { state = MatchRules.addPoint(state, Player.UWE) }
        state = MatchRules.addPoint(state, Player.UWE)

        assertEquals(Player.UWE, state.matchWinner)
        assertEquals(2, state.uweSetsWon)

        state = MatchRules.undoLastPoint(state)

        assertNull(state.matchWinner)
        assertEquals(1, state.uweSetsWon)
        assertEquals(10, state.currentSet.uwePoints)
        assertEquals(0, state.currentSet.opponentPoints)
        assertEquals(Player.UWE, MatchRules.currentServer(state.currentSet))
        assertTrue(state.undoStack.isNotEmpty())
    }

    @Test
    fun deuceCueTriggersAtFirstTenAll() {
        val previous = MatchState(sets = listOf(SetScore(uwePoints = 9, opponentPoints = 10, firstServer = Player.UWE)))
        val next = MatchState(sets = listOf(SetScore(uwePoints = 10, opponentPoints = 10, firstServer = Player.UWE)))

        val cue = MatchRules.cueForTransition(previous, next, AppSettings())

        assertEquals(MatchCueKind.DEUCE, cue?.kind)
    }

    @Test
    fun setPointDetectedForEitherPlayer() {
        val state = MatchState(sets = listOf(SetScore(uwePoints = 10, opponentPoints = 9, firstServer = Player.UWE)))

        assertTrue(MatchRules.isSetPoint(state, Player.UWE))
        assertFalse(MatchRules.isSetPoint(state, Player.OPPONENT))
    }

    @Test
    fun matchPointDetectedForEitherPlayer() {
        val state = MatchState(
            sets = listOf(
                SetScore(uwePoints = 11, opponentPoints = 8, firstServer = Player.UWE),
                SetScore(uwePoints = 10, opponentPoints = 9, firstServer = Player.OPPONENT),
            ),
            currentSetIndex = 1,
            setsToWinMatch = MatchFormat.BEST_OF_THREE.setsToWinMatch,
        )

        assertTrue(MatchRules.isMatchPoint(state, Player.UWE))
        assertFalse(MatchRules.isMatchPoint(state, Player.OPPONENT))
    }

    @Test
    fun decidingSetChangeEndsTriggersAtFirstReachOfFive() {
        val previous = MatchState(
            sets = listOf(
                SetScore(11, 7, Player.UWE),
                SetScore(8, 11, Player.OPPONENT),
                SetScore(4, 3, Player.UWE),
            ),
            currentSetIndex = 2,
            setsToWinMatch = MatchFormat.BEST_OF_THREE.setsToWinMatch,
        )
        val next = previous.copy(
            sets = listOf(
                previous.sets[0],
                previous.sets[1],
                SetScore(5, 3, Player.UWE),
            ),
        )

        assertTrue(MatchRules.shouldChangeEnds(previous, next))
    }

    @Test
    fun blankNamesFallBackToDefaults() {
        val settings = AppSettings.sanitize(
            meName = "   ",
            opponentName = "",
            setsToWinMatch = 99,
            hapticsEnabled = true,
            soundsEnabled = true,
            keepScreenOn = true,
        )

        assertEquals("Me", settings.meName)
        assertEquals("Opponent", settings.opponentName)
        assertEquals(MatchFormat.BEST_OF_THREE.setsToWinMatch, settings.setsToWinMatch)
    }

    @Test
    fun soundsDefaultToEnabled() {
        assertTrue(AppSettings().soundsEnabled)
    }

    @Test
    fun doublesServeCycleFollowsIttfOrder() {
        var state = MatchRules.newMatch(AppSettings(matchMode = MatchMode.DOUBLES))
        state = MatchRules.chooseDoublesOpening(state, DoublesSeat.HOME_1, DoublesSeat.AWAY_1)

        assertEquals(DoublesSeat.HOME_1, MatchRules.currentDoublesServer(state.currentSet))
        assertEquals(DoublesSeat.AWAY_1, MatchRules.currentDoublesReceiver(state.currentSet))

        repeat(2) { state = MatchRules.addPoint(state, Player.UWE) }
        assertEquals(DoublesSeat.AWAY_1, MatchRules.currentDoublesServer(state.currentSet))
        assertEquals(DoublesSeat.HOME_2, MatchRules.currentDoublesReceiver(state.currentSet))

        repeat(2) { state = MatchRules.addPoint(state, Player.OPPONENT) }
        assertEquals(DoublesSeat.HOME_2, MatchRules.currentDoublesServer(state.currentSet))
        assertEquals(DoublesSeat.AWAY_2, MatchRules.currentDoublesReceiver(state.currentSet))

        repeat(2) { state = MatchRules.addPoint(state, Player.UWE) }
        assertEquals(DoublesSeat.AWAY_2, MatchRules.currentDoublesServer(state.currentSet))
        assertEquals(DoublesSeat.HOME_1, MatchRules.currentDoublesReceiver(state.currentSet))
    }

    @Test
    fun doublesNextSetServingTeamAlternatesAndFirstReceiverIsDerived() {
        var state = MatchRules.newMatch(AppSettings(matchMode = MatchMode.DOUBLES))
        state = MatchRules.chooseDoublesOpening(state, DoublesSeat.HOME_1, DoublesSeat.AWAY_1)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }

        state = MatchRules.startNextSet(state)

        assertEquals(Player.OPPONENT, MatchRules.entitledServingTeam(state))
        assertEquals(
            DoublesSeat.HOME_1,
            MatchRules.derivedFirstReceiverForCurrentSet(state, DoublesSeat.AWAY_2),
        )
        assertEquals(
            DoublesSeat.HOME_2,
            MatchRules.derivedFirstReceiverForCurrentSet(state, DoublesSeat.AWAY_1),
        )
    }

    @Test
    fun doublesDecidingSetSwapsReceivingOrderAtFive() {
        var state = MatchState(
            sets = listOf(
                SetScore(11, 8, doublesFirstServer = DoublesSeat.HOME_1, doublesFirstReceiver = DoublesSeat.AWAY_1),
                SetScore(8, 11, doublesFirstServer = DoublesSeat.AWAY_1, doublesFirstReceiver = DoublesSeat.HOME_2),
                SetScore(4, 3, doublesFirstServer = DoublesSeat.HOME_1, doublesFirstReceiver = DoublesSeat.AWAY_1),
            ),
            currentSetIndex = 2,
            setsToWinMatch = MatchFormat.BEST_OF_THREE.setsToWinMatch,
            matchMode = MatchMode.DOUBLES,
        )

        state = MatchRules.addPoint(state, Player.UWE)

        assertEquals(Player.OPPONENT, state.currentSet.doublesReceiverSwapTeam)
        assertEquals(DoublesSeat.HOME_1, MatchRules.currentDoublesServer(state.currentSet))
        assertEquals(DoublesSeat.AWAY_2, MatchRules.currentDoublesReceiver(state.currentSet))
    }
}
