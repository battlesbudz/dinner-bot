package com.battlesbudz.dinnerbot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test: the app launches and reaches a real first screen. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun appLaunchesToOnboardingOrRecipes() {
        // Fresh install: the setup walkthrough. Returning install: the
        // recipe list. Either way the app must reach a real screen, not a
        // crash or a blank. Wait for the loading screen to resolve instead
        // of asserting on the first frame (the v0.1.1 flake: an immediate
        // check races the database load and sees only "Loading...").
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText("Welcome to Dinner Bot"),
            ).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(
                    androidx.compose.ui.test.hasText("Dinner Bot recipes"),
                ).fetchSemanticsNodes().isNotEmpty()
        }
        val onboarding = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText("Welcome to Dinner Bot"),
        ).fetchSemanticsNodes().isNotEmpty()
        if (onboarding) {
            composeRule.onNodeWithText("Welcome to Dinner Bot").assertIsDisplayed()
        }
    }
}
