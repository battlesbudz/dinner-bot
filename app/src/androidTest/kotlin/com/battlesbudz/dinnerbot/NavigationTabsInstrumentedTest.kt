package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With setup completed, every bottom-navigation destination must be
 * reachable and show its screen. Guards the v0.1.0 bug where the six
 * hand-rolled tab buttons crushed their labels letter by letter, and —
 * since the v0.6.0 redesign — Battles's five-tab decision: Recipes,
 * List, Plan in the middle, Pantry (with the price book behind its pill
 * toggle), Settings. Also guards the redesigned back behavior: the
 * system back button steps out of a sub-screen to the screen before it
 * instead of leaving the app.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTabsInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var firstRecipeTitle: String

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()
        try {
            val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
            SeedLoader(db).seedIfEmpty(priceJson, recipesJson)
            db.settingsDao().upsert(
                SettingsEntity(
                    id = 1,
                    householdSize = 4,
                    cookNightsJson = "[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\"]",
                    weeklyBudgetCents = 100_00,
                    wontEatJson = "[]",
                    dietNotes = "",
                    pantryStaplesJson = "[]",
                    onboardingComplete = true,
                ),
            )
            firstRecipeTitle = db.recipeDao().getAll().first().title
        } finally {
            db.close()
        }
    }

    @After fun tearDown() {
        context.deleteDatabase("dinner-bot.db")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTab(tag: String, heading: String) {
        composeRule.onNodeWithTag(tag).performClick()
        waitForText(heading)
        // onFirst: the selected tab's own label can duplicate a short
        // heading (the Pantry tab label vs the Pantry screen title).
        composeRule.onAllNodesWithText(heading).onFirst().assertIsDisplayed()
    }

    @Test fun everyTabIsReachableAndShowsItsScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            openTab("tab-List", "Shopping list")
            openTab("tab-Plan", "This week's dinners")
            openTab("tab-Pantry", "Pantry")
            // The price book folded into Pantry (v0.6.0): reachable and
            // readable behind the pill toggle, and back out again.
            composeRule.onNodeWithTag("pantry-toggle-prices").performClick()
            waitForText("Price book")
            composeRule.onAllNodesWithText("Price book").onFirst().assertIsDisplayed()
            composeRule.onNodeWithTag("pantry-toggle-onhand").performClick()
            waitForText("Pantry")
            openTab("tab-Settings", "How Dinner Bot works")
            openTab("tab-Recipes", "Dinner Bot recipes")
        }
    }

    @Test fun fiveTabsSitInOrderWithPlanInTheMiddle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            val order = listOf("tab-Recipes", "tab-List", "tab-Plan", "tab-Pantry", "tab-Settings")
            val lefts = order.map { tag ->
                composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left
            }
            assertTrue(
                "tabs must run Recipes, List, Plan, Pantry, Settings left to right: $lefts",
                lefts.zipWithNext().all { (left, next) -> left < next },
            )
            // Exactly five: no Prices or Nutrition tab remains.
            assertTrue(composeRule.onAllNodesWithTag("tab-Prices").fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithTag("tab-Nutrition").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun systemBackFromRecipeDetailReturnsToTheRecipeList() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithText(firstRecipeTitle).performClick()
            waitForText("Back to recipes")
            composeRule.onNodeWithText("Back to recipes").assertIsDisplayed()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            // Back stepped to the list instead of finishing the activity:
            // the list heading is up again (a finished activity would
            // leave nothing to find).
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithText("Dinner Bot recipes").assertIsDisplayed()
        }
    }

    @Test fun howItWorksReplayOpensFromSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            openTab("tab-Settings", "How Dinner Bot works")
            composeRule.onNodeWithText("How Dinner Bot works").performClick()
            waitForText("Welcome to Dinner Bot")
            composeRule.onNodeWithText("Welcome to Dinner Bot").assertIsDisplayed()
            composeRule.onNodeWithText("Done").performClick()
            waitForText("How Dinner Bot works")
        }
    }
}
