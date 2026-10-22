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
        // Fresh install: onboarding. Returning install: the recipe list.
        // Either way the app must reach a real screen, not a crash or a blank.
        val onboarding = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText("Welcome to Dinner Bot"),
        ).fetchSemanticsNodes().isNotEmpty()
        val recipes = composeRule.onAllNodes(
            androidx.compose.ui.test.hasText("Dinner Bot recipes"),
        ).fetchSemanticsNodes().isNotEmpty()
        assert(onboarding || recipes) { "App launched but showed neither onboarding nor recipes" }
        if (onboarding) {
            composeRule.onNodeWithText("Welcome to Dinner Bot").assertIsDisplayed()
        }
    }
}
