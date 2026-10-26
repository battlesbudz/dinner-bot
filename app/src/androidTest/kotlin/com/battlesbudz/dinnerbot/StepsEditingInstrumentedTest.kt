package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.6.3: steps editing, Battles's field report — after creating a
 * recipe she could add ingredients but the steps could not be
 * re-edited.
 *
 * - Her own recipe: the steps edit rewrites the stored recipe, the
 *   detail screen shows the new steps, and the cook view cooks from
 *   them.
 * - A library recipe: the steps edit lands on the household override
 *   with the ingredient list untouched, and restore-original brings
 *   the seed steps back.
 */
@RunWith(AndroidJUnit4::class)
class StepsEditingInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var firstTitle: String
    private lateinit var firstRecipeId: String
    private lateinit var firstSeedStep: String

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
        val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
        val first = SeedDataParser.parseRecipes(recipesJson).sortedBy { it.title }.first()
        firstTitle = first.title
        firstRecipeId = first.id
        firstSeedStep = first.steps.first()
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()
        try {
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

    private fun awaitStep(step: String, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("STEP FAILED: $step\n" + composeRule.onRoot().printToString())
        }
    }

    private fun waitForTag(tag: String) {
        awaitStep("waitForTag($tag)") {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoTag(tag: String) {
        awaitStep("waitForNoTag($tag)") {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        awaitStep("waitForText($text)") {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDb(): DinnerBotDatabase =
        Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()

    /** From an open recipe detail: replace the steps wholesale through the steps editor. */
    private fun replaceSteps(vararg lines: String) {
        composeRule.onNodeWithTag("edit-steps").performScrollTo().performClick()
        waitForTag("edit-steps-field")
        composeRule.onNodeWithTag("edit-steps-field").performTextClearance()
        composeRule.onNodeWithTag("edit-steps-field")
            .performTextInput(lines.joinToString("\n"))
        composeRule.onNodeWithTag("edit-steps-save").performClick()
        waitForNoTag("edit-steps-field")
    }

    @Test fun ownRecipeStepsEditPersistsAndShowsInTheCookView() {
        var recipeId: String? = null
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("add-own-recipe").performScrollTo().performClick()
            waitForTag("import-title")
            composeRule.onNodeWithTag("import-title").performScrollTo()
                .performTextInput("Andrea's Weeknight Eggs")

            // One ingredient straight from the book (Eggs), so the
            // recipe is real enough to plan and cook.
            composeRule.onNodeWithTag("import-edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-add")
            composeRule.onNodeWithTag("ingredient-add").performClick()
            waitForTag("picker-search")
            composeRule.onNodeWithTag("picker-search").performTextInput("Eggs")
            composeRule.onNodeWithTag("picker-item-eggs").performClick()
            waitForTag("qty-amount")
            composeRule.onNodeWithTag("qty-save").performClick()
            waitForTag("editor-save")
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForNoTag("editor-save")

            composeRule.onNodeWithTag("import-steps").performScrollTo()
                .performTextInput("Crack the eggs.\nCook them gently.")
            composeRule.onNodeWithTag("import-save").performScrollTo().performClick()
            waitForText("Andrea's Weeknight Eggs")
            waitForText("Estimated cost:", substring = true)

            // The fix itself: the steps can be re-edited afterwards.
            replaceSteps("Scramble slowly.", "Serve on toast.")
            waitForText("1. Scramble slowly.")
            waitForText("2. Serve on toast.")
        }
        val db = openDb()
        try {
            val imported = runBlocking { db.importedRecipeDao().getAll() }
            assertEquals(1, imported.size)
            val model = with(SeedMapper) { imported.single().toModel() }
            recipeId = model.recipe.id
            assertEquals(
                listOf("Scramble slowly.", "Serve on toast."),
                model.recipe.steps,
            )
            // Plan tonight around her recipe, the way a generated
            // week would land it, and let the cook view read it.
            val monday = LocalDate.now().with(DayOfWeek.MONDAY).toString()
            runBlocking {
                db.planDao().insert(
                    with(PlanMapper) {
                        PlanMapper.StoredPlan(
                            id = "plan-steps-test",
                            weekStart = monday,
                            budgetCents = 100_00,
                            cashNeededCents = 0,
                            foodCostCents = 0,
                            samsMonthlySavingsCents = 0,
                            nights = listOf(
                                PlannedNight(day = "MON", recipeId = recipeId!!),
                            ),
                            notes = emptyList(),
                            createdAtMillis = System.currentTimeMillis(),
                        ).toEntity()
                    },
                )
            }
        } finally {
            db.close()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("Start cooking")
            composeRule.onAllNodesWithText("Start cooking").onFirst()
                .performScrollTo().performClick()
            waitForText("Cooking: Andrea's Weeknight Eggs")
            waitForText("Scramble slowly.", substring = true)
            waitForText("Serve on toast.", substring = true)
        }
    }

    @Test fun libraryStepsEditLandsOnTheOverrideAndRestores() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(firstTitle))
            composeRule.onNodeWithText(firstTitle).performClick()
            waitForTag("edit-steps")
            replaceSteps("Household step one.", "Household step two.")
            waitForText("1. Household step one.")
            waitForText("2. Household step two.")
            waitForTag("recipe-has-edits")

            val db = openDb()
            try {
                val override = runBlocking { db.recipeOverrideDao().getAll() }
                    .single { it.recipeId == firstRecipeId }
                    .let { with(SeedMapper) { it.toModel() } }
                assertEquals(
                    listOf("Household step one.", "Household step two."),
                    override.steps,
                )
                // The ingredient half of the override is the recipe's
                // own list, not an empty shell: a steps-only edit must
                // never take the ingredients with it.
                val recipesJson = context.assets.open("recipes.json")
                    .bufferedReader().use { it.readText() }
                val seed = SeedDataParser.parseRecipes(recipesJson)
                    .single { it.id == firstRecipeId }
                assertEquals(seed.ingredients, override.ingredients)
            } finally {
                db.close()
            }

            // Restore-original brings the seed steps back.
            composeRule.onNodeWithTag("restore-original").performScrollTo().performClick()
            waitForText("1. $firstSeedStep")
            waitForNoTag("restore-original")
        }
        val db = openDb()
        try {
            val overrides = runBlocking { db.recipeOverrideDao().getAll() }
            assertTrue(
                "restore-original must delete the override row",
                overrides.none { it.recipeId == firstRecipeId },
            )
        } finally {
            db.close()
        }
    }
}
