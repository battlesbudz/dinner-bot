package com.battlesbudz.dinnerbot

import android.content.Context
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With setup completed, every bottom-navigation destination must be
 * reachable and show its screen. Guards the v0.1.0 bug where the six
 * hand-rolled tab buttons crushed their labels letter by letter.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTabsInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

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
            openTab("tab-Plan", "This week's dinners")
            openTab("tab-List", "Shopping list")
            openTab("tab-Pantry", "Pantry")
            openTab("tab-Prices", "Price book")
            openTab("tab-Settings", "How Dinner Bot works")
            openTab("tab-Recipes", "Dinner Bot recipes")
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
