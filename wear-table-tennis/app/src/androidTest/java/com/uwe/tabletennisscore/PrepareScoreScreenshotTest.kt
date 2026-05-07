package com.uwe.tabletennisscore

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class PrepareScoreScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun stageNineNineComebackScreen() {
        runBlocking {
            AppSettingsStore(composeTestRule.activity.applicationContext).save(
                AppSettings.sanitize(
                    meName = "Uwe",
                    opponentName = "Lutz",
                    setsToWinMatch = MatchFormat.BEST_OF_THREE.setsToWinMatch,
                    hapticsEnabled = true,
                    soundsEnabled = true,
                    keepScreenOn = true,
                ),
            )
        }

        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.recreate()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").performClick()

        repeat(5) { composeTestRule.onNodeWithTag("pointOpponent").performClick() }
        repeat(9) { composeTestRule.onNodeWithTag("pointUwe").performClick() }
        repeat(4) { composeTestRule.onNodeWithTag("pointOpponent").performClick() }

        composeTestRule.onNodeWithTag("scoreScreen").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        saveScreenshot("tt-score-nine-nine.png")
    }

    @Test
    fun stageDoublesScoreScreen() {
        composeTestRule.onNodeWithTag("openSettingsFromPrompt").performClick()
        composeTestRule.onNodeWithTag("modeDoubles").performClick()
        composeTestRule.onNodeWithTag("saveSettings").performScrollTo()
        composeTestRule.onNodeWithTag("saveSettings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("serveUwe").performClick()
        composeTestRule.onNodeWithTag("doublesServerH1").performClick()
        composeTestRule.onNodeWithTag("doublesReceiverA1").performClick()

        repeat(7) { composeTestRule.onNodeWithTag("pointUwe").performClick() }
        repeat(5) { composeTestRule.onNodeWithTag("pointOpponent").performClick() }

        composeTestRule.waitForIdle()
        saveScreenshot("tt-score-doubles.png")
    }

    private fun saveScreenshot(fileName: String) {
        val screenshot = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        val output = File(composeTestRule.activity.getExternalFilesDir(null), fileName)
        FileOutputStream(output).use { stream ->
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
