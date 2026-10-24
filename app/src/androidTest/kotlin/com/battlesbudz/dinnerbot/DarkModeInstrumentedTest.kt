package com.battlesbudz.dinnerbot

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Dark mode (v0.6.1) follows the phone: with the app's night mode
 * forced on, the key screens must still compose and read correctly —
 * every tab, the price book behind Pantry's pill toggle, and a recipe
 * detail. The scheme selection itself is pinned by DarkThemeProbeTest;
 * this class guards that nothing in the real screens breaks when the
 * dark scheme is the one in force.
 */
@RunWith(AndroidJUnit4::class)
class DarkModeInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var firstRecipeTitle: String

    private fun setNightMode(on: Boolean) {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        uiModeManager.setApplicationNightMode(
            if (on) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
        )
    }

    private fun isNight(): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

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
        setNightMode(true)
        // The walk below only proves anything if night mode truly took
        // effect in this process; wait for the configuration to flip
        // instead of walking the light app and calling it dark.
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!isNight() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
        assertTrue("night mode never took effect", isNight())
    }

    @After fun tearDown() {
        setNightMode(false)
        // Wait for the flip back to settle before the next test class
        // launches anything: a configuration change landing mid-test
        // in a later class would recreate its activity under it.
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (isNight() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
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
        composeRule.onAllNodesWithText(heading).onFirst().assertIsDisplayed()
    }

    @Test fun keyScreensComposeUnderNightMode() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithText("Dinner Bot recipes").assertIsDisplayed()
            openTab("tab-List", "Shopping list")
            openTab("tab-Plan", "This week's dinners")
            openTab("tab-Pantry", "Pantry")
            composeRule.onNodeWithTag("pantry-toggle-prices").performClick()
            waitForText("Price book")
            composeRule.onAllNodesWithText("Price book").onFirst().assertIsDisplayed()
            composeRule.onNodeWithTag("pantry-toggle-onhand").performClick()
            waitForText("Pantry")
            openTab("tab-Settings", "How Dinner Bot works")
            // A recipe detail composes in the dark scheme too.
            openTab("tab-Recipes", "Dinner Bot recipes")
            composeRule.onNodeWithText(firstRecipeTitle).performClick()
            waitForText("Back to recipes")
            composeRule.onNodeWithText("Back to recipes").assertIsDisplayed()
        }
    }
}
