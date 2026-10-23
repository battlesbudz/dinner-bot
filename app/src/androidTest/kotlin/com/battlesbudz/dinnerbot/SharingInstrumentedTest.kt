package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Epic issue #9 flow proof: with setup completed, the Settings tab shows
 * the backup-and-sharing section with a Share backup entry next to
 * Export, and tapping Import backup first explains, in plain words,
 * that importing replaces the data on this phone — before any file
 * picker opens. Cancelling leaves the app exactly as it was.
 */
@RunWith(AndroidJUnit4::class)
class SharingInstrumentedTest {

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

    @Test fun shareEntrySitsNextToExportAndImportWarnsItReplaces() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Settings").performClick()
            waitForText("Backup and sharing")
            composeRule.onNodeWithText("Backup and sharing").assertIsDisplayed()
            composeRule.onNodeWithText("Export backup").assertIsDisplayed()
            composeRule.onNodeWithText("Share backup").assertIsDisplayed()

            composeRule.onNodeWithText("Import backup").performClick()
            waitForText("Import a backup?")
            composeRule.onNodeWithText("Import a backup?").assertIsDisplayed()
            composeRule.onNodeWithText("replaces everything", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("not a live", substring = true).assertIsDisplayed()

            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithText("Import a backup?").fetchSemanticsNodes().isEmpty()
            }
            // The section is still there and nothing was imported.
            composeRule.onNodeWithText("Backup and sharing").assertIsDisplayed()
        }
    }
}
