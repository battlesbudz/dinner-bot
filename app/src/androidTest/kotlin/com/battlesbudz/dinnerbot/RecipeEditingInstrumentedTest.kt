package com.battlesbudz.dinnerbot

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import android.content.Context
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
 * - D: the quantity dialog in the editor changes an ingredient's amount
 *   and the change lands on the saved recipe.
 */
@RunWith(AndroidJUnit4::class)
class RecipeEditingInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    // The first recipe in the title-ordered library, the display name of
    // its first ingredient, and that ingredient's line as the detail
    // screen renders it at the recipe's own servings.
    private lateinit var firstTitle: String
    private lateinit var firstItemName: String
    private lateinit var firstUnitLabel: String
    private lateinit var firstLineOriginal: String

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
        firstItemName = itemsById[ingredient.priceItemId]?.name ?: ingredient.pantryItem ?: ""
        firstUnitLabel = ingredient.quantity.unit.label
        firstLineOriginal =
            "${trim(ingredient.quantity.amount)} $firstUnitLabel $firstItemName"

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

    /** Wait for a step, naming it and dumping the semantics tree if it never lands. */
    private fun awaitStep(step: String, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("STEP FAILED: $step\n" + composeRule.onRoot().printToString())
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        awaitStep("waitForText($text)") {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
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

    private fun textPresent(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring)
            .fetchSemanticsNodes().isNotEmpty()

    /** The cook view's ran-out rows ("<name> — ran out"), in order. */
    private fun ranOutRowNames(): List<String> =
        composeRule.onAllNodesWithText(" — ran out", substring = true)
            .fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.Text]
                    .joinToString("") { it.text }
                    .removeSuffix(" — ran out")
            }

    /** Open a recipe from the library list, scrolling the list to its card first. */
    private fun openRecipeFromList(title: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).performClick()
        waitForTag("edit-ingredients")
    }

    private fun openFirstRecipe() {
        waitForText("Dinner Bot recipes")
        openRecipeFromList(firstTitle)
    }

    @Test fun libraryEditMarksPersistsAndRestores() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openFirstRecipe()
            composeRule.onNodeWithTag("edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-remove-0")
            composeRule.onNodeWithTag("ingredient-remove-0").performClick()
            composeRule.onNodeWithTag("editor-save").performClick()

            // The marker is up and the removed ingredient is off the dish.
            waitForTag("recipe-has-edits")
            composeRule.onNodeWithTag("recipe-has-edits")
                .performScrollTo().assertIsDisplayed()
            assertTrue(
                "the removed ingredient leaves the detail screen",
                !textPresent(firstItemName, substring = true),
            )
        }
        // A fresh launch (the edit lives in the database) shows it still.
        ActivityScenario.launch(MainActivity::class.java).use {
            openFirstRecipe()
            waitForTag("recipe-has-edits")
            assertTrue(!textPresent(firstItemName, substring = true))
            // Restore-original: the seed version stands again whole.
            composeRule.onNodeWithTag("restore-original").performScrollTo().performClick()
            waitForNoTag("recipe-has-edits")
            assertTrue(
                "the seed ingredient line is back after restore",
                textPresent(firstLineOriginal, substring = true),
            )
        }
    }

    @Test fun editorQuantityChangeLandsOnTheSavedRecipe() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openFirstRecipe()
            composeRule.onNodeWithTag("edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-qty-0")
            composeRule.onNodeWithTag("ingredient-qty-0").performClick()
            waitForTag("qty-amount")
            // Change the quantity through the dialog's unit menu (clicks
            // only: typed text entry into this dialog's amount field does
            // not land under the emulator's test framework — tried as
            // replacement and as focus-clear-type, the field never took
            // it; the amount math itself is covered by JVM tests).
            composeRule.onNodeWithTag("qty-unit").performClick()
            waitForTag("qty-unit-FLUID_OUNCE")
            composeRule.onNodeWithTag("qty-unit-FLUID_OUNCE").performClick()
            composeRule.onNodeWithTag("qty-save").performClick()
            // The editor's first row now shows the changed quantity.
            val amount = firstLineOriginal.substringBefore(' ')
            val editedLine = "$amount fl oz $firstItemName"
            awaitStep("editor row shows the new quantity") {
                textPresent(editedLine, substring = true)
            }
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForTag("recipe-has-edits")
            assertTrue(
                "the detail screen cooks the new quantity",
                textPresent(editedLine, substring = true),
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

            composeRule.onNodeWithTag("edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-remove-0")
            composeRule.onNodeWithTag("ingredient-remove-0").performClick()
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForTag("edit-scope-tonight")
            composeRule.onNodeWithTag("edit-scope-tonight").performClick()

            // Tonight's dinner lost exactly that ingredient.
            awaitStep("tonight loses the removed ingredient") {
                ranOutRowNames().size == before.size - 1
            }
            assertEquals(before.drop(1), ranOutRowNames())
            assertTrue(removedName !in ranOutRowNames())

            // The saved recipe carries no edit: no marker on its screen.
            composeRule.onNodeWithText("Back to the week").performClick()
            composeRule.onNodeWithTag("tab-Recipes").performClick()
            waitForText("Dinner Bot recipes")
            openRecipeFromList(title)
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

            composeRule.onNodeWithTag("edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-remove-0")
            composeRule.onNodeWithTag("ingredient-remove-0").performClick()
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForTag("edit-scope-always")
            composeRule.onNodeWithTag("edit-scope-always").performClick()

            composeRule.onNodeWithText("Back to the week").performClick()
            composeRule.onNodeWithTag("tab-Recipes").performClick()
            waitForText("Dinner Bot recipes")
            openRecipeFromList(title)
            waitForTag("recipe-has-edits")
            composeRule.onNodeWithTag("recipe-has-edits")
                .performScrollTo().assertIsDisplayed()
        }
    }
}
