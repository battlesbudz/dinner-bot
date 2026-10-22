package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-1 thumbs behavior: DOWN is out of generation and swaps entirely,
 * UP wins ties only (never cost), unrated stays in normal rotation.
 */
class WeekPlannerRatingsTest {

    private fun item(
        id: String, store: Store, packAmount: Double, unit: MeasureUnit,
        packPrice: Int, perUnit: Double? = null,
    ) = PriceItem(
        id = id, name = id, category = "Test", homeStore = store, packDescription = "pack",
        packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
        pricePerUnitCents = perUnit ?: packPrice / packAmount,
        checkedDate = "2026-10-22", source = "test",
    )

    private val spaghetti = item("spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209, perUnit = 6.53)
    private val eggs = item("eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185, perUnit = 15.4)
    private val turkey = item("ground-turkey", Store.WALMART, 1.0, MeasureUnit.POUND, 198)
    private val book = listOf(spaghetti, eggs, turkey)

    private fun recipe(id: String, title: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = title, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    /** Same cost for every id: one pack of eggs + half a pack of spaghetti. */
    private fun cheapDinner(id: String, title: String = id) = recipe(
        id, title,
        priced("eggs", 6.0, MeasureUnit.COUNT),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )

    /** Pricier dinner: a pound of turkey on top of the cheap base. */
    private fun priceyDinner(id: String, title: String = id) = recipe(
        id, title,
        priced("eggs", 6.0, MeasureUnit.COUNT),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
        priced("ground-turkey", 1.0, MeasureUnit.POUND),
    )

    private fun settings(nights: List<String>) = HouseholdSettings(
        householdSize = 4, cookNights = nights, weeklyBudgetCents = 100_00,
    )

    private val fiveNights = listOf("MON", "TUE", "WED", "THU", "FRI")

    @Test fun downRatedRecipeIsNeverGeneratedAcrossVariations() {
        val recipes = (1..6).map { cheapDinner("c$it") }
        val ratings = mapOf("c1" to RecipeRating.DOWN)
        for (variation in 0..7) {
            val plan = WeekPlanner.generate(
                settings(fiveNights), recipes, book, emptyMap(), emptySet(),
                "2026-10-19", variation = variation, ratings = ratings,
            )
            assertTrue(
                "variation $variation planned a thumbed-down dinner: ${plan.nights}",
                plan.nights.none { it.recipeId == "c1" },
            )
            assertEquals(5, plan.nights.size)
        }
    }

    @Test fun downRatedRecipeIsNeverSwappedIn() {
        val recipes = (1..4).map { cheapDinner("c$it") }
        val ratings = mapOf("c2" to RecipeRating.DOWN, "c3" to RecipeRating.DOWN)
        val plan = WeekPlanner.generate(
            settings(listOf("MON")), recipes, book, emptyMap(), emptySet(),
            "2026-10-19", ratings = ratings,
        )
        val swapped = WeekPlanner.swapDinner(
            settings(listOf("MON")), plan, "MON", recipes, book, emptyMap(), emptySet(),
            ratings = ratings,
        )
        assertEquals("c4", swapped.nights.single().recipeId)
    }

    @Test fun swapWithEveryAlternativeDownRatedLeavesPlanUnchanged() {
        val recipes = (1..3).map { cheapDinner("c$it") }
        val ratings = mapOf("c2" to RecipeRating.DOWN, "c3" to RecipeRating.DOWN)
        val plan = WeekPlanner.generate(
            settings(listOf("MON")), recipes, book, emptyMap(), emptySet(),
            "2026-10-19", ratings = ratings,
        )
        assertEquals("c1", plan.nights.single().recipeId)
        val swapped = WeekPlanner.swapDinner(
            settings(listOf("MON")), plan, "MON", recipes, book, emptyMap(), emptySet(),
            ratings = ratings,
        )
        assertEquals("c1", swapped.nights.single().recipeId)
    }

    @Test fun upRatedWinsTieAtSameCost() {
        // Titles are set so the plain title tie-break alone would pick the
        // unrated dinner; equal cost means the thumbs-up must decide.
        val unrated = cheapDinner("a-unrated", title = "Aardvark Stew")
        val upRated = cheapDinner("z-up", title = "Zebra Stew")
        val ratings = mapOf("z-up" to RecipeRating.UP)
        val ranked = WeekPlanner.rankCandidates(
            settings(listOf("MON")), listOf(unrated, upRated), book.associateBy { it.id },
            emptyMap(), ratings,
        )
        assertEquals("z-up", ranked.first().id)
        val plan = WeekPlanner.generate(
            settings(listOf("MON")), listOf(unrated, upRated), book, emptyMap(), emptySet(),
            "2026-10-19", ratings = ratings,
        )
        assertEquals("z-up", plan.nights.single().recipeId)
    }

    @Test fun upRatedDoesNotOverrideCheaperUnrated() {
        val cheap = cheapDinner("cheap", title = "Zebra Stew")
        val priceyUp = priceyDinner("pricey", title = "Aardvark Stew")
        val ratings = mapOf("pricey" to RecipeRating.UP)
        val ranked = WeekPlanner.rankCandidates(
            settings(listOf("MON")), listOf(cheap, priceyUp), book.associateBy { it.id },
            emptyMap(), ratings,
        )
        assertEquals("cheap", ranked.first().id)
        val plan = WeekPlanner.generate(
            settings(listOf("MON")), listOf(cheap, priceyUp), book, emptyMap(), emptySet(),
            "2026-10-19", ratings = ratings,
        )
        assertEquals("cheap", plan.nights.single().recipeId)
    }

    @Test fun upRatedTieBreakSurvivesRotation() {
        // All three dinners cost the same. Ranked order puts the UP-rated
        // one first; without the repair, variation 1+ rotation would take an
        // unrated dinner for the single cook night instead.
        val recipes = listOf(
            cheapDinner("u1", title = "U1"),
            cheapDinner("up", title = "Up"),
            cheapDinner("u2", title = "U2"),
        )
        val ratings = mapOf("up" to RecipeRating.UP)
        for (variation in 0..2) {
            val plan = WeekPlanner.generate(
                settings(listOf("MON")), recipes, book, emptyMap(), emptySet(),
                "2026-10-19", variation = variation, ratings = ratings,
            )
            assertEquals("variation $variation", "up", plan.nights.single().recipeId)
        }
    }

    @Test fun unratedRecipesStayInNormalRotation() {
        val recipes = (1..6).map { cheapDinner("c$it") }
        val ratings = mapOf("c6" to RecipeRating.UP)
        val plan = WeekPlanner.generate(
            settings(fiveNights), recipes, book, emptyMap(), emptySet(),
            "2026-10-19", ratings = ratings,
        )
        assertEquals(5, plan.nights.size)
        assertEquals(5, plan.nights.map { it.recipeId }.toSet().size)
    }

    @Test fun allDownRatedGivesTheThumbsDownEmptyNote() {
        val recipes = (1..3).map { cheapDinner("c$it") }
        val ratings = recipes.associate { it.id to RecipeRating.DOWN }
        val plan = WeekPlanner.generate(
            settings(fiveNights), recipes, book, emptyMap(), emptySet(),
            "2026-10-19", ratings = ratings,
        )
        assertTrue(plan.nights.isEmpty())
        assertTrue(plan.notes.single().contains("thumbed down"))
    }
}
