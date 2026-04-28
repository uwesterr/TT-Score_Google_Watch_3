package com.uwe.tabletennisscore

import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TableTennisAppTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() {
        runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext).save(AppSettings())
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun startsWithServePrompt() {
        composeTestRule.onNodeWithTag("servePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serve first?").assertIsDisplayed()
    }

    @Test
    fun settingsScreenOpensAndShowsDefaults() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()

        composeTestRule.onNodeWithTag("settingsScreen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settingsMeSpeech").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settingsOpponentSpeech").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("toggleSounds").assertIsDisplayed()
        assertTrue(
            composeTestRule.activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
        )
    }

    @Test
    fun savingCustomNamesUpdatesMatchUi() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("settingsMeName").performTextClearance()
        composeTestRule.onNodeWithTag("settingsMeName").performTextInput("Alex")
        composeTestRule.onNodeWithTag("settingsOpponentName").performTextClearance()
        composeTestRule.onNodeWithTag("settingsOpponentName").performTextInput("Lutz")
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alex").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lutz").assertIsDisplayed()

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithText("Serving: Alex").assertIsDisplayed()
    }

    @Test
    fun pointButtonsUpdateScore() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithContentDescription("Me score 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 0").assertIsDisplayed()
    }

    @Test
    fun swipeLeftShowsPointsHistoryScreen() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("scoreScreen").performTouchInput { swipeLeft() }

        composeTestRule.onNodeWithTag("historyChartScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("historyChart").assertIsDisplayed()
    }

    @Test
    fun bestOfFiveCanBeSelectedBeforeServe() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("bestOf5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithText("Serving: Me").assertIsDisplayed()
    }

    @Test
    fun cueBannerAppearsForServeChangeAndDeuce() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()
        composeTestRule.onNodeWithTag("pointOpponent").performClick()
        composeTestRule.onNodeWithText("Opponent serves").assertIsDisplayed()

        repeat(8) {
            composeTestRule.onNodeWithTag("pointUwe").performClick()
            composeTestRule.onNodeWithTag("pointOpponent").performClick()
        }
        composeTestRule.onNodeWithTag("pointUwe").performClick()
        composeTestRule.onNodeWithTag("pointOpponent").performClick()

        composeTestRule.onNodeWithText("Deuce").assertIsDisplayed()
    }

    @Test
    fun nextSetStartsImmediatelyWithOppositeStartingServer() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }

        composeTestRule.onNodeWithTag("nextSet").performClick()

        composeTestRule.onNodeWithTag("scoreScreen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("servePrompt").assertCountEquals(0)
        composeTestRule.onNodeWithText("Serving: Opponent").assertIsDisplayed()
    }

    @Test
    fun firstPlayerWinShowsCelebrationAnimation() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }

        composeTestRule.onNodeWithTag("setComplete").assertIsDisplayed()
        composeTestRule.onNodeWithTag("winnerConfetti").assertIsDisplayed()
    }

    @Test
    fun swipeLeftOnSetCompleteShowsLastSetHistory() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }

        composeTestRule.onNodeWithTag("setComplete").performTouchInput { swipeLeft() }

        composeTestRule.onNodeWithTag("completedSetHistoryScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("completedSetHistoryChart").assertIsDisplayed()
    }

    @Test
    fun opponentWinDoesNotShowCelebrationAnimation() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointOpponent").performClick() }

        composeTestRule.onNodeWithTag("setComplete").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("winnerConfetti").assertCountEquals(0)
    }

    @Test
    fun swipeLeftOnMatchOverShowsFinalSetHistory() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }
        composeTestRule.onNodeWithTag("nextSet").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }

        composeTestRule.onNodeWithTag("matchOver").performTouchInput { swipeLeft() }

        composeTestRule.onNodeWithTag("finalSetHistoryScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finalSetHistoryChart").assertIsDisplayed()
    }

    @Test
    fun turningAlwaysOnOffClearsWindowFlag() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("toggleKeepScreenOn").performClick()
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            composeTestRule.activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0,
        )
    }

    @Test
    fun doublesModeUsesDedicatedOpeningOrderPrompt() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("modeDoubles").performClick()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("doublesServePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Who serves first?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("doublesServerH1").performClick()
        composeTestRule.onNodeWithTag("doublesReceiverA1").performClick()

        composeTestRule.onNodeWithText("Server: Me\nReceiver: Opponent").assertIsDisplayed()
    }

    @Test
    fun switchingSinglesMatchToDoublesStartsFreshMatch() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithTag("openSettings").performClick()
        composeTestRule.onNodeWithTag("modeDoubles").performClick()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("doublesServePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Who serves first?").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("servePrompt").assertCountEquals(0)
    }

    @Test
    fun switchingDoublesMatchBackToSinglesStartsFreshMatch() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("modeDoubles").performClick()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("doublesServerH1").performClick()
        composeTestRule.onNodeWithTag("doublesReceiverA1").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithTag("openSettings").performClick()
        composeTestRule.onNodeWithTag("modeSingles").performClick()
        composeTestRule.onNodeWithTag("settingsScreen").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("servePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serve first?").assertIsDisplayed()
    }
}
