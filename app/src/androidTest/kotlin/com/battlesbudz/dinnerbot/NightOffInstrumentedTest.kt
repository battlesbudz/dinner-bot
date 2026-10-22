package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Phase V2-2 flow proof: with setup completed and a week planned, marking a
 * night not cooking (reason: leftovers) takes the night off the plan, and
 * "Cook this night" puts a dinner back.
 */
@RunWith(AndroidJUnit4::class)
class NightOffInstrumentedTest {

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

    @Test fun markNightOffWithReasonAndBackOn() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("This week's dinners")
            composeRule.onNodeWithText("Plan my week").performClick()
            // A generated week has per-night Swap buttons.
            waitForText("Swap")

            composeRule.onAllNodesWithText("Not cooking").onFirst()
                .performScrollTo().performClick()
            waitForText("Not cooking this night?")
            composeRule.onNodeWithText("Leftovers").performClick()

            waitForText("Not cooking: Leftovers")
            composeRule.onAllNodesWithText("Not cooking: Leftovers").onFirst().assertIsDisplayed()

            composeRule.onNodeWithText("Cook this night").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithText("Not cooking: Leftovers")
                    .fetchSemanticsNodes().isEmpty()
            }
        }
    }
}
