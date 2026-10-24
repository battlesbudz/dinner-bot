package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
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
 * Phase RI-1 flow proofs:
 * - A: from the recipe screen, a from-now-on edit (there is no tonight
 *   there) shows the "your edits" marker, survives an app restart, and
 *   restore-original brings the seed version back whole.
 * - B: from a planned night's cook view, removing an ingredient with
 *   "Tonight only" changes tonight's dinner and leaves the saved recipe
 *   completely alone (no edits marker).
 * - C: the same cook-view edit saved "From now on" marks the saved
 *   recipe as edited.
 */
@RunWith(AndroidJUnit4::class)
class RecipeEditingInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    // The first recipe in the title-ordered library, and its first
    // ingredient's line as the detail screen renders it.
    private lateinit var firstTitle: String
    private lateinit var firstLineOriginal: String
    private lateinit var firstLineEdited: String

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
        val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
        val recipes = SeedDataParser.parseRecipes(recipesJson).sortedBy { it.title }
        val itemsById = SeedDataParser.parsePriceBook(priceJson).associateBy { it.id }
        val first = recipes.first()
        firstTitle = first.title
        val ingredient = first.ingredients.first()
        val itemName = itemsById[ingredient.priceItemId]?.name ?: ingredient.pantryItem ?: ""
        firstLineOriginal = "${trim(ingredient.quantity.amount)} ${ingredient.quantity.unit.label} $itemName"
        firstLineEdited = "${trim(ingredient.quantity.amount + 2)} ${ingredient.quantity.unit.label} $itemName"

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

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.2f", value).trimEnd('0').trimEnd('.')

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The cook view's ran-out rows ("<name> — ran out"), in order. */
    private fun ranOutRowNames(): List<String> =
        composeRule.onAllNodesWithText(" — ran out", substring = true)
            .fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.Text]
                    .joinToString("") { it.text }
                    .removeSuffix(" — ran out")
            }

    private fun openFirstRecipe() {
        waitForText("Dinner Bot recipes")
        composeRule.onNodeWithText(firstTitle).performClick()
        waitForTag("edit-ingredients")
    }

    @Test fun libraryEditMarksPersistsAndRestores() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openFirstRecipe()
            composeRule.onNodeWithTag("edit-ingredients").performClick()
            waitForTag("ingredient-qty-0")
            composeRule.onNodeWithTag("ingredient-qty-0").performClick()
            waitForTag("qty-amount")
            val newAmount = firstLineEdited.substringBefore(' ')
            composeRule.onNodeWithTag("qty-amount").performTextReplacement(newAmount)
            composeRule.onNodeWithTag("qty-save").performClick()
            composeRule.onNodeWithTag("editor-save").performClick()

            // The marker and the edited line are on the detail screen.
            waitForTag("recipe-has-edits")
            composeRule.onNodeWithTag("recipe-has-edits").assertIsDisplayed()
            assertTrue(
                composeRule.onAllNodesWithText(firstLineEdited, substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
        // A fresh launch (the edit lives in the database) shows it still.
        ActivityScenario.launch(MainActivity::class.java).use {
            openFirstRecipe()
            waitForTag("recipe-has-edits")
            assertTrue(
                composeRule.onAllNodesWithText(firstLineEdited, substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            // Restore-original: the seed version stands again whole.
            composeRule.onNodeWithTag("restore-original").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("recipe-has-edits")
                    .fetchSemanticsNodes().isEmpty()
            }
            assertTrue(
                composeRule.onAllNodesWithText(firstLineOriginal, substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    /** Generate a week and open the first night's cook view; returns tonight's recipe title. */
    private fun openFirstCookNight(): String {
        waitForText("Dinner Bot recipes")
        composeRule.onNodeWithTag("tab-Plan").performClick()
        waitForText("This week's dinners")
        composeRule.onNodeWithText("Plan my week").performClick()
        waitForText("Swap")
        composeRule.onAllNodesWithText("Start cooking").onFirst()
            .performScrollTo().performClick()
        waitForText("Cooking:", substring = true)
        val header = composeRule.onAllNodesWithText("Cooking:", substring = true)
            .fetchSemanticsNodes().first()
        return header.config[SemanticsProperties.Text]
            .joinToString("") { it.text }
            .removePrefix("Cooking: ")
    }

    @Test fun cookViewTonightOnlyEditLeavesTheSavedRecipeAlone() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val title = openFirstCookNight()
            val before = ranOutRowNames()
            assertTrue("tonight has ingredients to edit", before.size >= 2)
            val removedName = before.first()

            composeRule.onNodeWithTag("edit-ingredients").performClick()
            waitForTag("ingredient-remove-0")
            composeRule.onNodeWithTag("ingredient-remove-0").performClick()
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForTag("edit-scope-tonight")
            composeRule.onNodeWithTag("edit-scope-tonight").performClick()

            // Tonight's dinner lost exactly that ingredient.
            composeRule.waitUntil(timeoutMillis = 15_000) {
                ranOutRowNames().size == before.size - 1
            }
            assertEquals(before.drop(1), ranOutRowNames())
            assertTrue(removedName !in ranOutRowNames())

            // The saved recipe carries no edit: no marker on its screen.
            composeRule.onNodeWithText("Back to the week").performClick()
            composeRule.onNodeWithTag("tab-Recipes").performClick()
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithText(title).performScrollTo().performClick()
            waitForTag("edit-ingredients")
            assertTrue(
                "a tonight-only edit never marks the saved recipe",
                composeRule.onAllNodesWithTag("recipe-has-edits")
                    .fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test fun cookViewFromNowOnEditMarksTheSavedRecipe() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val title = openFirstCookNight()
            val before = ranOutRowNames()
            assertTrue(before.size >= 2)

            composeRule.onNodeWithTag("edit-ingredients").performClick()
            waitForTag("ingredient-remove-0")
            composeRule.onNodeWithTag("ingredient-remove-0").performClick()
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForTag("edit-scope-always")
            composeRule.onNodeWithTag("edit-scope-always").performClick()

            composeRule.onNodeWithText("Back to the week").performClick()
            composeRule.onNodeWithTag("tab-Recipes").performClick()
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithText(title).performScrollTo().performClick()
            waitForTag("recipe-has-edits")
            composeRule.onNodeWithTag("recipe-has-edits").assertIsDisplayed()
        }
    }
}
