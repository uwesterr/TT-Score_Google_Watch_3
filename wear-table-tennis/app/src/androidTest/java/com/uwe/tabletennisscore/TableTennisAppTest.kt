package com.uwe.tabletennisscore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TableTennisAppTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsWithServePrompt() {
        composeTestRule.onNodeWithTag("servePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serve first?").assertIsDisplayed()
    }

    @Test
    fun pointButtonsUpdateScore() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithContentDescription("Uwe score 1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Opponent score 0").assertIsDisplayed()
    }

    @Test
    fun serverIndicatorChangesAfterTwoPoints() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithText("Serving: Uwe").assertIsDisplayed()

        composeTestRule.onNodeWithTag("pointUwe").performClick()
        composeTestRule.onNodeWithTag("pointOpponent").performClick()

        composeTestRule.onNodeWithText("Serving: Opponent").assertIsDisplayed()
    }

    @Test
    fun newMatchDuringPlayReturnsToServePrompt() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("pointUwe").performClick()

        composeTestRule.onNodeWithTag("newMatchDuringPlay").performClick()

        composeTestRule.onNodeWithTag("servePrompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Serve first?").assertIsDisplayed()
    }

    @Test
    fun setAndMatchCompletionScreensAppear() {
        composeTestRule.onNodeWithTag("serveUwe").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }

        composeTestRule.onNodeWithTag("setComplete").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nextSet").performClick()
        composeTestRule.onNodeWithTag("serveOpponent").performClick()
        repeat(11) { composeTestRule.onNodeWithTag("pointUwe").performClick() }

        composeTestRule.onNodeWithTag("matchOver").assertIsDisplayed()
        composeTestRule.onNodeWithText("Uwe wins").assertIsDisplayed()
    }
}
