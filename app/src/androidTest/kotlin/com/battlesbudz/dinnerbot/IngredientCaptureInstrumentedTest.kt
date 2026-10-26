package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.feature.recipes.RecipeCostCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v0.6.2 hotfix proofs — Andrea's dead end, 2026-10-25: she tried
 * to add her own recipe, the first ingredient was not in the price
 * book, and there was no way to add it; she was stopped at step one.
 *
 * - A: the reported failure itself. "Add your own recipe" with a
 *   brand-new ingredient (gochujang — not one of the 42 seed items),
 *   NO price typed: the recipe saves end to end, the ingredient is in
 *   the book guess-priced, and the saved recipe costs from it.
 * - B: the price book's own add door, with a typed price: the item
 *   lands priced, user-corrected, at the exact cents.
 * - C: editing an existing library recipe and adding a brand-new
 *   ingredient with a price: the household override saves AND the
 *   item is in the book (the override path never linked before).
 */
@RunWith(AndroidJUnit4::class)
class IngredientCaptureInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var firstTitle: String
    private lateinit var firstRecipeId: String

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
        val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
        val first = SeedDataParser.parseRecipes(recipesJson).sortedBy { it.title }.first()
        firstTitle = first.title
        firstRecipeId = first.id
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

    private fun tagCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun openDb(): DinnerBotDatabase =
        Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()

    /**
     * From an open ingredient editor: add a brand-new ingredient that
     * is not in the price book — the picker's "New ingredient…" entry,
     * the shared form (price only when [price] is given), then the
     * quantity step — ending back in the editor with the row added.
     */
    private fun addNewIngredientInEditor(name: String, packAmount: String, price: String?) {
        composeRule.onNodeWithTag("ingredient-add").performClick()
        waitForTag("picker-add-new")
        composeRule.onNodeWithTag("picker-add-new").performClick()
        waitForTag("add-item-name")
        composeRule.onNodeWithTag("add-item-name").performScrollTo().performTextInput(name)
        composeRule.onNodeWithTag("add-item-pack-amount").performScrollTo()
            .performTextInput(packAmount)
        if (price != null) {
            composeRule.onNodeWithTag("add-item-price").performScrollTo().performTextInput(price)
        }
        composeRule.onNodeWithTag("add-item-confirm").performClick()
        waitForTag("qty-amount")
        composeRule.onNodeWithTag("qty-save").performClick()
        waitForTag("editor-save")
    }

    @Test fun handMadeRecipeWithABrandNewUnpricedIngredientSaves() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("add-own-recipe").performScrollTo().performClick()
            waitForTag("import-title")
            composeRule.onNodeWithTag("import-title").performScrollTo()
                .performTextInput("Andrea's Gochujang Noodles")

            // The exact step she was stopped at: adding the first
            // ingredient, which the price book does not have.
            composeRule.onNodeWithTag("import-edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-add")
            addNewIngredientInEditor(name = "Gochujang", packAmount = "1", price = null)
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForNoTag("editor-save")

            // Back on the review the row is a priced book ingredient,
            // and the recipe saves. (The row renders as one line —
            // "1 ct Gochujang" — so the name matches as a substring.)
            waitForText("Gochujang", substring = true)
            composeRule.onNodeWithTag("import-save").performScrollTo().performClick()
            awaitStep("import flow closed") { tagCount("import-save") == 0 }
            waitForText("Andrea's Gochujang Noodles")
            waitForText("Estimated cost:", substring = true)
        }
        val db = openDb()
        try {
            val imported = runBlocking { db.importedRecipeDao().getAll() }
            assertEquals(1, imported.size)
            val model = with(SeedMapper) { imported.single().toModel() }
            assertEquals("Andrea's Gochujang Noodles", model.recipe.title)
            val ingredient = model.recipe.ingredients.single()
            assertEquals("user-gochujang", ingredient.priceItemId)

            val items = runBlocking { db.priceItemDao().getAll() }
            assertEquals("the 43 seed items plus the one she added", 44, items.size)
            val created = items.single { it.id == "user-gochujang" }
            assertEquals("Gochujang", created.name)
            assertTrue("no price typed, so the item starts guess-priced", created.guessPriced)
            assertFalse(created.userCorrected)
            assertTrue(created.packPriceCents > 0)

            // The saved recipe costs from the new item: saved AND costed.
            val itemsById = items.map { with(SeedMapper) { it.toModel() } }.associateBy { it.id }
            val cost = RecipeCostCalculator.ingredientCostCents(
                model.recipe, model.recipe.servings, itemsById,
            )
            assertTrue("the recipe must cost from its new ingredient", cost > 0)
        } finally {
            db.close()
        }
    }

    @Test fun priceBookAddWithATypedPriceLandsPriced() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Pantry").performClick()
            waitForTag("pantry-toggle-prices")
            composeRule.onNodeWithTag("pantry-toggle-prices").performClick()
            waitForTag("pricebook-add")
            composeRule.onNodeWithTag("pricebook-add").performClick()
            waitForTag("add-item-name")
            composeRule.onNodeWithTag("add-item-name").performScrollTo()
                .performTextInput("Tahini")
            composeRule.onNodeWithTag("add-item-pack-amount").performScrollTo()
                .performTextInput("1")
            composeRule.onNodeWithTag("add-item-price").performScrollTo()
                .performTextInput("4.29")
            composeRule.onNodeWithTag("add-item-confirm").performClick()
            waitForNoTag("add-item-confirm")
        }
        val db = openDb()
        try {
            val items = runBlocking { db.priceItemDao().getAll() }
            assertEquals(44, items.size)
            val created = items.single { it.id == "user-tahini" }
            assertEquals("Tahini", created.name)
            assertEquals(429, created.packPriceCents)
            assertTrue("a typed price is her number", created.userCorrected)
            assertFalse(created.guessPriced)
        } finally {
            db.close()
        }
    }

    @Test fun editingALibraryRecipeAddsTheNewIngredientToTheBook() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(firstTitle))
            composeRule.onNodeWithText(firstTitle).performClick()
            waitForTag("edit-ingredients")
            composeRule.onNodeWithTag("edit-ingredients").performScrollTo().performClick()
            waitForTag("ingredient-add")
            addNewIngredientInEditor(name = "Harissa", packAmount = "1", price = "1.19")
            composeRule.onNodeWithTag("editor-save").performClick()
            waitForTag("recipe-has-edits")
        }
        val db = openDb()
        try {
            val overrides = runBlocking { db.recipeOverrideDao().getAll() }
            val override = overrides.single { it.recipeId == firstRecipeId }
            val model = with(SeedMapper) { override.toModel() }
            assertTrue(
                "the household version carries the new ingredient",
                model.ingredients.any { it.priceItemId == "user-harissa" },
            )
            val items = runBlocking { db.priceItemDao().getAll() }
            assertEquals(44, items.size)
            val created = items.single { it.id == "user-harissa" }
            assertEquals(119, created.packPriceCents)
            assertTrue(created.userCorrected)
            assertFalse(created.guessPriced)
        } finally {
            db.close()
        }
    }
}
