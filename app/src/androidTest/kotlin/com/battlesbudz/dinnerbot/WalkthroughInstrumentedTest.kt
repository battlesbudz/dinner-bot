package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A fresh install (no saved settings) must open on the setup walkthrough. */
@RunWith(AndroidJUnit4::class)
class WalkthroughInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun freshLaunchShowsWalkthroughAndReachesSetup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Fresh-install state: no database file, so no completed setup exists.
        context.deleteDatabase("dinner-bot.db")
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Welcome to Dinner Bot")
            composeRule.onNodeWithText("Welcome to Dinner Bot").assertIsDisplayed()
            composeRule.onNodeWithText("Next").performClick()
            waitForText("One cash budget for the week")
            composeRule.onNodeWithText("One cash budget for the week").assertIsDisplayed()
            composeRule.onNodeWithText("Next").performClick()
            waitForText("Three stores, one list")
            composeRule.onNodeWithText("Set up my household").performClick()
            waitForText("How many are you feeding?")
            composeRule.onNodeWithText("How many are you feeding?").assertIsDisplayed()
        }
    }
}
