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
import androidx.compose.ui.test.performScrollToNode
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
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.6.3: label facts at work in the app. The eggs item carries facts
 * scanned from its carton (155 kcal per 100 g against the USDA entry's
 * 143), so a dinner built on eggs computes from the label and says so
 * on its nutrition card; a dinner whose items were never scanned
 * still reads USDA and carries no label note. The facts row is
 * written through the real DAO, the recipes through the real imported
 * recipe table — the same writes the scan flows make.
 */
@RunWith(AndroidJUnit4::class)
class LabelNutritionInstrumentedTest {

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
            // The scanned carton: 155 kcal per 100 g where USDA says 143.
            db.labelNutritionDao().upsert(
                with(SeedMapper) {
                    LabelNutrition(
                        itemId = "eggs",
                        barcode = "0021234500000",
                        per100g = NutritionValues(
                            caloriesKcal = 155.0, proteinG = 13.0, fatG = 10.0,
                            carbohydrateG = 1.0, sodiumMg = 140.0,
                        ),
                        servingSizeText = "1 egg (50 g)",
                        servingGrams = 50.0,
                        updatedAtMillis = 1L,
                    ).toEntity()
                },
            )
            fun dinner(id: String, title: String, itemId: String, grams: Double) =
                ImportedRecipe(
                    recipe = Recipe(
                        id = id,
                        title = title,
                        servings = 4,
                        activeMinutes = 10,
                        cuisine = "Test",
                        mainProtein = "Eggs",
                        ingredients = listOf(
                            RecipeIngredient(
                                priceItemId = itemId,
                                quantity = Quantity(grams, MeasureUnit.GRAM),
                            ),
                        ),
                        steps = listOf("Cook."),
                        origin = RecipeOrigin.MANUAL,
                    ),
                    sourceLink = null,
                    createdAtMillis = 1L,
                )
            // 200 g of eggs at the label's 155 = 310 kcal, 77.5 a
            // serving, shown as 78 kcal. USDA would show 72 kcal.
            db.importedRecipeDao().upsert(
                with(SeedMapper) {
                    dinner("manual-label-eggs", "Label Eggs Dinner", "eggs", 200.0).toEntity()
                },
            )
            // 100 g of spaghetti at USDA's 371 = 92.75 a serving,
            // shown as 93 kcal, with no label anywhere near it.
            db.importedRecipeDao().upsert(
                with(SeedMapper) {
                    dinner("manual-plain-pasta", "Plain Pasta Dinner", "spaghetti", 100.0)
                        .toEntity()
                },
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

    private fun waitForText(text: String, substring: Boolean = false) {
        awaitStep("waitForText($text)") {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tagCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun openRecipe(title: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).performClick()
        waitForText("Nutrition per serving (estimate)")
    }

    @Test fun recipeWithScannedItemComputesFromTheLabel() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            openRecipe("Label Eggs Dinner")
            awaitStep("label calories shown") {
                composeRule.onAllNodesWithText("78 kcal").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(
                "the label note must show on the nutrition card",
                tagCount("recipe-nutrition-label-note") == 1,
            )
        }
    }

    @Test fun recipeWithoutScansStillComputesFromUsda() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            openRecipe("Plain Pasta Dinner")
            awaitStep("USDA calories shown") {
                composeRule.onAllNodesWithText("93 kcal").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(
                "no label note may show for an unscanned recipe",
                tagCount("recipe-nutrition-label-note") == 0,
            )
        }
    }
}
