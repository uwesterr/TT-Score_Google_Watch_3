package com.uwe.tabletennisscore

import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeLeft
import androidx.wear.input.WearableButtons
import androidx.wear.input.testing.TestWearableButtonsProvider
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.BeforeClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TableTennisAppTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun useTestHardwareButtons() {
            WearableButtons.setWearableButtonsProvider(
                TestWearableButtonsProvider(
                    mapOf(
                        KeyEvent.KEYCODE_STEM_1 to
                            TestWearableButtonsProvider.TestWearableButtonLocation(200f, 100f),
                        KeyEvent.KEYCODE_STEM_2 to
                            TestWearableButtonsProvider.TestWearableButtonLocation(200f, 220f),
                    ),
                ),
            )
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() {
        runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext).save(AppSettings())
        }
        composeTestRule.runOnUiThread {
            composeTestRule.activity.resetAppForTests?.invoke()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun startsWithServePrompt() {
        composeTestRule.onNodeWithTag("servePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serve first?").assertIsDisplayed()
    }

    @Test
    fun hardwareScoringDefaultsToOff() {
        val settings = runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext).settings.first()
        }

        assertFalse(settings.hardwareScoringEnabled)
    }

    @Test
    fun settingsScreenOpensAndShowsDefaults() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()

        composeTestRule.onNodeWithTag("settingsScreen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settingsMeSpeech").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settingsOpponentSpeech").performScrollTo()
        composeTestRule.onNodeWithTag("settingsOpponentSpeech").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggleSounds").performScrollTo()
        composeTestRule.onNodeWithTag("toggleSounds").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggleHardwareScoring").performScrollTo()
        composeTestRule.onNodeWithTag("toggleHardwareScoring").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hardwareScoringExplanation").performScrollTo()
        composeTestRule.onNodeWithTag("hardwareScoringExplanation").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
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
        composeTestRule.onNodeWithContentDescription("Me score 0").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithContentDescription("Me score 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 0").assertIsDisplayed()
    }

    @Test
    fun savingHardwareScoringTogglePersistsSetting() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("toggleHardwareScoring").performScrollTo()
        composeTestRule.onNodeWithContentDescription("Hardware scoring OFF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggleHardwareScoring").performClick()
        composeTestRule.onNodeWithContentDescription("Hardware scoring ON").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        val settings = runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext).settings.first()
        }
        assertTrue(settings.hardwareScoringEnabled)
    }

    @Test
    fun stemOneScoresFirstPlayerWhenHardwareScoringIsEnabled() {
        runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext)
                .save(AppSettings(hardwareScoringEnabled = true))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        pressHardwareKey(KeyEvent.KEYCODE_STEM_1)

        composeTestRule.onNodeWithContentDescription("Me score 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 0").assertIsDisplayed()
    }

    @Test
    fun stemTwoScoresOpponentWhenHardwareScoringIsEnabled() {
        runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext)
                .save(AppSettings(hardwareScoringEnabled = true))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        pressHardwareKey(KeyEvent.KEYCODE_STEM_2)

        composeTestRule.onNodeWithContentDescription("Me score 0").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 1").assertIsDisplayed()
    }

    @Test
    fun hardwareButtonDoesNothingWhenHardwareScoringIsDisabled() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        pressHardwareKey(KeyEvent.KEYCODE_STEM_1)

        composeTestRule.onNodeWithContentDescription("Me score 0").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("bestOf5").performScrollTo()
        composeTestRule.onNodeWithTag("bestOf5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
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
        composeTestRule.onNodeWithTag("toggleKeepScreenOn").performScrollTo()
        composeTestRule.onNodeWithTag("toggleKeepScreenOn").performClick()
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
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
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
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
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
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
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("doublesServerH1").performClick()
        composeTestRule.onNodeWithTag("doublesReceiverA1").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithTag("openSettings").performClick()
        composeTestRule.onNodeWithTag("modeSingles").performClick()
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("servePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serve first?").assertIsDisplayed()
    }

    private fun pressHardwareKey(keyCode: Int) {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        composeTestRule.waitForIdle()
    }
}
