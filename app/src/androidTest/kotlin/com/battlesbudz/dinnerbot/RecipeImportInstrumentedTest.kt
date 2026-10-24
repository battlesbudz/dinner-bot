package com.battlesbudz.dinnerbot

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase RI-2 flow proofs:
 * - A: paste text → review shows the spring-onion line unmatched (the
 *   strict matcher never merges it into yellow onions) → save → the
 *   recipe is in the library, and the spring onions are IN the price
 *   book as their own guess-priced item (Battles's correction,
 *   2026-10-24: everything is in the pricing book).
 * - B: a share intent from another app opens the review directly on
 *   the shared text.
 * - C: a link that cannot be read leaves Andrea on the intake screen
 *   with an error and saves nothing.
 * - D: the review's suggestion for "onion" applies only when tapped,
 *   and the saved recipe then prices that line from yellow onions.
 */
@RunWith(AndroidJUnit4::class)
class RecipeImportInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
        val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
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

    private fun waitForText(text: String, substring: Boolean = false) {
        awaitStep("waitForText($text)") {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** After a save tap, wait until the import flow has actually closed. */
    private fun waitForImportClosed() {
        awaitStep("import flow closed") { tagCount("import-save") == 0 }
    }

    private fun tagCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun tagCountUnmerged(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    /** Which status branch a review row took, for failure messages. */
    private fun rowStatus(index: Int): String = when {
        tagCountUnmerged("import-row-$index-unmatched") > 0 -> "unmatched"
        tagCountUnmerged("import-row-$index-covered") > 0 -> "covered"
        textPresent("In the price book") -> "matched (price book text present)"
        else -> "no row $index status found"
    }

    private fun textPresent(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring)
            .fetchSemanticsNodes().isNotEmpty()

    /** Wait for a review row to read unmatched, reporting its actual branch on failure. */
    private fun awaitRowUnmatched(index: Int) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                tagCountUnmerged("import-row-$index-unmatched") > 0
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "row $index never read unmatched: status=${rowStatus(index)}, " +
                    "suggestionTag=${tagCount("import-suggestion-$index")}, " +
                    "qtySuggestionTag=${tagCount("import-qty-suggestion-$index")}, " +
                    "rowTag=${tagCount("import-row-$index")}",
            )
        }
    }

    private fun openDb(): DinnerBotDatabase =
        Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()

    private val springOnionText = """
        Spring Onion Soup
        Serves 4
        Ingredients
        2 spring onions, sliced
        1 cup flour
        Instructions
        Stir everything together.
        Serve hot.
    """.trimIndent()

    @Test fun pasteTextImportKeepsSpringOnionsAsWritten() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("import-recipe").performScrollTo().performClick()
            waitForTag("import-text-input")
            composeRule.onNodeWithTag("import-text-input")
                .performScrollTo().performTextInput(springOnionText)
            composeRule.onNodeWithTag("import-text-go").performScrollTo().performClick()

            // The review: spring onions unmatched, shown as written.
            awaitRowUnmatched(0)
            assertTrue(tagCountUnmerged("import-row-0-unmatched") > 0)
            waitForText("spring onions", substring = true)

            composeRule.onNodeWithTag("import-save").performScrollTo().performClick()

            // Saved: the detail screen opens on her recipe, as written.
            waitForImportClosed()
            waitForText("Spring Onion Soup")
            waitForText("spring onions", substring = true)
        }
        val db = openDb()
        try {
            val imported = runBlocking { db.importedRecipeDao().getAll() }
            assertEquals(1, imported.size)
            val model = with(SeedMapper) { imported.single().toModel() }
            assertEquals("Spring Onion Soup", model.recipe.title)
            // The flour matched an existing item; the spring onions
            // matched nothing, so the import put them in the book as a
            // guess-priced item of their own and linked the recipe.
            assertEquals("flour", model.recipe.ingredients[1].priceItemId)
            val createdId = model.recipe.ingredients[0].priceItemId
            assertEquals("user-spring-onion", createdId)
            assertEquals(null, model.recipe.ingredients[0].pantryItem)
            val items = runBlocking { db.priceItemDao().getAll() }
            assertEquals(43, items.size)
            val created = items.single { it.id == "user-spring-onion" }
            assertEquals("spring onions", created.name)
            assertTrue(created.guessPriced)
            assertTrue(created.packPriceCents > 0)
            assertTrue(runBlocking { db.recipeOverrideDao().getAll() }.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun shareIntentOpensTheReviewDirectly() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, springOnionText)
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            // No intake screen: the shared text goes straight to review.
            waitForTag("import-title")
            awaitRowUnmatched(0)
            composeRule.onNodeWithTag("import-save").performScrollTo().performClick()
            waitForImportClosed()
            waitForText("Spring Onion Soup")
        }
        val db = openDb()
        try {
            assertEquals(1, runBlocking { db.importedRecipeDao().getAll() }.size)
        } finally {
            db.close()
        }
    }

    @Test fun unreadableLinkStaysOnIntakeAndSavesNothing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("import-recipe").performScrollTo().performClick()
            waitForTag("import-link-input")
            composeRule.onNodeWithTag("import-link-input")
                .performTextInput("http://127.0.0.1:9/recipe")
            composeRule.onNodeWithTag("import-link-go").performClick()
            waitForTag("import-error")
            composeRule.onNodeWithTag("import-error").assertIsDisplayed()
            // Still on intake: the text route is right there.
            waitForTag("import-text-input")
            assertEquals(0, tagCount("import-save"))
        }
        val db = openDb()
        try {
            assertTrue(runBlocking { db.importedRecipeDao().getAll() }.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun suggestionAppliesOnlyWhenTapped() {
        val text = """
            Onion Skillet
            Ingredients
            1 onion, diced
            1 cup flour
            Instructions
            Cook it all in the skillet.
        """.trimIndent()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("import-recipe").performScrollTo().performClick()
            waitForTag("import-text-input")
            composeRule.onNodeWithTag("import-text-input")
                .performScrollTo().performTextInput(text)
            composeRule.onNodeWithTag("import-text-go").performScrollTo().performClick()
            waitForTag("import-suggestion-0")
            composeRule.onNodeWithTag("import-suggestion-0")
                .performScrollTo().performClick()
            composeRule.onNodeWithTag("import-save").performScrollTo().performClick()
            waitForImportClosed()
            waitForText("Onion Skillet")
        }
        val db = openDb()
        try {
            val imported = runBlocking { db.importedRecipeDao().getAll() }
            val model = with(SeedMapper) { imported.single().toModel() }
            assertEquals("yellow-onions", model.recipe.ingredients[0].priceItemId)
        } finally {
            db.close()
        }
    }
}
