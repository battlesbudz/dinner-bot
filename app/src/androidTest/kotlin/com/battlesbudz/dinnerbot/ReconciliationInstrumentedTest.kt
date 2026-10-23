package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-7 flows on a device: the cook view's check-offs and ran-out
 * marks persist per planned dinner and survive leaving and reopening the
 * view; a swapped dinner's check-offs clear with it; and the Sunday
 * reconciliation opens before planning with the three lists in order,
 * the shopping list coming last.
 *
 * Setup follows the established pattern (NightOffInstrumentedTest): the
 * database is pre-seeded with onboarding complete, so the app opens on
 * the tabs. Cook-view checkboxes carry per-ingredient test tags
 * ("cook-used-<key>" / "cook-ranout-<key>"); the tests match them by
 * prefix so they do not depend on which dinners the planner picked.
 */
@RunWith(AndroidJUnit4::class)
class ReconciliationInstrumentedTest {

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

    private fun hasTagPrefix(prefix: String): SemanticsMatcher =
        SemanticsMatcher("testTag starts with $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    private fun waitForCookView() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasTagPrefix(USED_PREFIX)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openFirstCookView() {
        composeRule.onAllNodesWithText("Start cooking").onFirst()
            .performScrollTo().performClick()
        waitForCookView()
    }

    private fun planAWeek() {
        waitForText("Dinner Bot recipes")
        composeRule.onNodeWithTag("tab-Plan").performClick()
        waitForText("This week's dinners")
        composeRule.onNodeWithText("Plan my week").performClick()
        waitForText("Swap")
    }

    @Test
    fun cookViewPersistsCheckoffsAndRanOutMarks() {
        ActivityScenario.launch(MainActivity::class.java).use {
            planAWeek()
            openFirstCookView()
            val used = composeRule.onAllNodes(hasTagPrefix(USED_PREFIX))
            val ranOut = composeRule.onAllNodes(hasTagPrefix(RANOUT_PREFIX))
            val ingredientCount = used.fetchSemanticsNodes().size
            assertTrue(ingredientCount >= 1)
            // One ran-out toggle per ingredient, at the bottom of the view.
            assertEquals(ingredientCount, ranOut.fetchSemanticsNodes().size)

            used[0].performScrollTo().performClick()
            ranOut[0].performScrollTo().performClick()

            // Leave and reopen the same planned dinner: both marks persisted.
            composeRule.onNodeWithText("Back to the week").performScrollTo().performClick()
            waitForText("Swap")
            openFirstCookView()
            composeRule.onAllNodes(hasTagPrefix(USED_PREFIX))[0].assertIsOn()
            composeRule.onAllNodes(hasTagPrefix(RANOUT_PREFIX))[0].assertIsOn()
        }
    }

    @Test
    fun swappingADinnerClearsItsCookCheckoffs() {
        ActivityScenario.launch(MainActivity::class.java).use {
            planAWeek()
            openFirstCookView()
            composeRule.onAllNodes(hasTagPrefix(USED_PREFIX))[0]
                .performScrollTo().performClick()
            composeRule.onNodeWithText("Back to the week").performScrollTo().performClick()
            waitForText("Swap")

            // Swap that night's dinner, then open the replacement's cook
            // view: the old dinner's check-off must not carry over.
            composeRule.onAllNodesWithText("Swap").onFirst()
                .performScrollTo().performClick()
            waitForText("Swap")
            openFirstCookView()
            val reopened = composeRule.onAllNodes(hasTagPrefix(USED_PREFIX))
            val count = reopened.fetchSemanticsNodes().size
            assertTrue(count >= 1)
            for (i in 0 until count) reopened[i].assertIsOff()
        }
    }

    @Test
    fun sundayFlowRunsInOrderBeforePlanning() {
        ActivityScenario.launch(MainActivity::class.java).use {
            planAWeek()

            // Roll the stored week's start back so it counts as finished;
            // the in-memory state is stale, so the gate reads the finished
            // plan from the store when Regenerate is tapped.
            val dbFile = context.getDatabasePath("dinner-bot.db")
            val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
            )
            sqlite.execSQL("UPDATE plans SET weekStart = '2020-01-06'")
            sqlite.close()

            // Starting a new week opens reconciliation FIRST: the Sunday
            // check shows before any new planning happens. (The stored
            // plan was never cooked from, so lists 1 and 2 are empty and
            // the flow opens on the pantry list.)
            composeRule.onNodeWithText("Regenerate week").performScrollTo().performClick()
            waitForText("Sunday check")
            composeRule.onAllNodesWithTag("recon-pantry-list").fetchSemanticsNodes()
                .also { assertTrue(it.isNotEmpty()) }

            // Finish: the week plans only now, and the shopping list
            // exists only after the plan, last in the flow.
            composeRule.onNodeWithText("Finish and plan the week").performScrollTo().performClick()
            waitForText("Swap")
            composeRule.onNodeWithTag("tab-List").performClick()
            waitForText("This week's shopping")
        }
    }

    private companion object {
        const val USED_PREFIX = "cook-used-"
        const val RANOUT_PREFIX = "cook-ranout-"
    }
}
