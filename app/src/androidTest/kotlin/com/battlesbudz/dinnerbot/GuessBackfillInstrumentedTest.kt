package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
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
 * Battles's correction (2026-10-24), proven on a v0.5.1-shaped
 * install: a recipe imported before the correction holds ingredients
 * with no price book item. On upgrade the missing items appear in the
 * book at guessed prices with the guess mark, the recipe links to
 * them, an ingredient the book already names links without creating
 * anything, and a second launch changes nothing. Correcting the
 * guessed price in the price book then writes the real price and
 * clears the mark for good.
 */
@RunWith(AndroidJUnit4::class)
class GuessBackfillInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
        val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
        withDb { db ->
            SeedLoader(db).seedIfEmpty(priceJson, recipesJson)
            db.settingsDao().upsert(
                SettingsEntity(
                    id = 1,
                    householdSize = 4,
                    cookNightsJson = "[\"MON\"]",
                    weeklyBudgetCents = 100_00,
                    wontEatJson = "[]",
                    dietNotes = "",
                    pantryStaplesJson = "[]",
                    onboardingComplete = true,
                ),
            )
            // A v0.5.1 import: spring onions matched nothing and stayed
            // as written; the flour line is as written too, though the
            // book has flour — the backfill links it without creating.
            val imported = ImportedRecipe(
                recipe = Recipe(
                    id = "imported-spring-soup", title = "Spring Onion Soup",
                    servings = 4, activeMinutes = 15, cuisine = "Test",
                    mainProtein = "Vegetables",
                    ingredients = listOf(
                        RecipeIngredient(
                            pantryItem = "spring onions",
                            quantity = Quantity(2.0, MeasureUnit.COUNT),
                        ),
                        RecipeIngredient(
                            pantryItem = "Flour",
                            quantity = Quantity(1.0, MeasureUnit.CUP),
                        ),
                    ),
                    steps = listOf("Stir.", "Serve."), origin = RecipeOrigin.IMPORTED,
                ),
                sourceLink = null, createdAtMillis = 5L,
            )
            db.importedRecipeDao().upsert(with(SeedMapper) { imported.toEntity() })
        }
    }

    @After fun tearDown() {
        context.deleteDatabase("dinner-bot.db")
    }

    private fun <T> withDb(block: suspend (DinnerBotDatabase) -> T): T = runBlocking {
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithText(text, substring = substring)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "never saw text: $text\n" + composeRule.onRoot().printToString(),
            )
        }
    }

    /** Launch the app and wait for the load (and its backfill) to land. */
    private fun launchAndLoad() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            // The backfill runs inside the load; poll the book for it.
            var created = false
            repeat(40) {
                if (!created) {
                    created = withDb { db ->
                        db.priceItemDao().getById("user-spring-onion") != null
                    }
                    if (!created) Thread.sleep(500)
                }
            }
            assertTrue("backfill never created the spring onions item", created)
        }
    }

    @Test fun upgradeBackfillCreatesGuessItemsLinksRecipesAndIsIdempotent() {
        launchAndLoad()
        withDb { db ->
            val items = db.priceItemDao().getAll()
            // The seed book (42) plus exactly one created item.
            assertEquals(43, items.size)
            val created = items.single { it.id == "user-spring-onion" }
            assertEquals("spring onions", created.name)
            assertTrue(created.guessPriced)
            assertTrue(created.packPriceCents > 0)
            // The recipe links to the created item and to flour.
            val stored = db.importedRecipeDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            val ingredients = stored.single().recipe.ingredients
            assertEquals("user-spring-onion", ingredients[0].priceItemId)
            assertEquals(null, ingredients[0].pantryItem)
            assertEquals("flour", ingredients[1].priceItemId)
            assertEquals(null, ingredients[1].pantryItem)
        }
        // A second launch creates and rewrites nothing.
        launchAndLoad()
        withDb { db ->
            assertEquals(43, db.priceItemDao().getAll().size)
        }
    }

    @Test fun correctingTheGuessedPriceInThePriceBookClearsTheMark() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            var guessCents = 0
            repeat(40) {
                if (guessCents == 0) {
                    guessCents = withDb { db ->
                        db.priceItemDao().getById("user-spring-onion")?.packPriceCents ?: 0
                    }
                    if (guessCents == 0) Thread.sleep(500)
                }
            }
            assertTrue("backfill never created the spring onions item", guessCents > 0)

            composeRule.onNodeWithTag("tab-Prices").performClick()
            waitForText("Price book")
            // The item is visibly a guess in the book. It sits at the
            // end of the lazy list, so swipe until it is composed.
            var composed = false
            repeat(25) {
                if (!composed) {
                    composed = composeRule.onAllNodesWithText("spring onions")
                        .fetchSemanticsNodes().isNotEmpty()
                    if (!composed) {
                        composeRule.onRoot().performTouchInput { swipeUp() }
                        Thread.sleep(300)
                    }
                }
            }
            assertTrue("the spring onions row never came into view", composed)
            waitForText("Guessed price", substring = true)
            val guessText = "$${guessCents / 100}.${(guessCents % 100).toString().padStart(2, '0')}"
            // Tap THIS item's price: another book item could share the
            // same dollar text, so pick the price node nearest the
            // item's name and tap it by coordinates.
            Thread.sleep(500)
            val nameBounds = composeRule.onAllNodesWithText("spring onions")
                .fetchSemanticsNodes().single().boundsInRoot
            val priceBounds = composeRule.onAllNodesWithText(guessText)
                .fetchSemanticsNodes()
                .minByOrNull {
                    kotlin.math.abs(it.boundsInRoot.top - nameBounds.top) +
                        kotlin.math.abs(it.boundsInRoot.left - nameBounds.left)
                }!!.boundsInRoot
            androidx.test.uiautomator.UiDevice
                .getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
                .click(
                    (priceBounds.left + priceBounds.right).toInt() / 2,
                    (priceBounds.top + priceBounds.bottom).toInt() / 2,
                )
            waitForText("Pack price for", substring = true)
            composeRule.onNode(hasSetTextAction()).performTextReplacement("1.45")
            composeRule.onNodeWithText("Save").performClick()
            // The write lands in the book: real price, mark cleared.
            var corrected = false
            repeat(40) {
                if (!corrected) {
                    corrected = withDb { db ->
                        val item = db.priceItemDao().getById("user-spring-onion")
                        item != null && !item.guessPriced && item.packPriceCents == 145
                    }
                    if (!corrected) Thread.sleep(500)
                }
            }
            assertTrue("the correction never landed", corrected)
            withDb { db ->
                assertFalse(db.priceItemDao().getById("user-spring-onion")!!.guessPriced)
            }
        }
    }
}
