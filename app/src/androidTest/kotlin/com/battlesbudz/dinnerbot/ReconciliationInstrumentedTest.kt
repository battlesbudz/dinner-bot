package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
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
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.RanOutEventEntity
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-7 flow proofs, end to end on the real app:
 * 1. With a finished week and a ran-out mark waiting, starting a new week
 *    opens the Sunday reconciliation FIRST, walks its three lists in
 *    Battles's order, and only then plans; the mark lands confirmed and
 *    the stored plan is the new week.
 * 2. The cook view's check-offs and ran-out marks persist per planned
 *    dinner across leaving and reopening the view, and land in the
 *    database the Sunday flow consumes.
 */
@RunWith(AndroidJUnit4::class)
class ReconciliationInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    private fun currentMonday(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
    }

    @After fun tearDown() {
        context.deleteDatabase("dinner-bot.db")
    }

    private fun openDb(): DinnerBotDatabase =
        Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()

    /** Seed the book and a completed setup, the way NightOff's test does. */
    private fun seedAppData() = runBlocking {
        val db = openDb()
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

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun sundayFlowRunsInOrderBeforePlanning() = runBlocking {
        seedAppData()
        val lastWeekStart = currentMonday().minusWeeks(1)
        val db = openDb()
        try {
            val recipeId = db.recipeDao().getAll().first().id
            val oldPlan = PlanMapper.StoredPlan(
                id = "plan-old",
                weekStart = lastWeekStart.toString(),
                budgetCents = 100_00,
                cashNeededCents = 500,
                foodCostCents = 500,
                samsMonthlySavingsCents = 0,
                nights = listOf(PlannedNight("MON", recipeId)),
                notes = emptyList(),
                createdAtMillis = 1L,
            )
            db.planDao().insert(with(PlanMapper) { oldPlan.toEntity() })
            db.ranOutDao().upsert(
                RanOutEventEntity(
                    itemKey = "pantry:olive oil", displayName = "Olive oil", stapleSide = true,
                    priceItemId = null, bulkItemId = null, pantryStapleName = "Olive oil",
                    dateMarked = LocalDate.now().toString(), source = "COOKING",
                    state = "PENDING", cyclePlanId = "plan-old",
                ),
            )
        } finally {
            db.close()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("This week's dinners")
            composeRule.onNodeWithText("Regenerate week").performClick()

            // List 1 first: the marks, in Battles's words.
            waitForText("These are the things you indicated you ran out of", substring = true)
            composeRule.onNodeWithText("Olive oil").assertExists()
            composeRule.onNodeWithText("Next").performClick()

            // List 2 second.
            waitForText("Did you run out of these also?")
            composeRule.onNodeWithText("Next").performClick()

            // List 3 third: the pantry.
            waitForText("Your pantry")
            composeRule.onNodeWithText("Finish and plan the week").performClick()

            // Only now is the week planned: the flow is gone and a plan shows.
            waitForNoText("These are the things you indicated you ran out of", substring = true)
            waitForText("Swap")
        }

        val after = openDb()
        try {
            // The mark was consumed by the completed reconciliation.
            val events = after.ranOutDao().getAll()
            assertEquals(1, events.size)
            assertEquals("CONFIRMED", events.single().state)
            // The stored plan is the new week, not last week's.
            val latest = after.planDao().getLatest()!!
            assertEquals(currentMonday().toString(), latest.weekStart)
            // And the finished plan's id is recorded as reconciled.
            val settings = after.settingsDao().get()!!
            assertTrue(settings.reconciledPlanIdsJson.contains("plan-old"))
        } finally {
            after.close()
        }
    }

    @Test fun cookViewPersistsCheckoffsAndRanOutMarks() {
        seedAppData()
        var ingredientCount = 0
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("This week's dinners")
            composeRule.onNodeWithText("Plan my week").performClick()
            waitForText("Swap")

            composeRule.onAllNodesWithText("Start cooking").onFirst()
                .performScrollTo().performClick()
            waitForText("Ran out of anything?")
            // One toggle per ingredient in the used list, then the same
            // count again in the ran-out section.
            val toggles = composeRule.onAllNodes(isToggleable())
            val total = toggles.fetchSemanticsNodes().size
            assertTrue("cook view renders ingredient toggles", total >= 2 && total % 2 == 0)
            ingredientCount = total / 2

            toggles[0].performScrollTo().performClick()
            toggles[ingredientCount].performScrollTo().performClick()

            composeRule.onNodeWithText("Back to the week").performClick()
            waitForText("Swap")
            composeRule.onAllNodesWithText("Start cooking").onFirst()
                .performScrollTo().performClick()
            waitForText("Ran out of anything?")
            // Both marks survived leaving and reopening the dinner.
            val reopened = composeRule.onAllNodes(isToggleable())
            reopened[0].assertIsOn()
            reopened[ingredientCount].assertIsOn()
        }

        val after = openDb()
        try {
            val events = runBlocking { after.ranOutDao().getAll() }
            assertEquals(1, events.size)
            assertEquals("PENDING", events.single().state)
            assertEquals("COOKING", events.single().source)
            val checkoffs = runBlocking { after.cookCheckoffDao().getAll() }
            assertEquals(1, checkoffs.count { it.checked })
        } finally {
            after.close()
        }
    }
}
