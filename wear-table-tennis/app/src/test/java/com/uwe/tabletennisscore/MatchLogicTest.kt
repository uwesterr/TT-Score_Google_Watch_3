package com.uwe.tabletennisscore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLogicTest {
    @Test
    fun normalServeChangesEveryTwoPoints() {
        var state = MatchRules.chooseFirstServer(MatchState(), Player.UWE)

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
        var state = MatchRules.chooseFirstServer(MatchState(), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }
        assertNull(state.matchWinner)

        state = MatchRules.startNextSet(state)
        assertNull(state.currentSet.firstServer)
        state = MatchRules.chooseFirstServer(state, Player.OPPONENT)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }

        assertEquals(Player.UWE, state.matchWinner)
        assertEquals(2, state.uweSetsWon)
        assertEquals(0, state.opponentSetsWon)
    }

    @Test
    fun everyNewSetRequiresFirstServerChoice() {
        var state = MatchRules.chooseFirstServer(MatchState(), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.OPPONENT) }

        state = MatchRules.startNextSet(state)

        assertNull(state.currentSet.firstServer)
        val unchanged = MatchRules.addPoint(state, Player.UWE)
        assertEquals(0, unchanged.currentSet.uwePoints)
        assertEquals(0, unchanged.currentSet.opponentPoints)
    }

    @Test
    fun undoRestoresPointsServerSetAndMatchState() {
        var state = MatchRules.chooseFirstServer(MatchState(), Player.UWE)
        repeat(11) { state = MatchRules.addPoint(state, Player.UWE) }
        state = MatchRules.startNextSet(state)
        state = MatchRules.chooseFirstServer(state, Player.OPPONENT)
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
}
