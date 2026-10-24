package com.battlesbudz.dinnerbot

import android.content.Context
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
 * Phase V2-6 flows, as reshaped by the v0.6.0 redesign: the Nutrition
 * tab is gone — the week's nutrition summary is a card on the Plan
 * screen and per-recipe figures live on each recipe's detail screen,
 * both still labeled as USDA estimates in plain words.
 */
@RunWith(AndroidJUnit4::class)
class NutritionInstrumentedTest {

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

    private fun waitForTextContaining(fragment: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(fragment, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun weekNutritionCardShowsOnPlan() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("This week's dinners")
            composeRule.onNodeWithText("Plan my week").performClick()
            // The week's nutrition card renders on Plan (v0.6.0: the old
            // Nutrition tab's weekly half), labeled as USDA estimates.
            waitForTag("week-nutrition-card")
            waitForText("Week nutrition (estimate)")
            waitForTextContaining("Estimates from USDA FoodData Central data")
            waitForTextContaining("kcal")
            assertTrue(
                composeRule.onAllNodesWithText("protein", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test fun recipeDetailShowsPerServingNutrition() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onAllNodesWithText("Bean and Cheese Burritos")
                .onFirst().performClick()
            waitForText("Nutrition per serving (estimate)")
            waitForTextContaining("Calories:")
        }
    }
}
