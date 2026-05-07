package com.uwe.tabletennisscore

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.wear.input.WearableButtons
import androidx.wear.input.testing.TestWearableButtonsProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class HardwareScoringFallbackTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun useNoReportedMultifunctionButtons() {
            WearableButtons.setWearableButtonsProvider(
                TestWearableButtonsProvider(
                    emptyMap<Int, TestWearableButtonsProvider.TestWearableButtonLocation>(),
                ),
            )
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableHardwareScoring() {
        runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext)
                .save(AppSettings(hardwareScoringEnabled = true))
        }
        composeTestRule.runOnUiThread {
            composeTestRule.activity.resetAppForTests?.invoke()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun primaryStemScoresFirstPlayerWhenNoMultifunctionButtonsAreReported() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        pressHardwareKey(KeyEvent.KEYCODE_STEM_PRIMARY)

        composeTestRule.waitUntil(timeoutMillis = 1_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Me score 1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Me score 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 0").assertIsDisplayed()
    }

    @Test
    fun primaryStemDoublePressScoresOpponentWhenNoMultifunctionButtonsAreReported() {
        val settings = runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext).settings.first()
        }
        assertTrue(settings.hardwareScoringEnabled)

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        pressHardwareKey(KeyEvent.KEYCODE_STEM_PRIMARY)
        pressHardwareKey(KeyEvent.KEYCODE_STEM_PRIMARY)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Me score 0").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 1").assertIsDisplayed()
    }

    private fun pressHardwareKey(keyCode: Int) {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        composeTestRule.waitForIdle()
    }
}
