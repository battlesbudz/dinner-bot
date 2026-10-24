package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.GroceryItemEntity
import com.battlesbudz.dinnerbot.core.data.PantryItemEntity
import com.battlesbudz.dinnerbot.core.data.PlanEntity
import com.battlesbudz.dinnerbot.core.data.RecipeRatingEntity
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.Store
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
 * Phase RI-3 flow proofs:
 * - A: the weekly list shows a pantry-covered item crossed through at
 *   $0 with its covering entry named; "Buy anyway" turns it into a buy
 *   line for the week, and the pantry itself is never written.
 * - B: generating a week containing a not-yet-priced dinner of
 *   Andrea's shows the disclaimer pop-up naming the dinner.
 * - C: cooking one of her recipes to completion fires the keep prompt
 *   once; a thumbs up records the UP rating through the normal path.
 * - D: dismissing the keep prompt records no rating, and the prompt
 *   never fires again for that recipe.
 */
@RunWith(AndroidJUnit4::class)
class WeeklyListRi3InstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    private val potatoesItem = PriceItem(
        id = "russet-potatoes", name = "Russet potatoes", category = "Produce",
        homeStore = Store.ALDI, packDescription = "5 lb bag",
        packQuantity = Quantity(5.0, MeasureUnit.POUND), packPriceCents = 349,
        pricePerUnitCents = 69.8, checkedDate = "2026-10-24", source = "test",
    )
    private val eggsItem = PriceItem(
        id = "eggs", name = "Eggs", category = "Dairy",
        homeStore = Store.ALDI, packDescription = "12 ct",
        packQuantity = Quantity(12.0, MeasureUnit.COUNT), packPriceCents = 185,
        pricePerUnitCents = 15.4, checkedDate = "2026-10-24", source = "test",
    )
    private val potatoRecipe = Recipe(
        id = "potato-night", title = "Potato Night", servings = 4, activeMinutes = 10,
        cuisine = "Test", mainProtein = "Test",
        ingredients = listOf(
            RecipeIngredient(
                priceItemId = "russet-potatoes",
                quantity = Quantity(2.0, MeasureUnit.POUND),
            ),
            RecipeIngredient(
                priceItemId = "eggs", quantity = Quantity(6.0, MeasureUnit.COUNT),
            ),
        ),
        steps = listOf("Cook."),
    )

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

    private fun tagCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    /** Seed a stored one-night plan (MON -> [recipeId]) with the given grocery rows. */
    private suspend fun DinnerBotDatabase.seedStoredPlan(
        planId: String,
        recipeId: String,
        rows: List<GroceryItemEntity>,
    ) {
        planDao().insert(
            PlanEntity(
                id = planId, weekStart = "2026-10-26", budgetCents = 100_00,
                cashNeededCents = 185, foodCostCents = 232, samsMonthlySavingsCents = 0,
                nightsJson = "[{\"day\":\"MON\",\"recipeId\":\"$recipeId\"}]",
                notesJson = "[]", createdAtMillis = 1L,
            ),
        )
        groceryDao().insertAll(rows)
    }

    @Test fun coveredRowShowsCrossedOutAndBuyAnywayBuysItWithoutTouchingThePantry() {
        withDb { db ->
            db.priceItemDao().insertAll(
                listOf(
                    with(SeedMapper) { potatoesItem.toEntity() },
                    with(SeedMapper) { eggsItem.toEntity() },
                ),
            )
            db.recipeDao().insertAll(listOf(with(SeedMapper) { potatoRecipe.toEntity() }))
            db.pantryDao().upsert(PantryItemEntity("potatoes", "Potatoes", owned = true))
            db.seedStoredPlan(
                "plan-ri3", "potato-night",
                listOf(
                    GroceryItemEntity(
                        planId = "plan-ri3", itemId = "eggs", displayName = "Eggs",
                        store = "ALDI", neededAmount = 6.0, neededUnit = "COUNT",
                        packsToBuy = 1, packPriceCents = 185, portionCostCents = 92,
                        checkedOff = false,
                    ),
                    GroceryItemEntity(
                        planId = "plan-ri3", itemId = "russet-potatoes",
                        displayName = "Russet potatoes", store = "ALDI",
                        neededAmount = 2.0, neededUnit = "POUND",
                        packsToBuy = 0, packPriceCents = 349, portionCostCents = 140,
                        checkedOff = false, coveredByPantryName = "Potatoes",
                    ),
                ),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-List").performClick()
            waitForTag("covered-row-russet-potatoes")
            waitForText("Potatoes — from your pantry")
            composeRule.onNodeWithTag("uncross-russet-potatoes")
                .performScrollTo()
                .performClick()
            awaitStep("covered row left the list") {
                tagCount("covered-row-russet-potatoes") == 0
            }
            awaitStep("potatoes row became a buy line") {
                withDb { db ->
                    val row = db.groceryDao().getForPlan("plan-ri3")
                        .singleOrNull { it.itemId == "russet-potatoes" }
                    row != null && row.packsToBuy == 1 && row.coveredByPantryName == null
                }
            }
            withDb { db ->
                val row = db.groceryDao().getForPlan("plan-ri3")
                    .single { it.itemId == "russet-potatoes" }
                assertTrue(row.uncrossed)
            }
            // Uncrossing is a list action only: the pantry still owns potatoes.
            withDb { db ->
                assertTrue(db.pantryDao().getAll().single { it.name == "Potatoes" }.owned)
            }
        }
    }

    @Test fun generatingAWeekWithAnUnpricedImportedDinnerShowsTheDisclaimer() {
        withDb { db ->
            val imported = ImportedRecipe(
                recipe = Recipe(
                    id = "imported-saffron-rice", title = "Saffron Rice Night",
                    servings = 4, activeMinutes = 20, cuisine = "Test",
                    mainProtein = "Rice",
                    ingredients = listOf(
                        RecipeIngredient(
                            pantryItem = "Saffron",
                            quantity = Quantity(0.5, MeasureUnit.OUNCE),
                        ),
                    ),
                    steps = listOf("Cook."), origin = RecipeOrigin.IMPORTED,
                ),
                sourceLink = null, createdAtMillis = 5L,
            )
            db.importedRecipeDao().upsert(with(SeedMapper) { imported.toEntity() })
            // A second unpriced import, also cost 0: generation rotates
            // the ranked pool by one, so the week's single night lands
            // on one of the two either way — and the cap keeps it to one.
            val second = ImportedRecipe(
                recipe = Recipe(
                    id = "imported-truffle-pasta", title = "Truffle Pasta Night",
                    servings = 4, activeMinutes = 15, cuisine = "Test",
                    mainProtein = "Pasta",
                    ingredients = listOf(
                        RecipeIngredient(
                            pantryItem = "Truffle oil",
                            quantity = Quantity(1.0, MeasureUnit.TABLESPOON),
                        ),
                    ),
                    steps = listOf("Cook."), origin = RecipeOrigin.IMPORTED,
                ),
                sourceLink = null, createdAtMillis = 6L,
            )
            db.importedRecipeDao().upsert(with(SeedMapper) { second.toEntity() })
            db.recipeRatingDao().upsert(
                RecipeRatingEntity("imported-saffron-rice", "UP", 1L),
            )
            db.recipeRatingDao().upsert(
                RecipeRatingEntity("imported-truffle-pasta", "UP", 1L),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("Plan my week")
            composeRule.onNodeWithText("Plan my week").performClick()
            waitForTag("unpriced-disclaimer")
            waitForText("One dinner is not priced yet")
            composeRule.onNodeWithText("Got it").performClick()
            awaitStep("disclaimer dismissed") { tagCount("unpriced-disclaimer") == 0 }
            // The planned night is one of her two unpriced imports.
            withDb { db ->
                val plan = db.planDao().getLatest()!!
                assertTrue(plan.nightsJson.contains("imported-"))
            }
        }
    }

    /** Seed the imported egg bowl, planned for MON, ready to cook. */
    private fun seedEggBowlFixtures() {
        withDb { db ->
            db.priceItemDao().insertAll(listOf(with(SeedMapper) { eggsItem.toEntity() }))
            val imported = ImportedRecipe(
                recipe = Recipe(
                    id = "imported-egg-bowl", title = "Egg Bowl Night",
                    servings = 4, activeMinutes = 10, cuisine = "Test",
                    mainProtein = "Eggs",
                    ingredients = listOf(
                        RecipeIngredient(
                            priceItemId = "eggs",
                            quantity = Quantity(6.0, MeasureUnit.COUNT),
                        ),
                    ),
                    steps = listOf("Cook."), origin = RecipeOrigin.IMPORTED,
                ),
                sourceLink = null, createdAtMillis = 5L,
            )
            db.importedRecipeDao().upsert(with(SeedMapper) { imported.toEntity() })
            db.seedStoredPlan(
                "plan-ri3-cook", "imported-egg-bowl",
                listOf(
                    GroceryItemEntity(
                        planId = "plan-ri3-cook", itemId = "eggs", displayName = "Eggs",
                        store = "ALDI", neededAmount = 6.0, neededUnit = "COUNT",
                        packsToBuy = 1, packPriceCents = 185, portionCostCents = 92,
                        checkedOff = false,
                    ),
                ),
            )
        }
    }

    /** Open the planned egg bowl's cook view and check its only ingredient off. */
    private fun cookEggBowlToPrompt() {
        waitForText("Dinner Bot recipes")
        composeRule.onNodeWithTag("tab-Plan").performClick()
        waitForText("Start cooking")
        composeRule.onNodeWithText("Start cooking").performScrollTo().performClick()
        waitForTag("cook-used-item:eggs")
        composeRule.onNodeWithTag("cook-used-item:eggs").performClick()
        waitForTag("keep-prompt")
    }

    @Test fun firstCookOfAnImportedRecipePromptsOnceAndThumbsUpRatesIt() {
        seedEggBowlFixtures()
        ActivityScenario.launch(MainActivity::class.java).use {
            cookEggBowlToPrompt()
            waitForText("Did you like Egg Bowl Night?")
            composeRule.onNodeWithTag("keep-prompt-up").performClick()
            awaitStep("UP rating recorded") {
                withDb { db ->
                    db.recipeRatingDao().getAll()
                        .any { it.recipeId == "imported-egg-bowl" && it.rating == "UP" }
                }
            }
            withDb { db ->
                assertEquals(
                    listOf("imported-egg-bowl"),
                    db.keepPromptDao().getAll().map { it.recipeId },
                )
            }
        }
    }

    @Test fun dismissingTheKeepPromptRecordsNothingAndNeverRepeats() {
        seedEggBowlFixtures()
        ActivityScenario.launch(MainActivity::class.java).use {
            cookEggBowlToPrompt()
            androidx.test.uiautomator.UiDevice
                .getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
                .pressBack()
            awaitStep("prompt dismissed") { tagCount("keep-prompt") == 0 }
            withDb { db ->
                // No rating was recorded...
                assertTrue(
                    db.recipeRatingDao().getAll().none { it.recipeId == "imported-egg-bowl" },
                )
                // ...but the ask itself is on record, so it never repeats.
                assertEquals(
                    listOf("imported-egg-bowl"),
                    db.keepPromptDao().getAll().map { it.recipeId },
                )
            }
            // Completing the cook again does not re-ask.
            composeRule.onNodeWithTag("cook-used-item:eggs").performClick()
            composeRule.onNodeWithTag("cook-used-item:eggs").performClick()
            Thread.sleep(2_000)
            assertEquals(0, tagCount("keep-prompt"))
            withDb { db ->
                assertFalse(
                    db.recipeRatingDao().getAll().any { it.recipeId == "imported-egg-bowl" },
                )
            }
        }
    }
}
